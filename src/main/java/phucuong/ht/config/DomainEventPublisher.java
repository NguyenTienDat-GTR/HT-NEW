package phucuong.ht.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishAfterCommit(DomainEventMessage message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message);
                }
            });
            return;
        }

        send(message);
    }

    // Sự kiện chỉ là tác vụ phụ (nhật ký, thông báo): dữ liệu đã lưu thì RabbitMQ lỗi không được làm request báo lỗi,
    // nếu không người dùng sẽ gửi lại và tạo trùng.
    private void send(DomainEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(rabbitMQConfig.DOMAIN_EXCHANGE, rabbitMQConfig.DOMAIN_ROUTING_KEY, message);
        } catch (AmqpException e) {
            log.error("Không gửi được sự kiện {} ({} {}) sang RabbitMQ, dữ liệu đã được lưu: {}",
                    message.getEventType(), message.getEntityType(), message.getEntityId(), e.getMessage());
        }
    }
}
