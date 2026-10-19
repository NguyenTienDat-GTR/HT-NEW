package phucuong.ht.modules.training.courseType.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.OffsetDateTime;

@Getter
@Builder
public class TrainingCourseTypeResponse {
    private String typeCode;
    private String typeName;
    private UnitType managementUnitType;
    private LeaderLevel requiredCurrentLevel;
    private Integer requiredMinAge;
    private Integer requiredYearsActive;
    private String prerequisiteTypeCode;
    private String prerequisiteTypeName;
    private LeaderLevel resultingLevel;
    private String certificateShortCode;
    private Boolean status;
    private String createdBy;
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
