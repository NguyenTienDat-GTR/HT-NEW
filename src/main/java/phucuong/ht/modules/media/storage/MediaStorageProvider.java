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

    /**
     * Lưu một file không phải ảnh (vd. mẫu Excel) theo khóa logic có đủ phần mở rộng; cùng khóa thì ghi đè bản cũ.
     * Mặc định dùng chung thao tác với ảnh (thư mục cục bộ lưu thế nào cũng được); Cloudinary ghi đè bằng loại "raw".
     */
    default void putFile(String key, byte[] data, String contentType) {
        put(key, data, contentType);
    }

    default byte[] getFile(String key) {
        return get(key);
    }

    default void deleteFile(String key) {
        delete(key);
    }

    default void copy(String sourceKey, String targetKey, String contentType) {
        put(targetKey, get(sourceKey), contentType);
    }

    /** Đổi khóa. Mặc định chép rồi xóa; nhà cung cấp có thao tác đổi tên riêng nên ghi đè. */
    default void move(String sourceKey, String targetKey, String contentType) {
        copy(sourceKey, targetKey, contentType);
        delete(sourceKey);
    }

    /** Đặt ảnh vào đúng thư mục hiển thị của nhà cung cấp (nơi thư mục tách khỏi khóa, ví dụ Cloudinary "dynamic folders"); mặc định không làm gì. */
    default void syncFolder(String key) {
    }

    /** Tạo sẵn thư mục (nơi nào không có khái niệm thư mục thì bỏ qua). */
    default void ensureFolder(String prefix) {
    }

    /**
     * Xóa một thư mục nếu nó đang trống. Không bao giờ ném lỗi: thư mục còn tệp, không tồn tại hoặc nhà cung cấp
     * từ chối đều chỉ trả về false (thư mục trống còn sót sẽ được lệnh quét dọn xử lý sau).
     *
     * @return true nếu thư mục đã bị xóa
     */
    default boolean deleteFolder(String path) {
        return false;
    }

    /** Một tệp ảnh đang nằm trên nhà cung cấp (khóa logic + thời điểm tạo). */
    record StoredFile(String key, java.time.OffsetDateTime createdAt) {
    }

    /** Nhà cung cấp có liệt kê được tệp / thư mục để quét dọn hay không. */
    default boolean supportsSweep() {
        return false;
    }

    /** Duyệt các tệp ảnh có khóa bắt đầu bằng {@code prefix}; {@code visitor} trả false để dừng sớm. */
    default void forEachFile(String prefix, java.util.function.Predicate<StoredFile> visitor) {
        throw new MediaStorageException("Nhà cung cấp " + name() + " không hỗ trợ liệt kê tệp", false);
    }

    /** Tên các thư mục con trực tiếp của {@code path}. */
    default java.util.List<String> listFolders(String path) {
        throw new MediaStorageException("Nhà cung cấp " + name() + " không hỗ trợ liệt kê thư mục", false);
    }
}
