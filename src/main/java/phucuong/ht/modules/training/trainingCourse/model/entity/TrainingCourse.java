package phucuong.ht.modules.training.trainingCourse.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "training_course")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "recipientUsernames")
@EqualsAndHashCode(callSuper = true)
public class TrainingCourse extends BaseModel {

    @Id
    @Column(name = "course_code")
    private String courseCode;

    private String courseName;

    private Integer hostYear;

    // Số năm tối thiểu giữa năm đạt cấp độ của khóa tiên quyết và năm tổ chức khóa này (mặc định 0).
    @Column(name = "years_active", nullable = false)
    private Integer yearsActive = 0;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    // Loại khóa (catalog quản lý được), đã bao gồm điều kiện tham dự.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_type_code", nullable = false)
    private TrainingCourseType courseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;

    // Điểm tổng kết tối thiểu để đạt khóa.
    @Column(name = "passing_score", nullable = false)
    private BigDecimal passingScore;

    // Trạng thái đăng ký: scheduler tự mở khi đến giờ, tự đóng khi hết hạn; CLOSED là đóng thủ công.
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false)
    private CourseRegistrationStatus registrationStatus = CourseRegistrationStatus.NOT_STARTED;

    // Trạng thái tổ chức theo startDate/endDate: scheduler tự chuyển.
    @Enumerated(EnumType.STRING)
    @Column(name = "organizing_status", nullable = false)
    private CourseOrganizingStatus organizingStatus = CourseOrganizingStatus.UPCOMING;

    // Tài khoản nhận danh sách đăng ký (phải có quyền duyệt danh sách đăng ký).
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "training_course_recipient", joinColumns = @JoinColumn(name = "course_code"))
    @Column(name = "username")
    private Set<String> recipientUsernames = new LinkedHashSet<>();

    // Khóa chỉnh sửa điểm: admin giáo phận/giáo hạt (có quyền lock) bật/tắt trong thời gian diễn ra khóa; khi khóa thì không nhập điểm được.
    @Column(name = "score_locked", nullable = false)
    private Boolean scoreLocked = Boolean.FALSE;

    private String additionalNotes;
}
