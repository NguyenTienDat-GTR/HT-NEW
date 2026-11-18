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
    // Lệ phí: feeTotal = lệ phí × số người trong danh sách tham dự, feePaidTotal = lệ phí × số người đã đóng, feeRemaining = phần còn thiếu.
    // Khóa không thu lệ phí: các số tiền và số người đều 0, feeAmount null.
    public record Summary(String courseCode, boolean attendanceEnabled, boolean autoSendInvitation, int totalParticipants,
                          int checkedIn, int notCheckedIn, int invitationSent, int invitationPending, int invitationFailed,
                          int withoutEmail, boolean feeEnabled, Long feeAmount, long feeTotal, long feePaidTotal, long feeRemaining,
                          int feePaidCount, int feeUnpaidCount) {
    }

    // Ghi nhận đóng lệ phí: gửi trạng thái đích (true = đã đóng), gửi lặp cho cùng kết quả.
    public record FeeRequest(@NotNull(message = "Phải chọn trạng thái đóng lệ phí") Boolean paid) {
    }

    // Trạng thái đóng lệ phí của một hồ sơ sau khi ghi nhận.
    public record FeeResult(String participationId, boolean feePaid, OffsetDateTime feePaidAt, String feePaidBy) {
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
                                 OffsetDateTime checkedInAt, String checkedInBy, OffsetDateTime feePaidAt) {
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
    // feePaid: true = đã đóng lệ phí, false = chưa đóng, null = tất cả.
    public record ParticipantQuery(String search, String team, Boolean checkedIn, InvitationFilter invitation, int page, int size, Boolean feePaid) {
        public ParticipantQuery(String search, String team, Boolean checkedIn, InvitationFilter invitation, int page, int size) {
            this(search, team, checkedIn, invitation, page, size, null);
        }
    }

    // feeEnabled / feeAmount / feePaidAt: để màn quét hiện số tiền và trạng thái đóng lệ phí của người vừa quét.
    public record ScanResult(ScanStatus status, String participationId, String courseCode, String courseName, String holyName, String fullName, String parishName,
                             String deaneryName, String teamName, OffsetDateTime checkedInAt, boolean feeEnabled, Long feeAmount, OffsetDateTime feePaidAt) {
    }
}
