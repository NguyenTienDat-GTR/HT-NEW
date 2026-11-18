package phucuong.ht.modules.media.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.service.MediaAssetService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dọn dẹp và di dời ảnh:
 * <ul>
 *   <li>PENDING quá hạn (tải lên mà không lưu) → xóa tệp, thư mục và dòng ngay (không qua thời gian lưu).</li>
 *   <li>ACTIVE mồ côi (không bản ghi nào tham chiếu, ví dụ lưu hồ sơ thất bại sau khi gắn ảnh) → DELETED.</li>
 *   <li>DELETED quá thời gian lưu → xóa tệp trên cloud rồi xóa dòng.</li>
 *   <li>Avatar của huynh trưởng đã chuyển sang giáo phận khác → chuyển sang thư mục giáo phận mới.</li>
 * </ul>
 */
@Service
public class MediaMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MediaMaintenanceService.class);

    private final MediaAssetRepository repository;
    private final MediaAssetService assets;
    private final MediaProperties properties;
    private final JdbcTemplate jdbc;
    private final phucuong.ht.modules.media.storage.MediaStorageRegistry registry;

    public MediaMaintenanceService(MediaAssetRepository repository, MediaAssetService assets, MediaProperties properties, JdbcTemplate jdbc,
                                   phucuong.ht.modules.media.storage.MediaStorageRegistry registry) {
        this.repository = repository;
        this.assets = assets;
        this.properties = properties;
        this.jdbc = jdbc;
        this.registry = registry;
    }

    /** Đặt lại thư mục hiển thị cho mọi ảnh còn dùng (ACTIVE / PENDING) trên nhà cung cấp của chúng. */
    public int syncFolders() {
        int touched = 0;
        for (MediaAsset asset : repository.findAll()) {
            if (asset.getStatus() == MediaStatus.DELETED) {
                continue;
            }
            try {
                var provider = registry.require(asset.getProvider());
                asset.getVariants().forEach(info -> provider.syncFolder(info.key()));
                touched++;
            } catch (RuntimeException e) {
                log.warn("Không đồng bộ thư mục cho ảnh {}: {}", asset.getId(), e.getMessage());
            }
        }
        log.info("Đã đồng bộ thư mục hiển thị cho {} ảnh", touched);
        return touched;
    }

    /** Số lượt tối đa mỗi bước trong một lần chạy (mỗi lượt xử lý tối đa batchSize ảnh) để một đợt tồn đọng không chiếm cả tiến trình. */
    private static final int MAX_ROUNDS = 20;

    public void cleanup() {
        MediaProperties.Cleanup config = properties.getCleanup();
        OffsetDateTime now = OffsetDateTime.now();
        int batch = config.getBatchSize();

        // PENDING quá hạn chưa từng gắn vào bản ghi nào nên không cần thời gian lưu để khôi phục: xóa tệp và dòng ngay.
        int dropped = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<MediaAsset> stale = repository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING, now.minus(config.getPendingTtl()), PageRequest.of(0, batch));
            if (stale.isEmpty()) {
                break;
            }
            for (MediaAsset asset : stale) {
                if (discardPending(asset)) {
                    dropped++;
                }
            }
            if (stale.size() < batch) {
                break;
            }
        }
        if (dropped > 0) {
            log.info("Đã xóa {} ảnh tải lên nhưng không lưu (PENDING quá hạn)", dropped);
        }

        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<MediaAsset> orphans = repository.findUnreferencedActive(now.minus(config.getOrphanGrace()), batch);
            orphans.forEach(asset -> assets.release(asset.getId()));
            if (orphans.size() < batch) {
                break;
            }
        }

        int purged = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<MediaAsset> expired = repository.findByStatusAndDeletedAtBefore(MediaStatus.DELETED, now.minus(config.getDeletedRetention()), PageRequest.of(0, batch));
            int purgedInRound = 0;
            for (MediaAsset asset : expired) {
                try {
                    assets.purgeFiles(asset);
                    repository.delete(asset);
                    purgedInRound++;
                } catch (RuntimeException e) {
                    log.warn("Chưa xóa được tệp của ảnh {} (sẽ thử lại): {}", asset.getId(), e.getMessage());
                }
            }
            purged += purgedInRound;
            // Dừng khi hết ảnh hoặc cả lượt không xóa được ảnh nào (tránh lặp lại đúng các ảnh đang lỗi).
            if (expired.size() < batch || purgedInRound == 0) {
                break;
            }
        }
        if (purged > 0) {
            log.info("Đã xóa vĩnh viễn {} ảnh hết thời gian lưu", purged);
        }
    }

    /**
     * Xóa tệp (và thư mục) của một ảnh PENDING quá hạn rồi xóa dòng. Không xóa được tệp thì chuyển sang DELETED
     * để vòng dọn ảnh DELETED thử lại, tránh một ảnh lỗi chặn mãi các ảnh sau trong cùng lượt.
     */
    private boolean discardPending(MediaAsset asset) {
        try {
            assets.purgeFiles(asset);
            repository.delete(asset);
            return true;
        } catch (RuntimeException e) {
            log.warn("Chưa xóa được tệp của ảnh PENDING {} (chuyển sang DELETED để thử lại): {}", asset.getId(), e.getMessage());
            assets.release(asset.getId());
            return false;
        }
    }

    /** Di dời avatar nằm sai thư mục giáo phận (do huynh trưởng đổi giáo xứ sang giáo phận khác). */
    public int relocateLeaderAvatars() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select a.id as asset_id, d.diocese_id as diocese_id
                from leader l
                join media_asset a on a.id = l.image_asset_id
                join parish p on p.id = l.parish_id
                join deanery d on d.id = p.deanery_id
                where a.status = 'ACTIVE' and a.diocese_id is distinct from d.diocese_id
                limit ?
                """, properties.getCleanup().getBatchSize());
        int moved = 0;
        for (Map<String, Object> row : rows) {
            try {
                assets.relocate((UUID) row.get("asset_id"), (UUID) row.get("diocese_id"));
                moved++;
            } catch (RuntimeException e) {
                log.warn("Không di dời được ảnh {}: {}", row.get("asset_id"), e.getMessage());
            }
        }
        return moved;
    }
}
