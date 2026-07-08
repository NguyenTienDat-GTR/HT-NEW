package phucuong.ht.modules.training.courseParticipation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;

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
    private ScoreComponentTemplate component;

    private BigDecimal score;

    private String note;
}
