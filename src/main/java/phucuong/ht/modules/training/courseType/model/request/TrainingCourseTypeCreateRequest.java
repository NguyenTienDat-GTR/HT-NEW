package phucuong.ht.modules.training.courseType.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

@Getter
@Setter
public class TrainingCourseTypeCreateRequest {

    @NotBlank
    private String typeCode;

    @NotBlank
    private String typeName;

    @NotNull
    private UnitType managementUnitType;

    private LeaderLevel requiredCurrentLevel;

    private Integer requiredMinAge;

    private Integer requiredYearsActive;

    private String prerequisiteTypeCode;

    private LeaderLevel resultingLevel;

    private String certificateShortCode;
}
