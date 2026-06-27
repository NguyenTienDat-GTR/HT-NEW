package phucuong.ht.modules.training.courseRequirement.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.enums.CourseType;

@Getter
@Setter
public class CourseRequirementUpdateRequest {

    private String requirementName;

    private CourseType courseType;

    private LeaderLevel requiredCurrentLevel;

    private Integer requiredMinAge;

    private Integer requiredYearsActive;
}
