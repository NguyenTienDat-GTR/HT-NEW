package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** RabbitMQ lỗi không được làm hỏng request đã lưu dữ liệu. */
class DomainEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final DomainEventPublisher publisher = new DomainEventPublisher(rabbitTemplate);
    private final DomainEventMessage message = DomainEventMessage.of("training.registration.submitted", "courseParticipation", "p1", "parish", Map.of());

    @Test
    void sendFailureOutsideTransactionIsSwallowed() {
        doThrow(new AmqpIOException(new IOException("vhost test not found"))).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertDoesNotThrow(() -> publisher.publishAfterCommit(message));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void sendFailureAfterCommitIsSwallowed() {
        doThrow(new AmqpIOException(new IOException("vhost test not found"))).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publisher.publishAfterCommit(message);
            assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
