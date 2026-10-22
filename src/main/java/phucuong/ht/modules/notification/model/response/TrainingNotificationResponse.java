package phucuong.ht.modules.notification.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class TrainingNotificationResponse {
    private String id;
    private String eventType;
    private String title;
    private String message;
    private UnitType senderUnitType;
    private UUID senderUnitId;
    private String courseCode;
    private Integer totalCount;
    private Integer approvedCount;
    private Integer pendingCount;
    private String payload;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
    private Boolean status;
}
