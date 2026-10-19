package phucuong.ht.modules.training.scoreComponentTemplate.model.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ScoreComponentTemplateUpdateRequest {

    private String code;

    private String name;

    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal maxScore;

    private String description;

    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal coefficient;

    private Boolean includeInFinal;

    private Integer displayOrder;
}
