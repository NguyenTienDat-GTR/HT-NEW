package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TrainingCourseResponse {

    // Giáo hạt mà khóa giáo phận dành cho.
    public record TargetDeanery(UUID id, String name) {
    }

    private String courseCode;
    private String courseName;
    private String courseTypeCode;
    private String courseTypeName;
    private UnitType managementUnitType;
    private Integer hostYear;
    private Integer yearsActive;
    // Điều kiện tham dự của khóa (từ loại khóa); chỉ có ở chi tiết khóa. requiredCurrentLevelName là tên cấp độ hiện tại yêu cầu.
    private String requiredCurrentLevelName;
    private Integer requiredMinAge;
    private String prerequisiteTypeName;
    private Integer maxYearsAfterPrerequisite;
    // Chỉ có ở chi tiết/cập nhật khóa: đã có hồ sơ tham gia thì khóa các trường loại khóa, năm tổ chức, số năm hoạt động, điểm đạt.
    private Boolean hasParticipations;
    // Số tham dự viên đã được duyệt của khóa.
    private Long approvedParticipantCount;
    private Boolean scoreLocked;
    private Boolean autoApproveEligible;
    private Boolean scoreVisible;
    private Boolean attendanceEnabled;
    private Boolean autoSendInvitation;
    // Khóa có cấp độ đạt được thì mới có chứng nhận và tab duyệt chứng nhận.
    private Boolean certificateRequired;
    // Khóa quá khứ: tạo thủ công, không loại khóa/thời gian đăng ký; resultingLevel là cấp độ đạt được của khóa.
    private Boolean pastCourse;
    private String resultingLevel;
    private String resultingLevelName;
    private java.time.OffsetDateTime finishedAt;
    private String finishedBy;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime registrationStartAt;
    private OffsetDateTime registrationEndAt;
    private UUID dioceseId;
    private UUID deaneryId;
    private String dioceseName;
    private String deaneryName;
    private Boolean registrationOpen;
    private CourseRegistrationStatus registrationStatus;
    private CourseOrganizingStatus organizingStatus;
    private List<String> recipientUsernames;
    // Giáo hạt mà khóa giáo phận dành cho (rỗng = mở cho tất cả).
    private List<UUID> targetDeaneryIds;
    private List<TargetDeanery> targetDeaneries;
    private Boolean openToOtherDeaneries;
    private String createdBy;
    private String updatedBy;
    private BigDecimal passingScore;
    private String additionalNotes;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String finishedByName;
    private String createdByName;
    private String updatedByName;
}
