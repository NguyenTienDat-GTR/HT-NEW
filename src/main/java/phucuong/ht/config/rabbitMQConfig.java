package phucuong.ht.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class rabbitMQConfig {

    public static final String DOMAIN_EXCHANGE = "ht.domain.events";
    public static final String DOMAIN_ROUTING_KEY = "domain.event";
    public static final String DOMAIN_AUDIT_QUEUE = "ht.domain.events.audit";
    public static final String DOMAIN_AUDIT_DLQ = "ht.domain.events.audit.dlq";

    @Bean
    public DirectExchange domainExchange() {
        return new DirectExchange(DOMAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue domainAuditDlq() {
        return QueueBuilder.durable(DOMAIN_AUDIT_DLQ).build();
    }

    @Bean
    public Queue domainAuditQueue() {
        return QueueBuilder.durable(DOMAIN_AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", DOMAIN_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DOMAIN_AUDIT_DLQ)
                .build();
    }

    @Bean
    public Binding domainAuditBinding() {
        return BindingBuilder.bind(domainAuditQueue()).to(domainExchange()).with(DOMAIN_ROUTING_KEY);
    }

    @Bean
    public Binding domainAuditDlqBinding() {
        return BindingBuilder.bind(domainAuditDlq()).to(domainExchange()).with(DOMAIN_AUDIT_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
