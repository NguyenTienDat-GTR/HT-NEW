package phucuong.ht.modules.media.model.enums;

public enum MediaStatus {
    /** Mới tải lên, chưa gắn vào bản ghi nào. */
    PENDING,
    /** Đã gắn vào bản ghi nghiệp vụ. */
    ACTIVE,
    /** Đã gỡ khỏi bản ghi, chờ job dọn xóa thật trên cloud. */
    DELETED
}
