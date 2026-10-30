package phucuong.ht.config;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
public class DomainEventMessage {

    private String eventType;
    private String entityType;
    private String entityId;
    private String actor;
    private OffsetDateTime occurredAt;
    private Map<String, Object> payload;

    /** Sự kiện xảy ra "bây giờ"; cách dựng chung thay cho các builder lặp ở từng service. */
    public static DomainEventMessage of(String eventType, String entityType, String entityId, String actor, Map<String, Object> payload) {
        return DomainEventMessage.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .occurredAt(OffsetDateTime.now())
                .payload(payload)
                .build();
    }
}
