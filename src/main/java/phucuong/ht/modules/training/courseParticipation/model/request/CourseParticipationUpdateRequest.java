package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class CourseParticipationUpdateRequest {

    private UUID leaderId;

    private String courseCode;

    private LocalDate registrationDate;

    private ParticipationStatus participationStatus;

    private BigDecimal totalScore;

    private CourseResult result;

    private String review;

    private Boolean eligible;

    private String exceptionReason;

    private OffsetDateTime parishSubmittedAt;



    private WorkflowApprovalStatus approvalStatus;

    private WorkflowApprovalStatus certificateApprovalStatus;

    private OffsetDateTime certificateRequestedAt;

    private OffsetDateTime certificateApprovedAt;

    private String approvedBy;
}
