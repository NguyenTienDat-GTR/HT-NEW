package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.training.enums.CourseType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class TrainingCourseResponse {
    private String courseCode;
    private String requirementCode;
    private String courseName;
    private Integer hostYear;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime registrationStartAt;
    private OffsetDateTime registrationEndAt;
    private CourseType courseType;
    private UUID dioceseId;
    private UUID deaneryId;
    private String scoreFormulaId;
    private String additionalNotes;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
