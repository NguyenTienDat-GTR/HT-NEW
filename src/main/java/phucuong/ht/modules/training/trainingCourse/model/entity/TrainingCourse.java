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
@ToString(exclude = {"recipientUsernames", "targetDeaneryIds"})
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
    @JoinColumn(name = "course_type_code")
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

    // Giáo hạt mà khóa giáo phận này dành cho (một đợt); rỗng = mở cho tất cả. Hồ sơ của giáo hạt khác vẫn gửi được nhưng phải kèm lí do và duyệt thủ công.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "training_course_target_deanery", joinColumns = @JoinColumn(name = "course_code"))
    @Column(name = "deanery_id")
    private Set<java.util.UUID> targetDeaneryIds = new LinkedHashSet<>();

    // Khóa chỉnh sửa điểm: admin giáo phận/giáo hạt (có quyền lock) bật/tắt trong thời gian diễn ra khóa; khi khóa thì không nhập điểm được.
    @Column(name = "score_locked", nullable = false)
    private Boolean scoreLocked = Boolean.FALSE;

    // Khóa giáo hạt mở cho các giáo hạt khác cùng giáo phận: giáo hạt khác phải cho phép thì giáo xứ của họ mới gửi hồ sơ liên giáo hạt được.
    @Column(name = "open_to_other_deaneries", nullable = false)
    private Boolean openToOtherDeaneries = Boolean.FALSE;

    // Bật thì hệ thống tự duyệt các hồ sơ hợp lệ (không phải ngoại lệ) ngay sau khi giáo xứ gửi danh sách.
    @Column(name = "auto_approve_eligible", nullable = false)
    private Boolean autoApproveEligible = Boolean.FALSE;

    // Bật điểm danh: hồ sơ trong danh sách tham dự có mã QR ký số để quét điểm danh; chỉ khóa thường (không phải khóa quá khứ).
    @Column(name = "attendance_enabled", nullable = false)
    private Boolean attendanceEnabled = Boolean.FALSE;

    // Bật thì tự gửi email thông tin tham dự cho hồ sơ ngay khi được duyệt vào danh sách tham dự (kèm mã QR khi khóa bật điểm danh).
    @Column(name = "auto_send_invitation", nullable = false)
    private Boolean autoSendInvitation = Boolean.FALSE;

    // Bật thì cấp thấp hơn cấp sở hữu khóa (giáo xứ, chính huynh trưởng) mới thấy điểm trong chứng nhận và kết quả khóa.
    @Column(name = "score_visible", nullable = false)
    private Boolean scoreVisible = Boolean.FALSE;

    // Kết thúc khóa: thời điểm và người kết thúc; có giá trị thì khóa luôn ở trạng thái đã kết thúc.
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "finished_by")
    private String finishedBy;

    private String additionalNotes;

    // Khóa trong quá khứ: tạo thủ công bởi tài khoản được super-admin cấp quyền riêng, không có loại khóa và thời gian đăng ký,
    // đã kết thúc ngay khi tạo; danh sách tham dự được import từ Excel.
    @Column(name = "is_past_course", nullable = false)
    private Boolean pastCourse = Boolean.FALSE;

    // Cấp độ đạt được của khóa quá khứ (khóa thường lấy theo loại khóa).
    @Column(name = "resulting_level")
    private String resultingLevel;

    public boolean isPast() {
        return Boolean.TRUE.equals(pastCourse);
    }
}
