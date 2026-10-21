package phucuong.ht.modules.position.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

@Getter
@Setter
public class PositionCreateRequest {

    @NotBlank
    private String positionName;

    @NotNull
    private UnitType positionType;

    // Thứ tự hiển thị (từ 1); bỏ trống thì xếp cuối cùng của cấp.
    @Min(1)
    private Integer displayOrder;
}
