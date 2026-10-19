package phucuong.ht.modules.training.trainingCourse.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

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

    // Cờ mở/đóng đăng ký thủ công; tự chuyển false khi hết thời gian đăng ký. Nhận đăng ký khi cờ true và còn trong thời gian.
    @Column(name = "registration_open", nullable = false)
    private Boolean registrationOpen = Boolean.TRUE;

    // Tài khoản nhận danh sách đăng ký (phải có quyền duyệt danh sách đăng ký).
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "training_course_recipient", joinColumns = @JoinColumn(name = "course_code"))
    @Column(name = "username")
    private Set<String> recipientUsernames = new LinkedHashSet<>();

    private String additionalNotes;
}
