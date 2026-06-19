package phucuong.ht.modules.training.courseParticipation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}