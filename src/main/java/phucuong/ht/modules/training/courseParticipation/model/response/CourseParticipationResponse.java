package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CourseParticipationResponse {
    private String id;
    private UUID leaderId;
    private String courseCode;
    private CourseType courseType;
    private LocalDate registrationDate;
    private ParticipationStatus participationStatus;
    private BigDecimal totalScore;
    private CourseResult result;
    private String review;
    private Boolean eligible;
    private String exceptionReason;
    private OffsetDateTime parishSubmittedAt;
    private OffsetDateTime deanerySubmittedAt;
    private WorkflowApprovalStatus deaneryApprovalStatus;
    private WorkflowApprovalStatus dioceseApprovalStatus;
    private WorkflowApprovalStatus certificateApprovalStatus;
    private OffsetDateTime certificateRequestedAt;
    private OffsetDateTime certificateApprovedAt;
    private String approvedBy;
    private Boolean status;
}
