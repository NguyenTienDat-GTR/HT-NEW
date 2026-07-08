package phucuong.ht.modules.executiveBoard.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ExecutiveBoardAssignmentUpdateRequest {

    private UnitType unitType;

    private String unitId;

    private UUID leaderId;

    private String positionCode;

    private LocalDate startDate;

    private LocalDate endDate;
}
