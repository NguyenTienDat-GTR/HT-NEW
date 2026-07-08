package phucuong.ht.modules.training.scoreFormulaTemplate.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "score_formula_template")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"diocese", "deanery", "components"})
@EqualsAndHashCode(callSuper = true)
public class ScoreFormulaTemplate extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String code;

    private String name;

    private String expression;

    @Column(name = "passing_score")
    private BigDecimal passingScore;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;

    @OneToMany(mappedBy = "formula", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<ScoreFormulaComponent> components = new ArrayList<>();
}
