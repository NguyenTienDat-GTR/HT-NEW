package phucuong.ht.modules.certificate.model.enums;

// Nguồn của lần thu hồi chứng nhận.
public enum CertificateRevocationSource {
    // Người có quyền thu hồi thủ công; chỉ cấp lại được bằng quyền cấp lại.
    MANUAL,
    // Khóa huấn luyện bị vô hiệu hóa; tự mở lại khi bật lại khóa, không cấp lại riêng lẻ.
    COURSE_DISABLED
}
