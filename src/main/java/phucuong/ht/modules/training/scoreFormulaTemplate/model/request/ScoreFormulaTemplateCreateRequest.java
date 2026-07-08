package phucuong.ht.modules.training.scoreFormulaTemplate.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ScoreFormulaTemplateCreateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String expression;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal passingScore;

    private String description;

    @NotNull
    private UnitType unitType;

    @NotEmpty
    private List<String> componentIds;
}
