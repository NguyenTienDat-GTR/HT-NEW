package phucuong.ht.modules.training.crossDeanery.model;

// Trạng thái yêu cầu liên giáo hạt. EXPIRED chỉ được suy ra khi đọc (yêu cầu còn PENDING nhưng khóa đã hết hạn đăng ký), không lưu.
public enum CrossDeaneryStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
