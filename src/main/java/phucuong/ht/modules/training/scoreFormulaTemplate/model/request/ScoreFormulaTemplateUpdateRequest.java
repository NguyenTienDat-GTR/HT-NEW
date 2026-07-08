package phucuong.ht.modules.training.scoreFormulaTemplate.model.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ScoreFormulaTemplateUpdateRequest {

    private String code;

    private String name;

    private String expression;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    private String description;

    private List<String> componentIds;
}
