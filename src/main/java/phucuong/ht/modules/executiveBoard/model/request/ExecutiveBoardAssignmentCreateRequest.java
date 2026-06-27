package phucuong.ht.modules.executiveBoard.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

import java.time.LocalDate;

@Getter
@Setter
public class ExecutiveBoardAssignmentCreateRequest {

    @NotNull
    private UnitType unitType;

    @NotBlank
    private String unitId;

    @NotBlank
    private String leaderCode;

    @NotBlank
    private String positionCode;

    private LocalDate startDate;

    private LocalDate endDate;
}
