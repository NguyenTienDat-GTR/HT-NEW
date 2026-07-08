package phucuong.ht.modules.training.trainingCourse.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.training.enums.CourseType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class TrainingCourseUpdateRequest {

    private String requirementCode;

    private String courseName;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    private CourseType courseType;

    private String additionalNotes;
}
