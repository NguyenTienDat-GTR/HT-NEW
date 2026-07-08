package phucuong.ht.modules.training.scoreFormulaTemplate.model.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ScoreFormulaComponentResponse {
    private String id;
    private String componentId;
    private String componentCode;
    private String componentName;
    private BigDecimal maxScore;
    private Integer displayOrder;
}
