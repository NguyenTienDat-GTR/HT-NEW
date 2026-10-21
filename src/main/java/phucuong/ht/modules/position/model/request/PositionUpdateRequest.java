package phucuong.ht.modules.position.model.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

@Getter
@Setter
public class PositionUpdateRequest {

    private String positionName;

    private UnitType positionType;

    @Min(1)
    private Integer displayOrder;
}
