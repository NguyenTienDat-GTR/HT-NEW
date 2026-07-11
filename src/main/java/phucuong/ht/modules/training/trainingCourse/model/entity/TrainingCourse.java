package phucuong.ht.modules.training.trainingCourse.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "training_course")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class TrainingCourse extends BaseModel {

    @Id
    @Column(name = "course_code")
    private String courseCode;

    @ManyToOne
    @JoinColumn(name = "requirement_code")
    private CourseRequirement requirement;

    private String courseName;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    @Enumerated(EnumType.STRING)
    private CourseType courseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_formula_id", nullable = false)
    private ScoreFormulaTemplate scoreFormula;

    private String additionalNotes;
}
