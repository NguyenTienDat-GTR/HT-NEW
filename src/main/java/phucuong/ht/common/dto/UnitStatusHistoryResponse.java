package phucuong.ht.common.dto;

import java.time.OffsetDateTime;

public record UnitStatusHistoryResponse(
        String id,
        String unitType,
        String unitId,
        boolean status,
        String eventType,
        String reason,
        String changedBy,
        OffsetDateTime changedAt,
        int affectedAccountCount,
        int affectedChildUnitCount
) {
}
