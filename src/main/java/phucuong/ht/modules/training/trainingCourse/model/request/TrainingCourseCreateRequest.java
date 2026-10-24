package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class TrainingCourseCreateRequest {

    // Mã khóa do hệ thống tự sinh (UUID); client không gửi.

    @NotBlank
    private String courseName;

    @NotBlank
    private String courseTypeCode;

    private Integer hostYear;

    @Min(value = 0, message = "Số năm hoạt động không được âm")
    private Integer yearsActive;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    // Tài khoản nhận danh sách đăng ký; phải có quyền duyệt danh sách đăng ký thuộc phạm vi của khóa.
    private List<String> recipientUsernames;

    // null = giữ nguyên (khi sửa) hoặc tắt (khi tạo).
    private Boolean autoApproveEligible;

    private Boolean scoreVisible;

    // Bật điểm danh bằng mã QR (email thông tin tham dự chỉ kèm mã QR khi cờ này bật); null = tắt.
    private Boolean attendanceEnabled;

    // Tự gửi email thông tin tham dự khi hồ sơ được duyệt; null = tắt.
    private Boolean autoSendInvitation;

    private String additionalNotes;
}
