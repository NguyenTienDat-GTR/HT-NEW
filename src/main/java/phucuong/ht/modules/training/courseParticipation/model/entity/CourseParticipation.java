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

    private String review;

    private Boolean eligible;

    private String exceptionReason;

    private OffsetDateTime parishSubmittedAt;

    private OffsetDateTime deanerySubmittedAt;

    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus deaneryApprovalStatus;

    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus dioceseApprovalStatus;

    @Enumerated(EnumType.STRING)
    private WorkflowApprovalStatus certificateApprovalStatus;

    private OffsetDateTime certificateRequestedAt;

    private OffsetDateTime certificateApprovedAt;

    private String approvedBy;

    @OneToMany(mappedBy = "participation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseParticipationComponentScore> componentScores = new ArrayList<>();
}
