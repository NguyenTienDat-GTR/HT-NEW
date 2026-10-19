package phucuong.ht.modules.training.scoreComponentTemplate.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ScoreComponentTemplateResponse {
    private String id;
    private String code;
    private String name;
    private BigDecimal maxScore;
    private String description;
    private String courseTypeCode;
    private String courseTypeName;
    private BigDecimal coefficient;
    private Boolean includeInFinal;
    private Integer displayOrder;
    private UnitType unitType;
    private UUID dioceseId;
    private UUID deaneryId;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
