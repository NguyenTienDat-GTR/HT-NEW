package phucuong.ht.modules.certificate.model.enums;

// Loại người ký chứng nhận: Linh mục tuyên úy (chức danh cố định) hoặc Khác (phải nhập chức danh).
// COURSE_CHAPLAIN chỉ dùng khi duyệt: hệ thống tự lấy cha tuyên úy của đơn vị tổ chức khóa, rồi lưu chứng nhận dưới dạng CHAPLAIN.
public enum CertificateSignerType {
    CHAPLAIN,
    COURSE_CHAPLAIN,
    OTHER
}
