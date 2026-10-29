package phucuong.ht.modules.media.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.DataUrls;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaFolderService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chuyển ảnh base64 cũ trong database (avatar, nền chứng nhận, nền thẻ) lên cloud theo từng lô nhỏ.
 * Mỗi dòng xử lý trong một giao dịch có khóa dòng (SKIP LOCKED) nên nhiều instance chạy song song vẫn an toàn,
 * và chạy lại nhiều lần không sinh trùng (điều kiện chọn là dòng còn data URL và chưa có asset).
 * Dòng không xử lý được (ảnh hỏng) được ghi log và bỏ qua trong phiên chạy hiện tại, không xóa dữ liệu gốc.
 */
@Service
public class MediaLegacyMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaLegacyMigrationService.class);
    private static final String ACTOR = "system-media-migration";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MediaAssetService assets;
    private final MediaFolderService folders;
    private final MediaProperties properties;
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    public MediaLegacyMigrationService(JdbcTemplate jdbc, TransactionTemplate tx, MediaAssetService assets,
                                       MediaFolderService folders, MediaProperties properties) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.assets = assets;
        this.folders = folders;
        this.properties = properties;
    }

    /** Chạy một lô cho cả 4 nguồn; trả về số ảnh đã chuyển. */
    public int migrateBatch() {
        int limit = properties.getLegacyMigration().getBatchSize();
        int total = 0;
        total += migrateLeaders(limit);
        total += migrateCertificateConfigs(limit);
        total += migrateBadgeSide("front_background", "front_background_asset_id", limit);
        total += migrateBadgeSide("back_background", "back_background_asset_id", limit);
        if (total > 0) {
            log.info("Đã chuyển {} ảnh base64 cũ lên cloud", total);
        }
        return total;
    }

    private int migrateLeaders(int limit) {
        List<UUID> ids = jdbc.queryForList(
                "select id from leader where image_asset_id is null and image_url like 'data:%' order by id limit ?",
                UUID.class, limit + failed.size());
        int done = 0;
        for (UUID id : ids) {
            if (done >= limit || failed.contains("leader:" + id)) {
                continue;
            }
            if (Boolean.TRUE.equals(tx.execute(s -> migrateLeader(id)))) {
                done++;
            }
        }
        return done;
    }

    private boolean migrateLeader(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select l.image_url, d.diocese_id
                from leader l
                join parish p on p.id = l.parish_id
                join deanery d on d.id = p.deanery_id
                where l.id = ? and l.image_asset_id is null and l.image_url like 'data:%'
                for update of l skip locked
                """, id);
        if (rows.isEmpty()) {
            return false;
        }
        UUID assetId = convert("leader:" + id, (String) rows.get(0).get("image_url"), MediaCategory.LEADER_AVATAR, (UUID) rows.get(0).get("diocese_id"));
        if (assetId == null) {
            return false;
        }
        jdbc.update("update leader set image_asset_id = ?, image_url = null where id = ?", assetId, id);
        return true;
    }

    private int migrateCertificateConfigs(int limit) {
        List<UUID> ids = jdbc.queryForList(
                "select id from certificate_config where background_asset_id is null and background_image like 'data:%' order by id limit ?",
                UUID.class, limit + failed.size());
        int done = 0;
        for (UUID id : ids) {
            if (done >= limit || failed.contains("cert:" + id)) {
                continue;
            }
            if (Boolean.TRUE.equals(tx.execute(s -> migrateConfig("certificate_config", "background_image", "background_asset_id",
                    "cert:" + id, id, MediaCategory.CERTIFICATE_BACKGROUND)))) {
                done++;
            }
        }
        return done;
    }

    private int migrateBadgeSide(String column, String assetColumn, int limit) {
        List<UUID> ids = jdbc.queryForList(
                "select id from badge_config where " + assetColumn + " is null and " + column + " like 'data:%' order by id limit ?",
                UUID.class, limit + failed.size());
        int done = 0;
        for (UUID id : ids) {
            String key = "badge:" + column + ":" + id;
            if (done >= limit || failed.contains(key)) {
                continue;
            }
            if (Boolean.TRUE.equals(tx.execute(s -> migrateConfig("badge_config", column, assetColumn, key, id, MediaCategory.BADGE_BACKGROUND)))) {
                done++;
            }
        }
        return done;
    }

    private boolean migrateConfig(String table, String column, String assetColumn, String failKey, UUID id, MediaCategory category) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select " + column + " as data, scope_key from " + table + " where id = ? and " + assetColumn + " is null and "
                        + column + " like 'data:%' for update skip locked", id);
        if (rows.isEmpty()) {
            return false;
        }
        UUID dioceseId = folders.dioceseOfScopeKey((String) rows.get(0).get("scope_key"));
        UUID assetId = convert(failKey, (String) rows.get(0).get("data"), category, dioceseId);
        if (assetId == null) {
            return false;
        }
        jdbc.update("update " + table + " set " + assetColumn + " = ?, " + column + " = null where id = ?", assetId, id);
        return true;
    }

    private UUID convert(String failKey, String dataUrl, MediaCategory category, UUID dioceseId) {
        try {
            byte[] bytes = DataUrls.decode(dataUrl);
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Không giải mã được data URL");
            }
            MediaAsset asset = assets.createActive(bytes, category, dioceseId, ACTOR);
            return asset.getId();
        } catch (RuntimeException e) {
            failed.add(failKey);
            log.warn("Không chuyển được ảnh cũ {} lên cloud (bỏ qua, giữ nguyên dữ liệu gốc): {}", failKey, e.getMessage());
            return null;
        }
    }
}
