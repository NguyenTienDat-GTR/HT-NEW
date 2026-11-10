package phucuong.ht.modules.training.courseType.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.OffsetDateTime;

@Getter
@Builder(toBuilder = true)
public class TrainingCourseTypeResponse {
    private String typeCode;
    private java.util.UUID dioceseId;
    private String typeName;
    private UnitType managementUnitType;
    private String requiredCurrentLevel;
    private Integer requiredMinAge;
    private Integer maxYearsAfterPrerequisite;
    private String prerequisiteTypeCode;
    private String prerequisiteTypeName;
    private String resultingLevel;
    private Boolean hasCertificate;
    private String certificateShortCode;
    private Boolean status;
    private String createdBy;
    private String createdByName;
    private String updatedBy;
    private String updatedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
