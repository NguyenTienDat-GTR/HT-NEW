package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainEventMessageRoundTripTest {

    @Test
    void messageSurvivesJsonRoundTripThroughAmqpConverter() {
        var converter = new rabbitMQConfig().jackson2JsonMessageConverter();
        DomainEventMessage original = DomainEventMessage.builder()
                .eventType("training.course.registration_closed")
                .entityId("C1")
                .actor("admin")
                .occurredAt(OffsetDateTime.now())
                .payload(Map.of("courseCode", "C1", "open", false))
                .build();
        Message amqp = converter.toMessage(original, new MessageProperties());
        Object back = converter.fromMessage(amqp);
        assertEquals("training.course.registration_closed", ((DomainEventMessage) back).getEventType());
        assertEquals("C1", ((DomainEventMessage) back).getPayload().get("courseCode"));
    }
}
