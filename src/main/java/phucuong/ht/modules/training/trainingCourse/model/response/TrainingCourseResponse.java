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
    private String courseCode;
    private String courseName;
    private String courseTypeCode;
    private String courseTypeName;
    private UnitType managementUnitType;
    private Integer hostYear;
    private Integer yearsActive;
    // Chỉ có ở chi tiết/cập nhật khóa: đã có hồ sơ tham gia thì khóa các trường loại khóa, năm tổ chức, số năm hoạt động, điểm đạt.
    private Boolean hasParticipations;
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
    private String createdBy;
    private String updatedBy;
    private BigDecimal passingScore;
    private String additionalNotes;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
