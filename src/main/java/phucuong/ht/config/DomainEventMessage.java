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
}
