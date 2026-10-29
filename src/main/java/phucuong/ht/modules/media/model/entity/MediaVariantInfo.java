package phucuong.ht.modules.media.model.entity;

/**
 * Một biến thể đã lưu của ảnh. {@code key} là khóa logic (không phải URL của nhà cung cấp).
 */
public record MediaVariantInfo(String name, String key, String contentType, long bytes, int width, int height, String sha256) {

    public MediaVariantInfo withKey(String newKey) {
        return new MediaVariantInfo(name, newKey, contentType, bytes, width, height, sha256);
    }
}
