package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.training.enums.CourseType;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TrainingRegistrationBatchResponse {
    private String courseCode;
    private CourseType courseType;
    private UnitType senderUnitType;
    private UUID senderUnitId;
    private String senderUnitName;
    private Integer totalCount;
    private Integer autoApprovedCount;
    private Integer pendingCount;
    private List<CourseParticipationResponse> participations;
}
