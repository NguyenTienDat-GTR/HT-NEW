package phucuong.ht.modules.training.courseParticipation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent;

import java.math.BigDecimal;

@Entity
@Table(name = "course_participation_component_score")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"participation", "component"})
@EqualsAndHashCode(callSuper = true)
public class CourseParticipationComponentScore extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participation_id")
    private CourseParticipation participation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private TrainingCourseScoreComponent component;

    private BigDecimal score;

    private String note;
}
