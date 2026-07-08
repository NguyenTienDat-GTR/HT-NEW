package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ComponentScoreResponse {
    private String id;
    private String componentId;
    private String componentCode;
    private String componentName;
    private BigDecimal maxScore;
    private BigDecimal score;
    private String note;
    private Integer displayOrder;
}
