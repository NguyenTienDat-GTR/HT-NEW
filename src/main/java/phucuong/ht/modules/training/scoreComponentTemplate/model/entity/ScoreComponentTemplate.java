package phucuong.ht.modules.training.scoreComponentTemplate.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.math.BigDecimal;

@Entity
@Table(name = "score_component_template")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"diocese", "deanery"})
@EqualsAndHashCode(callSuper = true)
public class ScoreComponentTemplate extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String code;

    private String name;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;
}
