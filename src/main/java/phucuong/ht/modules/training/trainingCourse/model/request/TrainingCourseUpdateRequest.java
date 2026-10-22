package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class TrainingCourseUpdateRequest {

    private String courseName;

    private String courseTypeCode;

    private Integer hostYear;

    @Min(value = 0, message = "Số năm hoạt động không được âm")
    private Integer yearsActive;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    // null = giữ nguyên; danh sách (kể cả rỗng) = thay thế.
    private List<String> recipientUsernames;

    // null = giữ nguyên (khi sửa) hoặc tắt (khi tạo).
    private Boolean autoApproveEligible;

    private Boolean scoreVisible;

    private String additionalNotes;
}
