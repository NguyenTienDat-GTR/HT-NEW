package phucuong.ht.modules.training.enums;

// Trạng thái nhận đăng ký của khóa huấn luyện (lưu trong DB, scheduler tự chuyển theo thời gian đăng ký).
public enum CourseRegistrationStatus {
    NOT_STARTED,
    OPEN,
    // Đóng thủ công trong thời gian đăng ký; chỉ mở lại thủ công.
    CLOSED,
    ENDED
}
