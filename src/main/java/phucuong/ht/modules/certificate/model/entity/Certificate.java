package phucuong.ht.modules.certificate.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "certificate")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Certificate extends BaseModel {

    @Id
    @Column(name = "certificate_code")
    private String certificateCode;

    private String oldCertificateCode;

    private String certificateName;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    @ManyToOne
    @JoinColumn(name = "course_code")
    private TrainingCourse course;

    private String result;

    private String signedBy;

    // Người ký ghi trực tiếp vào chứng nhận khi duyệt (dùng cho mail merge khi xuất).
    @Enumerated(EnumType.STRING)
    @Column(name = "signer_type")
    private phucuong.ht.modules.certificate.model.enums.CertificateSignerType signerType;

    @Column(name = "signer_name")
    private String signerName;

    @Column(name = "signer_title")
    private String signerTitle;

    private LocalDate issueDate;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus approvalStatus;

    @Column(name = "system_suggested")
    private Boolean systemSuggested;

    @Column(name = "approved_by_exception")
    private Boolean approvedByException;

    @Column(name = "exception_approval_reason")
    private String exceptionApprovalReason;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    private OffsetDateTime approvedAt;

    private String approvedBy;

    // Thu hồi chứng nhận đã duyệt (approvalStatus = REVOKED).
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revocation_reason")
    private String revocationReason;

    // Nguồn thu hồi: MANUAL (người dùng thu hồi) hoặc COURSE_DISABLED (khóa bị vô hiệu hóa, tự mở lại khi bật lại khóa).
    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_source")
    private phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource revocationSource;

    // Cấp lại chứng nhận đã bị thu hồi.
    @Column(name = "reissued_at")
    private OffsetDateTime reissuedAt;

    @Column(name = "reissued_by")
    private String reissuedBy;

    @Column(name = "reissue_reason")
    private String reissueReason;
}
