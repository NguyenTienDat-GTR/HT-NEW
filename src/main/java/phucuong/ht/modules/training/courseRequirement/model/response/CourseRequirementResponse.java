package phucuong.ht.modules.training.courseRequirement.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.enums.CourseType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CourseRequirementResponse {
    private String requirementCode;
    private String requirementName;
    private CourseType courseType;
    private LeaderLevel requiredCurrentLevel;
    private Integer requiredMinAge;
    private Integer requiredYearsActive;
    private UUID dioceseId;
    private UUID deaneryId;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
