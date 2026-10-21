package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CourseParticipationResponse {
    private String id;
    private UUID leaderId;
    private String leaderFullName;
    private String courseCode;
    private String courseName;
    private String courseTypeCode;
    private LocalDate registrationDate;
    private ParticipationStatus participationStatus;
    private BigDecimal totalScore;
    private BigDecimal passingScore;
    private Boolean passed;
    private CourseResult result;
    private String review;
    private Boolean eligible;
    private String exceptionReason;
    // Cấp hiện tại của huynh trưởng, cấp độ đạt được của loại khóa và cấp đạt được do người duyệt chọn (hồ sơ ngoại lệ).
    private String leaderLevel;
    private String courseResultingLevel;
    private String targetLevel;
    private OffsetDateTime parishSubmittedAt;
    private WorkflowApprovalStatus approvalStatus;
    // Người gửi đăng ký (dùng để quyết định ai được thu hồi) và đơn vị của huynh trưởng.
    private String createdBy;
    private java.util.UUID parishId;
    private String parishName;
    private java.util.UUID deaneryId;
    private String deaneryName;
    // Cấp quản lý khóa (DEANERY/DIOCESE): quyết định endpoint duyệt phù hợp.
    private phucuong.ht.common.enums.UnitType managementUnitType;
    private WorkflowApprovalStatus certificateApprovalStatus;
    private OffsetDateTime certificateRequestedAt;
    private OffsetDateTime certificateApprovedAt;
    private phucuong.ht.modules.training.enums.CourseFinalResult finalResult;
    private String approvedBy;
    private List<ComponentScoreResponse> componentScores;
    private Boolean status;
}
