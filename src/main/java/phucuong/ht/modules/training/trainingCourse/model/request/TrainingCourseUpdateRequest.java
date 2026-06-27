package phucuong.ht.modules.training.trainingCourse.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.training.enums.CourseType;

import java.time.LocalDate;

@Getter
@Setter
public class TrainingCourseUpdateRequest {

    private String requirementCode;

    private String courseName;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private CourseType courseType;

    private String additionalNotes;
}
