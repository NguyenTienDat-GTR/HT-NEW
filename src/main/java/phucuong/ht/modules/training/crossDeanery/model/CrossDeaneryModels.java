package phucuong.ht.modules.training.crossDeanery.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Yêu cầu / phản hồi của việc gửi hồ sơ liên giáo hạt (giáo xứ ở giáo hạt này gửi sang khóa giáo hạt của giáo hạt khác).
public final class CrossDeaneryModels {

    private CrossDeaneryModels() {
    }

    // Khóa giáo hạt của giáo hạt khác (cùng giáo phận) đang mở đăng ký.
    public record OpenCourse(String courseCode, String courseName, String courseTypeName, UUID deaneryId, String deaneryName,
                             LocalDate startDate, LocalDate endDate, OffsetDateTime registrationEndAt) {
    }

    // Thông tin khóa huấn luyện để giáo hạt nhà xem khi cho phép khóa / duyệt yêu cầu liên giáo hạt.
    public record CourseInfo(String courseTypeName, String location, LocalDate startDate, LocalDate endDate,
                             OffsetDateTime registrationStartAt, OffsetDateTime registrationEndAt, String additionalNotes,
                             List<String> requirements) {
    }

    // Khóa giáo hạt khác đang mở liên giáo hạt, kèm quyết định của giáo hạt nhà: decision = PENDING (chưa quyết định) / ALLOWED / DENIED.
    public record CourseInvitation(String courseCode, String courseName, UUID hostDeaneryId, String hostDeaneryName, CourseInfo course,
                                   String decision, String decisionNote, String decidedByName, OffsetDateTime decidedAt,
                                   boolean registrationClosed, boolean canDecide) {
    }

    // allowed = true: cho phép giáo xứ trong giáo hạt gửi hồ sơ sang khóa; false: từ chối (bắt buộc note).
    public record AccessDecisionRequest(boolean allowed, @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    // Huynh trưởng có thể gửi: eligible = đủ điều kiện của khóa đích (không đủ thì gửi dạng ngoại lệ kèm lí do);
    // blockedReason khác null = không gửi được (đã có hồ sơ, đang chờ đồng ý, cấp độ vượt mức ngoại lệ...).
    public record Candidate(UUID leaderId, String holyName, String fullName, String leaderLevel, UUID parishId, String parishName,
                            UUID deaneryId, String deaneryName, boolean eligible, String blockedReason) {
    }

    public record ItemInput(@NotNull(message = "Thiếu huynh trưởng") UUID leaderId,
                            @NotNull(message = "Mỗi hồ sơ phải có lí do gửi sang giáo hạt khác")
                            @Size(min = 1, max = 500, message = "Lí do gửi tối đa 500 ký tự") String reason,
                            @Size(max = 500, message = "Lí do ngoại lệ tối đa 500 ký tự") String exceptionReason,
                            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note) {
    }

    public record CreateRequest(@NotEmpty(message = "Chưa chọn huynh trưởng nào")
                                @Size(max = 500, message = "Mỗi lần gửi tối đa 500 hồ sơ")
                                @Valid List<ItemInput> items) {
    }

    // approved = false: từ chối cả yêu cầu (bắt buộc note). approved = true: itemIds rỗng = đồng ý tất cả; ngược lại chỉ các hồ sơ được chọn
    // (các hồ sơ còn lại bị từ chối, bắt buộc note).
    public record DecisionRequest(boolean approved, @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note, List<String> itemIds) {
    }

    public record ItemResponse(String id, UUID leaderId, String holyName, String fullName, UUID parishId, String parishName,
                               String reason, String exceptionReason, String note, CrossDeaneryItemStatus itemStatus, String participationId) {
    }

    public record RequestResponse(String id, String courseCode, String courseName, UUID courseDeaneryId, String courseDeaneryName,
                                  UUID homeDeaneryId, String homeDeaneryName, UUID parishId, String parishName, String requesterType,
                                  CrossDeaneryStatus requestStatus, String decisionNote, String decidedByName, OffsetDateTime decidedAt,
                                  String createdByName, OffsetDateTime createdAt, boolean canDecide, boolean canCancel,
                                  List<ItemResponse> items, CourseInfo course) {
    }

    public record SubmitResult(RequestResponse request, int createdParticipations) {
    }
}
