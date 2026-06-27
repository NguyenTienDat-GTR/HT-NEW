package phucuong.ht.modules.position.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

@Getter
@Setter
public class PositionCreateRequest {

    @NotBlank
    private String positionName;

    private UnitType positionType;
}
