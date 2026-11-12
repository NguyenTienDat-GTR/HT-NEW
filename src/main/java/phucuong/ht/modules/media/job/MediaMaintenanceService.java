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
 *   <li>PENDING quá hạn (tải lên mà không lưu) → DELETED.</li>
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

    public void cleanup() {
        MediaProperties.Cleanup config = properties.getCleanup();
        OffsetDateTime now = OffsetDateTime.now();
        int batch = config.getBatchSize();

        for (MediaAsset asset : repository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING, now.minus(config.getPendingTtl()), PageRequest.of(0, batch))) {
            assets.release(asset.getId());
        }
        for (MediaAsset asset : repository.findUnreferencedActive(now.minus(config.getOrphanGrace()), batch)) {
            assets.release(asset.getId());
        }
        int purged = 0;
        for (MediaAsset asset : repository.findByStatusAndDeletedAtBefore(MediaStatus.DELETED, now.minus(config.getDeletedRetention()), PageRequest.of(0, batch))) {
            try {
                assets.purgeFiles(asset);
                repository.delete(asset);
                purged++;
            } catch (RuntimeException e) {
                log.warn("Chưa xóa được tệp của ảnh {} (sẽ thử lại): {}", asset.getId(), e.getMessage());
            }
        }
        if (purged > 0) {
            log.info("Đã xóa vĩnh viễn {} ảnh hết thời gian lưu", purged);
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
