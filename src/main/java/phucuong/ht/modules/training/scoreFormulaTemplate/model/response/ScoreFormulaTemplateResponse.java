package phucuong.ht.modules.training.scoreFormulaTemplate.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ScoreFormulaTemplateResponse {
    private String id;
    private String code;
    private String name;
    private String expression;
    private BigDecimal passingScore;
    private String description;
    private UnitType unitType;
    private UUID dioceseId;
    private UUID deaneryId;
    private Boolean status;
    private List<ScoreFormulaComponentResponse> components;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
