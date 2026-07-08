package phucuong.ht.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DomainEventAuditConsumer {

    @RabbitListener(queues = rabbitMQConfig.DOMAIN_AUDIT_QUEUE)
    public void handle(DomainEventMessage message) {
        log.info("Received domain event type={} entityType={} entityId={} actor={}",
                message.getEventType(),
                message.getEntityType(),
                message.getEntityId(),
                message.getActor());
    }
}
