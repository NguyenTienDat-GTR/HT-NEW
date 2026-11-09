package phucuong.ht.modules.training.courseCopy.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import phucuong.ht.modules.training.enums.CourseFinalResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Sao chép danh sách tham dự của các khóa tiên quyết đã kết thúc sang danh sách tham dự của khóa huấn luyện.
public final class CourseCopyModels {

    private CourseCopyModels() {
    }

    // Một khóa tiên quyết đã kết thúc có thể chọn làm nguồn; participantCount/passedCount = số hồ sơ trong danh sách tham dự / đạt.
    public record SourceCourse(String courseCode, String courseName, Integer hostYear, LocalDate startDate, LocalDate endDate,
                               OffsetDateTime finishedAt, String ownerName, long participantCount, long passedCount) {
    }

    // prerequisiteTypeName = tên loại khóa tiên quyết; available = khóa đích còn sao chép được (khóa thường, còn bật, chưa kết thúc).
    public record SourcesResponse(String prerequisiteTypeName, boolean available, List<SourceCourse> sources) {
    }

    // Một huynh trưởng có thể sao chép. Trùng ở nhiều khóa nguồn thì lấy hồ sơ đạt (khóa kết thúc gần nhất).
    // eligible = đủ điều kiện của khóa đích (chỉ để tham khảo, không chặn sao chép).
    // blockedReason khác null = không sao chép được (đã có hồ sơ trong khóa, đã ngưng hoạt động...).
    public record Candidate(UUID leaderId, String holyName, String fullName, String gender, String leaderLevel,
                            UUID deaneryId, String deaneryName, UUID parishId, String parishName,
                            String sourceCourseCode, String sourceCourseName, CourseFinalResult finalResult,
                            boolean eligible, String blockedReason) {
    }

    // includeFailed = true: gồm cả hồ sơ có kết quả chung không đạt.
    public record CopyRequest(@NotEmpty(message = "Chưa chọn khóa tiên quyết nào")
                              @Size(max = 50, message = "Mỗi lần chọn tối đa 50 khóa") List<String> sourceCourseCodes,
                              boolean includeFailed,
                              @NotEmpty(message = "Chưa chọn huynh trưởng nào để sao chép")
                              @Size(max = 5000, message = "Mỗi lần sao chép tối đa 5000 hồ sơ") List<UUID> leaderIds) {
    }

    // Khóa nguồn chọn được khi tạo / sửa khóa: khóa đã kết thúc hoặc khóa quá khứ trong phạm vi. courseTypeName = null với khóa quá khứ.
    public record AnySource(String courseCode, String courseName, Integer hostYear, String courseTypeName, boolean pastCourse,
                            LocalDate startDate, LocalDate endDate, String ownerName, long participantCount, long passedCount) {
    }

    // Sao chép ngay vào khóa đã có (nút ở tab Danh sách tham dự): copyMode ALL | PASSED_ONLY (null = ALL); leaderIds khác rỗng = chỉ các huynh trưởng này.
    public record CopyToCourseRequest(@NotEmpty(message = "Chưa chọn khóa nguồn nào")
                                      @Size(max = 50, message = "Mỗi lần chọn tối đa 50 khóa") List<String> sourceCourseCodes,
                                      @jakarta.validation.constraints.Pattern(regexp = "ALL|PASSED_ONLY", message = "Chế độ sao chép phải là ALL hoặc PASSED_ONLY") String copyMode,
                                      @Size(max = 5000, message = "Mỗi lần sao chép tối đa 5000 hồ sơ") List<UUID> leaderIds) {
    }

    public record Skipped(UUID leaderId, String fullName, String reason) {
    }

    public record CopyResult(int copied, List<Skipped> skipped) {
    }
}
