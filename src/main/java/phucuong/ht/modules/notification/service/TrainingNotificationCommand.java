package phucuong.ht.modules.notification.service;

import lombok.Builder;
import phucuong.ht.common.enums.UnitType;

import java.util.UUID;

@Builder
public record TrainingNotificationCommand(
        String eventType,
        String title,
        String message,
        UnitType senderUnitType,
        UUID senderUnitId,
        String courseCode,
        Integer totalCount,
        Integer approvedCount,
        Integer pendingCount,
        String payload,
        String actor
) {
}
