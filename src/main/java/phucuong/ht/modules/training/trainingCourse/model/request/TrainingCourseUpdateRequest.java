package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class TrainingCourseUpdateRequest {

    private String courseName;

    private String courseTypeCode;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    // null = giữ nguyên; danh sách (kể cả rỗng) = thay thế.
    private java.util.List<String> recipientUsernames;

    private String additionalNotes;
}
