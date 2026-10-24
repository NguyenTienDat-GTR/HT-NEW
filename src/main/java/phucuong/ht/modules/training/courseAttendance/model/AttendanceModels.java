package phucuong.ht.modules.training.courseAttendance.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// Yêu cầu / phản hồi của điểm danh khóa huấn luyện.
public final class AttendanceModels {

    private AttendanceModels() {
    }

    public record ScanRequest(@NotBlank(message = "Mã QR không được để trống") @Size(max = 200, message = "Mã QR quá dài") String token) {
    }

    public record CheckInRequest(@NotNull(message = "Phải chọn trạng thái điểm danh") Boolean checkedIn) {
    }

    // Tổng quan điểm danh + tình hình gửi email thông tin tham dự của khóa.
    public record Summary(String courseCode, boolean attendanceEnabled, boolean autoSendInvitation, int totalParticipants,
                          int checkedIn, int notCheckedIn, int invitationSent, int invitationPending, int invitationFailed,
                          int withoutEmail) {
    }

    public enum ScanStatus {
        CHECKED_IN,
        ALREADY_CHECKED_IN,
        CHECK_IN_CANCELLED,
        // Chỉ tra cứu: hồ sơ hợp lệ và chưa điểm danh, chờ người dùng xác nhận.
        READY
    }

    // Kết quả quét: người vừa điểm danh (hoặc đã điểm danh trước đó, kèm giờ điểm danh lần đầu).
    // Một dòng ở danh sách điểm danh / email của khóa. emailProblem: lý do email không dùng được (null = hợp lệ).
    public record ParticipantRow(String id, String holyName, String fullName, String parishName, String deaneryName, String teamName,
                                 String email, String emailProblem, OffsetDateTime invitationSentAt, String invitationError,
                                 OffsetDateTime checkedInAt, String checkedInBy) {
    }

    // Chi tiết một hồ sơ: thông tin huynh trưởng, tình trạng email, điểm danh và người tạo / duyệt / điểm danh (kèm tên hiển thị).
    public record ParticipantDetail(String id, String courseCode, String courseName, boolean attendanceEnabled,
                                    String holyName, String fullName, String gender, java.time.LocalDate birthDate, String phone,
                                    String parishName, String deaneryName, String dioceseName, String teamName, String leaderLevel,
                                    String email, String emailProblem, OffsetDateTime invitationSentAt, String invitationError,
                                    OffsetDateTime checkedInAt, String checkedInBy, String checkedInByName,
                                    java.time.LocalDate registrationDate, OffsetDateTime parishSubmittedAt,
                                    String approvalStatus, String approvedBy, String approvedByName,
                                    OffsetDateTime createdAt, String createdBy, String createdByName,
                                    OffsetDateTime updatedAt, String updatedBy, String updatedByName) {
    }

    public enum InvitationFilter {
        SENT, PENDING, FAILED, NO_EMAIL
    }

    // Bộ lọc danh sách: search theo tên thánh / họ tên / giáo xứ / giáo hạt / đội / email (không phân biệt hoa thường và dấu);
    // team: đúng tên đội (rỗng hoặc null = mọi đội); checkedIn: true / false / null = tất cả; invitation: lọc theo tình trạng email.
    public record ParticipantQuery(String search, String team, Boolean checkedIn, InvitationFilter invitation, int page, int size) {
    }

    public record ScanResult(ScanStatus status, String participationId, String holyName, String fullName, String parishName,
                             String deaneryName, String teamName, OffsetDateTime checkedInAt) {
    }
}
