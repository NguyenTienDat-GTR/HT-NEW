package phucuong.ht.modules.media.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.entity.MediaVariantInfo;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.service.MediaImageProcessor;
import phucuong.ht.modules.media.storage.MediaStorageProvider;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tự chuyển toàn bộ ảnh sang nhà cung cấp lưu trữ mới khi đổi {@code app.media.active-provider}.
 * <ul>
 *   <li>Chép từng biến thể từ nhà cung cấp cũ sang mới, đối chiếu sha256 ở cả hai đầu rồi mới đổi {@code provider}.</li>
 *   <li>Trong lúc chuyển, ảnh luôn đọc theo {@code provider} đang ghi trong media_asset nên hệ thống không gián đoạn.</li>
 *   <li>Lỗi thì tăng bộ đếm và hẹn thử lại theo backoff; bản cũ chỉ bị xóa khi bật {@code delete-source}.</li>
 *   <li>Cần giữ cấu hình của nhà cung cấp cũ cho tới khi chuyển xong (ví dụ CLOUDINARY_URL).</li>
 * </ul>
 */
@Service
public class MediaProviderMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaProviderMigrationService.class);

    private final MediaAssetRepository repository;
    private final MediaStorageRegistry registry;
    private final MediaProperties properties;

    public MediaProviderMigrationService(MediaAssetRepository repository, MediaStorageRegistry registry, MediaProperties properties) {
        this.repository = repository;
        this.registry = registry;
        this.properties = properties;
    }

    /** Chạy một lô; trả về số asset đã chuyển thành công. */
    @Transactional
    public int migrateBatch() {
        MediaProperties.ProviderMigration config = properties.getProviderMigration();
        List<MediaAsset> batch = repository.findToMigrate(registry.activeName(), OffsetDateTime.now(),
                config.getMaxRetries(), PageRequest.of(0, config.getBatchSize()));
        int migrated = 0;
        List<Runnable> afterCommit = new ArrayList<>();
        for (MediaAsset asset : batch) {
            String sourceName = asset.getProvider();
            List<MediaVariantInfo> sourceVariants = List.copyOf(asset.getVariants());
            try {
                migrateOne(asset);
                migrated++;
                if (config.isDeleteSource() && registry.has(sourceName)) {
                    MediaStorageProvider source = registry.require(sourceName);
                    afterCommit.add(() -> sourceVariants.forEach(v -> quietDelete(source, v.key())));
                }
            } catch (RuntimeException e) {
                recordFailure(asset, e);
            }
        }
        repository.saveAll(batch);
        // Xóa bản nguồn sau khi đã ghi nhận provider mới (vẫn trong cùng lô; lỗi xóa không ảnh hưởng dữ liệu).
        afterCommit.forEach(Runnable::run);
        if (migrated > 0) {
            log.info("Đã chuyển {} ảnh sang nhà cung cấp '{}'", migrated, registry.activeName());
        }
        return migrated;
    }

    private void migrateOne(MediaAsset asset) {
        MediaStorageProvider source = registry.require(asset.getProvider());
        MediaStorageProvider target = registry.active();
        for (MediaVariantInfo info : asset.getVariants()) {
            byte[] data = source.get(info.key());
            if (!MediaImageProcessor.sha256(data).equals(info.sha256())) {
                throw new IllegalStateException("Sai sha256 ở nguồn cho " + info.key());
            }
            target.put(info.key(), data, info.contentType());
            byte[] copied = target.get(info.key());
            if (!MediaImageProcessor.sha256(copied).equals(info.sha256())) {
                throw new IllegalStateException("Sai sha256 sau khi chép cho " + info.key());
            }
        }
        asset.setProvider(target.name());
        asset.setMigrationError(null);
        asset.setMigrationRetries(0);
        asset.setNextAttemptAt(null);
        asset.setUpdatedAt(OffsetDateTime.now());
    }

    private void recordFailure(MediaAsset asset, RuntimeException e) {
        int retries = asset.getMigrationRetries() + 1;
        long backoffMinutes = Math.min(360L, 1L << Math.min(retries, 9));
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        asset.setMigrationRetries(retries);
        asset.setMigrationError(message.length() > 480 ? message.substring(0, 480) : message);
        asset.setNextAttemptAt(OffsetDateTime.now().plusMinutes(backoffMinutes));
        log.warn("Chuyển ảnh {} sang nhà cung cấp mới lỗi (lần {}): {}", asset.getId(), retries, message);
    }

    private void quietDelete(MediaStorageProvider provider, String key) {
        try {
            provider.delete(key);
        } catch (RuntimeException e) {
            log.warn("Không xóa được bản nguồn {}: {}", key, e.getMessage());
        }
    }
}
