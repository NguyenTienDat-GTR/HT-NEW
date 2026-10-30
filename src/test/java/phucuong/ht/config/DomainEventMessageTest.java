package phucuong.ht.config;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventMessageTest {

    @Test
    void ofFillsEveryFieldAndStampsTheCurrentTime() {
        OffsetDateTime before = OffsetDateTime.now();

        DomainEventMessage message = DomainEventMessage.of("training.course.finished", "trainingCourse", "K1", "admin", Map.of("reason", "x"));

        assertEquals("training.course.finished", message.getEventType());
        assertEquals("trainingCourse", message.getEntityType());
        assertEquals("K1", message.getEntityId());
        assertEquals("admin", message.getActor());
        assertEquals(Map.of("reason", "x"), message.getPayload());
        assertFalse(message.getOccurredAt().isBefore(before));
        assertTrue(message.getOccurredAt().isBefore(OffsetDateTime.now().plusSeconds(1)));
    }
}
