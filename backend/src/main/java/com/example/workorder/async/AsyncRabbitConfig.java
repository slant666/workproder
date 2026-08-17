package com.example.workorder.async;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.async", name = "rabbit-enabled", havingValue = "true", matchIfMissing = true)
public class AsyncRabbitConfig {

    public static final String EXCHANGE = "work-order.async";
    public static final String DLX = "work-order.async.dlx";
    public static final String FILE_EXPORT_QUEUE = "work-order.file.export";
    public static final String FILE_EXPORT_DLQ = "work-order.file.export.dlq";
    public static final String EMAIL_SEND_QUEUE = "work-order.email.send";
    public static final String EMAIL_SEND_DLQ = "work-order.email.send.dlq";
    public static final String FILE_EXPORT_ROUTING_KEY = "file.export";
    public static final String EMAIL_SEND_ROUTING_KEY = "email.send";

    @Bean
    DirectExchange asyncExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange asyncDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue fileExportQueue() {
        return QueueBuilderSupport.durableQueue(FILE_EXPORT_QUEUE, DLX, FILE_EXPORT_ROUTING_KEY);
    }

    @Bean
    Queue fileExportDeadLetterQueue() {
        return new Queue(FILE_EXPORT_DLQ, true);
    }

    @Bean
    Queue emailSendQueue() {
        return QueueBuilderSupport.durableQueue(EMAIL_SEND_QUEUE, DLX, EMAIL_SEND_ROUTING_KEY);
    }

    @Bean
    Queue emailSendDeadLetterQueue() {
        return new Queue(EMAIL_SEND_DLQ, true);
    }

    @Bean
    Binding fileExportBinding(Queue fileExportQueue, DirectExchange asyncExchange) {
        return BindingBuilder.bind(fileExportQueue).to(asyncExchange).with(FILE_EXPORT_ROUTING_KEY);
    }

    @Bean
    Binding fileExportDeadLetterBinding(Queue fileExportDeadLetterQueue, DirectExchange asyncDeadLetterExchange) {
        return BindingBuilder.bind(fileExportDeadLetterQueue).to(asyncDeadLetterExchange).with(FILE_EXPORT_ROUTING_KEY);
    }

    @Bean
    Binding emailSendBinding(Queue emailSendQueue, DirectExchange asyncExchange) {
        return BindingBuilder.bind(emailSendQueue).to(asyncExchange).with(EMAIL_SEND_ROUTING_KEY);
    }

    @Bean
    Binding emailSendDeadLetterBinding(Queue emailSendDeadLetterQueue, DirectExchange asyncDeadLetterExchange) {
        return BindingBuilder.bind(emailSendDeadLetterQueue).to(asyncDeadLetterExchange).with(EMAIL_SEND_ROUTING_KEY);
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        return factory;
    }
}
