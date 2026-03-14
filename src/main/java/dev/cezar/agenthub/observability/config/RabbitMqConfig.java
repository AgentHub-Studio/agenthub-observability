package dev.cezar.agenthub.observability.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for consuming orchestrator events.
 * 
 * <p>This configuration is enabled only when rabbitmq.enabled=true.
 * It sets up exchanges, queues, and bindings to consume events from the orchestrator.
 * 
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "agenthub.observability.rabbitmq.enabled", havingValue = "true")
public class RabbitMqConfig {

    @Value("${agenthub.observability.rabbitmq.exchange:agenthub.orchestrator.events}")
    private String exchangeName;

    @Value("${agenthub.observability.rabbitmq.queue:agenthub.observability.events}")
    private String queueName;

    /**
     * Declare the topic exchange for orchestrator events.
     */
    @Bean
    public TopicExchange orchestratorEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Declare the queue for observability service.
     */
    @Bean
    public Queue observabilityEventsQueue() {
        return QueueBuilder
                .durable(queueName)
                .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
                .build();
    }

    /**
     * Bind queue to exchange for all execution.* events.
     */
    @Bean
    public Binding executionEventsBinding(Queue observabilityEventsQueue, TopicExchange orchestratorEventsExchange) {
        return BindingBuilder
                .bind(observabilityEventsQueue)
                .to(orchestratorEventsExchange)
                .with("execution.*");
    }

    /**
     * Bind queue to exchange for all node.* events.
     */
    @Bean
    public Binding nodeEventsBinding(Queue observabilityEventsQueue, TopicExchange orchestratorEventsExchange) {
        return BindingBuilder
                .bind(observabilityEventsQueue)
                .to(orchestratorEventsExchange)
                .with("node.*");
    }

    /**
     * JSON message converter for RabbitMQ.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure RabbitTemplate with JSON converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    /**
     * Configure listener container factory with JSON converter.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        return factory;
    }
}
