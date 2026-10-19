package phucuong.ht.modules.training.courseType.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

@Getter
@Setter
public class TrainingCourseTypeUpdateRequest {

    private String typeName;

    private UnitType managementUnitType;

    private LeaderLevel requiredCurrentLevel;

    private Integer requiredMinAge;

    private Integer requiredYearsActive;

    private String prerequisiteTypeCode;

    private LeaderLevel resultingLevel;

    private String certificateShortCode;

    // Cho phép gỡ khóa tiên quyết khi gửi true.
    private Boolean clearPrerequisite;
}
