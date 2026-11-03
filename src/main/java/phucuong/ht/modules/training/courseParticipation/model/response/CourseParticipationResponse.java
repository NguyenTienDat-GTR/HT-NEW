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
    private String leaderHolyName;
    private String leaderFullName;
    private String courseCode;
    private String courseName;
    // Khóa đã kết thúc (thủ công hoặc quá ngày kết thúc): hồ sơ chỉ xem, không duyệt/xóa được nữa.
    private Boolean courseFinished;
    // Khóa quá khứ: duyệt chứng nhận phải chọn ngày duyệt.
    private Boolean coursePast;
    private String courseTypeCode;
    private LocalDate registrationDate;
    private ParticipationStatus participationStatus;
    private BigDecimal totalScore;
    // true: điểm bị ẩn với người xem hiện tại (khóa không bật hiển thị điểm và người xem thấp hơn cấp sở hữu).
    private Boolean scoreHidden;
    private BigDecimal passingScore;
    private Boolean passed;
    private CourseResult result;
    private String review;
    private String scoreNote;
    private Boolean eligible;
    private String exceptionReason;
    // Ghi chú của người gửi; hồ sơ gửi ngoài đợt (kèm lí do) hoặc liên giáo hạt (kèm giáo hạt nhà, người đồng ý, lí do).
    private String registrationNote;
    private Boolean offTarget;
    private String offTargetReason;
    private Boolean crossDeanery;
    private String crossDeaneryReason;
    private String homeDeaneryName;
    private String homeDeaneryApprovedBy;
    private OffsetDateTime homeDeaneryApprovedAt;
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
    // Đơn vị sở hữu khóa (giáo phận hoặc giáo hạt): để giao diện chỉ hiện thao tác duyệt/chấm điểm trên hồ sơ của khóa thuộc đúng cấp của tài khoản.
    private java.util.UUID courseDioceseId;
    private java.util.UUID courseDeaneryId;
    private WorkflowApprovalStatus certificateApprovalStatus;
    private OffsetDateTime certificateRequestedAt;
    private OffsetDateTime certificateApprovedAt;
    private phucuong.ht.modules.training.enums.CourseFinalResult finalResult;
    private String approvedBy;
    private List<ComponentScoreResponse> componentScores;
    private Boolean status;
    private String approvedByName;
    // Đội được chia trong khóa và giới tính của huynh trưởng (NAM/NU).
    private String teamName;
    private String leaderGender;
    // Điểm danh và email thông tin tham dự (null = chưa điểm danh / chưa gửi); invitationError là lý do gửi thất bại gần nhất.
    private OffsetDateTime checkedInAt;
    private String checkedInBy;
    private OffsetDateTime invitationSentAt;
    private String invitationError;
    private Boolean attendanceEnabled;
    private String leaderEmail;
    private String leaderPhone;
    private OffsetDateTime createdAt;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
