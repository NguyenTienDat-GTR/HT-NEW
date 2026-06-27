package phucuong.ht.modules.training.courseRequirement.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.enums.CourseType;

@Getter
@Setter
public class CourseRequirementCreateRequest {

    @NotBlank
    private String requirementCode;

    @NotBlank
    private String requirementName;

    @NotNull
    private CourseType courseType;

    @NotNull
    private LeaderLevel requiredCurrentLevel;

    private Integer requiredMinAge;

    private Integer requiredYearsActive;
}
