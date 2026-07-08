package phucuong.ht.modules.executiveBoard.model.request;

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

    @NotNull
    private UUID unitId;

    @NotNull
    private UUID leaderId;

    @jakarta.validation.constraints.NotBlank
    private String positionCode;

    private LocalDate startDate;

    private LocalDate endDate;
}
