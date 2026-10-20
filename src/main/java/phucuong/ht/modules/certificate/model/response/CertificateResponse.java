package phucuong.ht.modules.certificate.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CertificateResponse {
    private String certificateCode;
    private String oldCertificateCode;
    private String certificateName;
    private UUID leaderId;
    private String leaderFullName;
    private String courseCode;
    private String courseName;
    private BigDecimal totalScore;
    private BigDecimal passingScore;
    private Boolean passed;
    private Boolean approvalReasonRequired;
    private String result;
    private String signedBy;
    private LocalDate issueDate;
    private String imageUrl;
    private WorkflowApprovalStatus approvalStatus;
    private Boolean systemSuggested;
    private Boolean approvedByException;
    private String exceptionApprovalReason;
    private String rejectionReason;
    private OffsetDateTime approvedAt;
    private String approvedBy;
    private Boolean status;
}
