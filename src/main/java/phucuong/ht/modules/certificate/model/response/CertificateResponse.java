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
    // Khóa đã kết thúc (finishedAt != null): không duyệt/từ chối được nữa, chỉ xuất PDF.
    private Boolean courseFinished;
    // Khóa quá khứ: duyệt chứng nhận phải chọn ngày duyệt.
    private Boolean coursePast;
    private BigDecimal totalScore;
    private Boolean scoreHidden;
    private BigDecimal passingScore;
    // Ghi chú của hồ sơ khóa huấn luyện (nhập ở tab chấm điểm).
    private String scoreNote;
    private Boolean passed;
    private Boolean approvalReasonRequired;
    private String result;
    private String signedBy;
    private phucuong.ht.modules.certificate.model.enums.CertificateSignerType signerType;
    private String signerName;
    private String signerTitle;
    private LocalDate issueDate;
    private String imageUrl;
    private WorkflowApprovalStatus approvalStatus;
    private Boolean systemSuggested;
    private Boolean approvedByException;
    // Chứng nhận ngoại lệ do người chấm điểm tạo cho hồ sơ không đạt (highlight ở tab duyệt, bắt buộc nhập lý do khi duyệt).
    private Boolean exceptionRequested;
    private String exceptionApprovalReason;
    private String rejectionReason;
    private OffsetDateTime approvedAt;
    private String approvedBy;
    private OffsetDateTime revokedAt;
    private String revokedBy;
    private String revocationReason;
    // MANUAL | COURSE_DISABLED (chỉ khi đang bị thu hồi); chứng nhận do khóa bị vô hiệu hóa không cấp lại riêng lẻ được.
    private String revocationSource;
    // Cấp lại chứng nhận đã bị thu hồi.
    private OffsetDateTime reissuedAt;
    private String reissuedBy;
    private String reissueReason;
    private Boolean status;

    // Chỉ có ở chi tiết (findById): thông tin huynh trưởng, khóa và kết quả để hiển thị theo nhóm.
    private String leaderHolyName;
    private LocalDate leaderBirthDate;
    private String leaderParishName;
    private String leaderDeaneryName;
    private String leaderDioceseName;
    private String courseTypeName;
    private Integer courseHostYear;
    private LocalDate courseStartDate;
    private LocalDate courseEndDate;
    private String resultingLevel;
    // Kết quả chung của hồ sơ sau khi kết thúc khóa / bị thu hồi chứng nhận: PASSED | FAILED.
    private String finalResult;
    private String approvedByName;
    private String revokedByName;
    private String reissuedByName;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
