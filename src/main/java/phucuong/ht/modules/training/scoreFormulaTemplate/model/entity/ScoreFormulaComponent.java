package phucuong.ht.modules.training.scoreFormulaTemplate.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;

@Entity
@Table(name = "score_formula_component")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"formula", "component"})
@EqualsAndHashCode(callSuper = true)
public class ScoreFormulaComponent extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formula_id")
    private ScoreFormulaTemplate formula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private ScoreComponentTemplate component;

    @Column(name = "display_order")
    private Integer displayOrder;
}
