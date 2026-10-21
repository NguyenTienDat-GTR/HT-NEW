package phucuong.ht.modules.training.courseParticipation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "course_participation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class CourseParticipation extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private TrainingCourse course;

    private LocalDate registrationDate;

    @Enumerated(EnumType.STRING)
    private ParticipationStatus participationStatus;

    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    private CourseResult result;

    // Kết quả chung chốt khi kết thúc khóa: PASSED được thăng cấp, FAILED không thăng cấp.
    @Enumerated(EnumType.STRING)
    @Column(name = "final_result")
    private phucuong.ht.modules.training.enums.CourseFinalResult finalResult;

    private String review;

    // Ghi chú nhập ở tab chấm điểm; vẫn sửa được khi điểm đã khóa, không sửa được khi khóa kết thúc hoặc bị vô hiệu hóa.
    @Column(name = "score_note", columnDefinition = "TEXT")
    private String scoreNote;

    private Boolean eligible;

    private String exceptionReason;

    // Cấp độ đạt được của hồ sơ ngoại lệ do người duyệt chọn; null = theo cấp độ đạt được của loại khóa.
    @Column(name = "target_level")
    private String targetLevel;

    private OffsetDateTime parishSubmittedAt;

    // Trạng thái duyệt đăng ký: khóa giáo hạt do giáo hạt duyệt, khóa giáo phận do giáo phận duyệt.
    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus approvalStatus;

    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus certificateApprovalStatus;

    private OffsetDateTime certificateRequestedAt;

    private OffsetDateTime certificateApprovedAt;

    private String approvedBy;

    @OneToMany(mappedBy = "participation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseParticipationComponentScore> componentScores = new ArrayList<>();
}
