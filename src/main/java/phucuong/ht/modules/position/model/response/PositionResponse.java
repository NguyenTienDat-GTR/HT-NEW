package phucuong.ht.modules.position.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.OffsetDateTime;

@Getter
@Builder
public class PositionResponse {
    private String positionCode;
    private java.util.UUID dioceseId;
    private java.util.UUID unitId;
    private String positionName;
    private UnitType positionType;
    private Integer displayOrder;
    private Boolean status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
