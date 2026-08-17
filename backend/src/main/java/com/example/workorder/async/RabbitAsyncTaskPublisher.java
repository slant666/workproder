package com.example.workorder.async;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitAsyncTaskPublisher implements AsyncTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitAsyncTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public boolean publishFileExport(Long jobId) {
        return publish(AsyncRabbitConfig.FILE_EXPORT_ROUTING_KEY, new FileExportMessage(jobId));
    }

    @Override
    public boolean publishEmailSend(Long emailOutboxId) {
        return publish(AsyncRabbitConfig.EMAIL_SEND_ROUTING_KEY, new EmailSendMessage(emailOutboxId));
    }

    private boolean publish(String routingKey, Object message) {
        try {
            rabbitTemplate.convertAndSend(AsyncRabbitConfig.EXCHANGE, routingKey, message);
            return true;
        } catch (AmqpException ex) {
            return false;
        }
    }
}
