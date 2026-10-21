package phucuong.ht.modules.executiveBoard.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ExecutiveBoardAssignmentResponse {
    private UUID id;
    private UnitType unitType;
    private UUID unitId;
    private String unitName;
    private UUID dioceseId;
    private UUID deaneryId;
    private UUID parishId;
    private UUID leaderId;
    private String leaderHolyName;
    private String leaderFullName;
    private UUID leaderParishId;
    private String positionCode;
    private String positionName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String updatedByName;
}
