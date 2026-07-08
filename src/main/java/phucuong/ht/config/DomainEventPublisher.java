package phucuong.ht.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishAfterCommit(DomainEventMessage message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(rabbitMQConfig.DOMAIN_EXCHANGE, rabbitMQConfig.DOMAIN_ROUTING_KEY, message);
                }
            });
            return;
        }

        rabbitTemplate.convertAndSend(rabbitMQConfig.DOMAIN_EXCHANGE, rabbitMQConfig.DOMAIN_ROUTING_KEY, message);
    }
}
