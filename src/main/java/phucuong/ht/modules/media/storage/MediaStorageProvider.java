package phucuong.ht.modules.media.storage;

/**
 * Hợp đồng với nơi lưu ảnh (Cloudinary, S3, thư mục cục bộ, ...). Hệ thống chỉ làm việc với "khóa logic"
 * (ví dụ ht/prod/dioceses/giao-phan-1a2b3c4d/avatars/{id}/thumb.jpg), không bao giờ lưu URL của nhà cung cấp.
 * Đổi nhà cung cấp = thêm một lớp implement interface này + đổi {@code app.media.active-provider}.
 */
public interface MediaStorageProvider {

    /** Tên định danh, được lưu trong media_asset.provider. */
    String name();

    void put(String key, byte[] data, String contentType);

    /** Đọc nội dung; ném {@link MediaStorageException} nếu không tồn tại hoặc lỗi đường truyền. */
    byte[] get(String key);

    /** Xóa, không lỗi nếu khóa không tồn tại. */
    void delete(String key);

    default void copy(String sourceKey, String targetKey, String contentType) {
        put(targetKey, get(sourceKey), contentType);
    }

    /** Đổi khóa. Mặc định chép rồi xóa; nhà cung cấp có thao tác đổi tên riêng nên ghi đè. */
    default void move(String sourceKey, String targetKey, String contentType) {
        copy(sourceKey, targetKey, contentType);
        delete(sourceKey);
    }

    /** Tạo sẵn thư mục (nơi nào không có khái niệm thư mục thì bỏ qua). */
    default void ensureFolder(String prefix) {
    }
}
