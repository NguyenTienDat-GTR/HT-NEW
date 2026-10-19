package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class TrainingCourseCreateRequest {

    // Mã khóa do hệ thống tự sinh (UUID); client không gửi.

    @NotBlank
    private String courseName;

    @NotBlank
    private String courseTypeCode;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    // Tài khoản nhận danh sách đăng ký; phải có quyền duyệt danh sách đăng ký thuộc phạm vi của khóa.
    private java.util.List<String> recipientUsernames;

    private String additionalNotes;
}
