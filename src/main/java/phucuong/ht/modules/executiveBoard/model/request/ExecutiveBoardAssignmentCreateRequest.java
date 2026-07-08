package phucuong.ht.modules.executiveBoard.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ExecutiveBoardAssignmentCreateRequest {

    @NotNull
    private UnitType unitType;

    @NotBlank
    private String unitId;

    @NotBlank
    private UUID leaderId;

    @NotBlank
    private String positionCode;

    private LocalDate startDate;

    private LocalDate endDate;
}
