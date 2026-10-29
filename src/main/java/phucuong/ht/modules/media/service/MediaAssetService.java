package phucuong.ht.modules.media.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.entity.MediaVariantInfo;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.model.enums.MediaVariants;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.storage.MediaStorageException;
import phucuong.ht.modules.media.storage.MediaStorageProvider;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vòng đời một ảnh: tải lên (PENDING) → gắn vào bản ghi nghiệp vụ (ACTIVE, chuyển vào thư mục giáo phận)
 * → gỡ (DELETED, job dọn xóa thật sau thời gian lưu).
 */
@Service
@RequiredArgsConstructor
public class MediaAssetService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetService.class);

    private final MediaAssetRepository repository;
    private final MediaStorageRegistry registry;
    private final MediaImageProcessor processor;
    private final MediaFolderService folders;
    private final MediaContentCache cache;

    /** Xử lý ảnh, ghi lên nhà cung cấp hiện hành ở thư mục tạm (pending) và tạo asset PENDING. */
    public MediaAsset upload(byte[] data, MediaCategory category, String actor) {
        List<MediaImageProcessor.Processed> processed = processor.process(data, category);
        UUID id = UUID.randomUUID();
        String base = folders.pendingBaseKey(category, id);
        MediaStorageProvider provider = registry.active();

        List<MediaVariantInfo> infos = new ArrayList<>();
        try {
            for (MediaImageProcessor.Processed p : processed) {
                String key = variantKey(base, p.name());
                provider.put(key, p.data(), MediaImageProcessor.CONTENT_TYPE);
                infos.add(new MediaVariantInfo(p.name(), key, MediaImageProcessor.CONTENT_TYPE, p.data().length, p.width(), p.height(), p.sha256()));
            }
        } catch (MediaStorageException e) {
            infos.forEach(info -> quietDelete(provider, info.key()));
            log.error("Tải ảnh lên nhà cung cấp {} thất bại", provider.name(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode.INTERNAL_ERROR, "Không lưu được ảnh, vui lòng thử lại sau");
        }

        OffsetDateTime now = OffsetDateTime.now();
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setCategory(category);
        asset.setStatus(MediaStatus.PENDING);
        asset.setProvider(provider.name());
        asset.setBaseKey(base);
        asset.setVariants(infos);
        asset.setCreatedBy(actor);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        try {
            return repository.saveAndFlush(asset);
        } catch (RuntimeException e) {
            infos.forEach(info -> quietDelete(provider, info.key()));
            throw e;
        }
    }

    /**
     * Gắn ảnh vào một bản ghi nghiệp vụ thuộc giáo phận {@code dioceseId} (null = dùng chung toàn hệ thống).
     * Ảnh PENDING của chính người tải được chuyển vào thư mục giáo phận; ảnh đã ACTIVE (ví dụ "lưu thành mẫu mới")
     * được sao chép thành asset mới để hai bản ghi không dùng chung một ảnh.
     *
     * @return id asset cuối cùng cần lưu vào bản ghi nghiệp vụ
     */
    @Transactional
    public UUID attach(UUID assetId, MediaCategory expected, UUID dioceseId, String actor) {
        MediaAsset asset = repository.findById(assetId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Ảnh không tồn tại hoặc đã bị xóa"));
        if (asset.getCategory() != expected) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Ảnh không đúng loại");
        }
        if (asset.getStatus() == MediaStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Ảnh không tồn tại hoặc đã bị xóa");
        }
        if (asset.getStatus() == MediaStatus.ACTIVE) {
            return copyOf(asset, dioceseId, actor).getId();
        }
        if (!Objects.equals(asset.getCreatedBy(), actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Bạn không được dùng ảnh do người khác tải lên");
        }

        MediaStorageProvider provider = registry.require(asset.getProvider());
        String base = folders.baseKey(dioceseId, asset.getCategory(), asset.getId());
        List<MediaVariantInfo> moved = new ArrayList<>();
        try {
            for (MediaVariantInfo info : asset.getVariants()) {
                String newKey = variantKey(base, info.name());
                provider.move(info.key(), newKey, info.contentType());
                moved.add(info.withKey(newKey));
            }
        } catch (MediaStorageException e) {
            log.error("Chuyển ảnh {} vào thư mục giáo phận thất bại", assetId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode.INTERNAL_ERROR, "Không lưu được ảnh, vui lòng thử lại sau");
        }
        asset.setVariants(moved);
        asset.setBaseKey(base);
        asset.setDioceseId(dioceseId);
        asset.setStatus(MediaStatus.ACTIVE);
        asset.setUpdatedAt(OffsetDateTime.now());
        repository.save(asset);
        return asset.getId();
    }

    /** Sao chép ảnh ACTIVE thành asset mới trên nhà cung cấp hiện hành, trong thư mục của {@code dioceseId}. */
    @Transactional
    public MediaAsset copyOf(MediaAsset source, UUID dioceseId, String actor) {
        UUID id = UUID.randomUUID();
        String base = folders.baseKey(dioceseId, source.getCategory(), id);
        MediaStorageProvider from = registry.require(source.getProvider());
        MediaStorageProvider to = registry.active();
        List<MediaVariantInfo> infos = new ArrayList<>();
        try {
            for (MediaVariantInfo info : source.getVariants()) {
                String key = variantKey(base, info.name());
                to.put(key, from.get(info.key()), info.contentType());
                infos.add(info.withKey(key));
            }
        } catch (MediaStorageException e) {
            infos.forEach(info -> quietDelete(to, info.key()));
            log.error("Sao chép ảnh {} thất bại", source.getId(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode.INTERNAL_ERROR, "Không lưu được ảnh, vui lòng thử lại sau");
        }
        OffsetDateTime now = OffsetDateTime.now();
        MediaAsset copy = new MediaAsset();
        copy.setId(id);
        copy.setCategory(source.getCategory());
        copy.setDioceseId(dioceseId);
        copy.setStatus(MediaStatus.ACTIVE);
        copy.setProvider(to.name());
        copy.setBaseKey(base);
        copy.setVariants(infos);
        copy.setCreatedBy(actor);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return repository.saveAndFlush(copy);
    }

    /**
     * Tạo thẳng asset ACTIVE trong thư mục giáo phận từ byte ảnh có sẵn (dùng khi chuyển ảnh base64 cũ lên cloud).
     */
    public MediaAsset createActive(byte[] data, MediaCategory category, UUID dioceseId, String actor) {
        List<MediaImageProcessor.Processed> processed = processor.process(data, category);
        UUID id = UUID.randomUUID();
        String base = folders.baseKey(dioceseId, category, id);
        MediaStorageProvider provider = registry.active();
        List<MediaVariantInfo> infos = new ArrayList<>();
        try {
            for (MediaImageProcessor.Processed p : processed) {
                String key = variantKey(base, p.name());
                provider.put(key, p.data(), MediaImageProcessor.CONTENT_TYPE);
                infos.add(new MediaVariantInfo(p.name(), key, MediaImageProcessor.CONTENT_TYPE, p.data().length, p.width(), p.height(), p.sha256()));
            }
        } catch (MediaStorageException e) {
            infos.forEach(info -> quietDelete(provider, info.key()));
            throw e;
        }
        OffsetDateTime now = OffsetDateTime.now();
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setCategory(category);
        asset.setDioceseId(dioceseId);
        asset.setStatus(MediaStatus.ACTIVE);
        asset.setProvider(provider.name());
        asset.setBaseKey(base);
        asset.setVariants(infos);
        asset.setCreatedBy(actor);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        try {
            return repository.saveAndFlush(asset);
        } catch (RuntimeException e) {
            infos.forEach(info -> quietDelete(provider, info.key()));
            throw e;
        }
    }

    /** Chuyển ảnh ACTIVE sang thư mục của giáo phận khác (ví dụ huynh trưởng chuyển giáo xứ sang giáo phận khác). */
    @Transactional
    public void relocate(UUID assetId, UUID newDioceseId) {
        MediaAsset asset = repository.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaStatus.ACTIVE || Objects.equals(asset.getDioceseId(), newDioceseId)) {
            return;
        }
        MediaStorageProvider provider = registry.require(asset.getProvider());
        String base = folders.baseKey(newDioceseId, asset.getCategory(), asset.getId());
        List<MediaVariantInfo> moved = new ArrayList<>();
        for (MediaVariantInfo info : asset.getVariants()) {
            String newKey = variantKey(base, info.name());
            provider.move(info.key(), newKey, info.contentType());
            cache.evict(asset.getId() + "/" + info.name());
            moved.add(info.withKey(newKey));
        }
        asset.setVariants(moved);
        asset.setBaseKey(base);
        asset.setDioceseId(newDioceseId);
        asset.setUpdatedAt(OffsetDateTime.now());
        repository.save(asset);
    }

    /** Xóa thật các tệp của asset trên nhà cung cấp (job dọn gọi). */
    public void purgeFiles(MediaAsset asset) {
        MediaStorageProvider provider = registry.require(asset.getProvider());
        for (MediaVariantInfo info : asset.getVariants()) {
            provider.delete(info.key());
            cache.evict(asset.getId() + "/" + info.name());
        }
    }

    /** Gỡ ảnh khỏi bản ghi nghiệp vụ: đánh dấu DELETED, job dọn sẽ xóa thật trên cloud sau thời gian lưu. */
    @Transactional
    public void release(UUID assetId) {
        if (assetId == null) {
            return;
        }
        repository.findById(assetId).ifPresent(asset -> {
            if (asset.getStatus() != MediaStatus.DELETED) {
                asset.setStatus(MediaStatus.DELETED);
                asset.setDeletedAt(OffsetDateTime.now());
                asset.setUpdatedAt(asset.getDeletedAt());
                repository.save(asset);
            }
        });
    }

    @Transactional(readOnly = true)
    public MediaAsset find(UUID assetId) {
        return repository.findById(assetId).orElse(null);
    }

    /** Đọc byte của một biến thể (qua nhà cung cấp mà asset đang nằm ở đó), có dùng bộ nhớ đệm cho biến thể nhỏ. */
    public byte[] loadVariant(MediaAsset asset, String variant) {
        MediaVariantInfo info = asset.variant(variant).orElseThrow(() -> new MediaStorageException("Ảnh không có biến thể " + variant, true));
        String cacheKey = asset.getId() + "/" + variant;
        byte[] cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] data = registry.require(asset.getProvider()).get(info.key());
        if (!MediaVariants.MASTER.equals(variant)) {
            cache.put(cacheKey, data);
        }
        return data;
    }

    /** Byte ảnh dùng để render PDF (biến thể master). Trả null nếu không có ảnh. */
    public byte[] loadMaster(UUID assetId) {
        if (assetId == null) {
            return null;
        }
        MediaAsset asset = find(assetId);
        if (asset == null || asset.getStatus() == MediaStatus.DELETED) {
            return null;
        }
        return loadVariant(asset, MediaVariants.MASTER);
    }

    /** Byte ảnh đại diện (biến thể medium) để in lên chứng nhận / thẻ; null nếu không có ảnh hoặc không đọc được. */
    public byte[] loadAvatar(UUID assetId) {
        if (assetId == null) {
            return null;
        }
        MediaAsset asset = find(assetId);
        if (asset == null || asset.getStatus() == MediaStatus.DELETED) {
            return null;
        }
        try {
            return loadVariant(asset, MediaVariants.MEDIUM);
        } catch (MediaStorageException e) {
            log.warn("Không đọc được ảnh đại diện {} để in: {}", assetId, e.getMessage());
            return null;
        }
    }

    static String variantKey(String base, String variant) {
        return base + "/" + variant + "." + MediaImageProcessor.EXTENSION;
    }

    private void quietDelete(MediaStorageProvider provider, String key) {
        try {
            provider.delete(key);
        } catch (RuntimeException e) {
            log.warn("Không dọn được tệp {} sau lỗi: {}", key, e.getMessage());
        }
    }
}
