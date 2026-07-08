package phucuong.ht.modules.training.courseRequirement.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.enums.CourseType;

@Entity
@Table(name = "course_requirement")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class CourseRequirement extends BaseModel {

    @Id
    @Column(name = "requirement_code")
    private String requirementCode;

    private String requirementName;

    @Enumerated(EnumType.STRING)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    private LeaderLevel requiredCurrentLevel;

    private Integer requiredMinAge;

    private Integer requiredYearsActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;
}
