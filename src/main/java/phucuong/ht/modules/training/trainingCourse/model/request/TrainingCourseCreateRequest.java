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

    // Khóa có mở đăng ký hay không; tắt thì bỏ qua thời gian đăng ký. null = mở (khi tạo).
    private Boolean registrationEnabled;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    // Tài khoản nhận danh sách đăng ký; phải có quyền duyệt danh sách đăng ký thuộc phạm vi của khóa.
    private List<String> recipientUsernames;

    // Giáo hạt mà khóa giáo phận dành cho (một đợt); null/rỗng = mở cho tất cả. Chỉ áp dụng cho khóa giáo phận.
    private List<java.util.UUID> targetDeaneryIds;

    // Thành phần điểm của khóa (tên, điểm tối đa, hệ số, cờ tính tổng kết). Bắt buộc khi tạo; khi sửa null = giữ nguyên.
    @jakarta.validation.Valid
    private List<phucuong.ht.modules.training.courseScoreComponent.model.request.ScoreComponentInput> scoreComponents;

    // Khóa giáo hạt mở đăng ký cho các giáo hạt khác cùng giáo phận (chờ từng giáo hạt cho phép); null = tắt.
    private Boolean openToOtherDeaneries;

    // null = giữ nguyên (khi sửa) hoặc tắt (khi tạo).
    private Boolean autoApproveEligible;

    private Boolean scoreVisible;

    // Bật điểm danh bằng mã QR (email thông tin tham dự chỉ kèm mã QR khi cờ này bật); null = tắt.
    private Boolean attendanceEnabled;

    // Tự gửi email thông tin tham dự khi hồ sơ được duyệt; null = tắt.
    private Boolean autoSendInvitation;

    // Sao chép danh sách tham dự từ các khóa nguồn (khóa đã kết thúc / khóa quá khứ cùng phạm vi) ngay khi lưu khóa. null hoặc rỗng = không sao chép.
    private List<String> copySourceCourseCodes;

    // ALL = mọi hồ sơ trong danh sách tham dự của khóa nguồn; PASSED_ONLY = chỉ hồ sơ có kết quả đạt. null = ALL.
    @jakarta.validation.constraints.Pattern(regexp = "ALL|PASSED_ONLY", message = "Chế độ sao chép phải là ALL hoặc PASSED_ONLY")
    private String copyMode;

    // Chỉ sao chép các huynh trưởng này từ khóa nguồn (chọn riêng ở hộp thoại); null / rỗng = sao chép theo copyMode.
    private List<java.util.UUID> copyLeaderIds;

    private String additionalNotes;
}
