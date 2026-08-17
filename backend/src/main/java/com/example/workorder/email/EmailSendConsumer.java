package com.example.workorder.email;

import com.example.workorder.async.AsyncRabbitConfig;
import com.example.workorder.async.EmailSendMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.async", name = "rabbit-enabled", havingValue = "true", matchIfMissing = true)
public class EmailSendConsumer {

    private final EmailDeliveryScheduler emailDeliveryScheduler;

    public EmailSendConsumer(EmailDeliveryScheduler emailDeliveryScheduler) {
        this.emailDeliveryScheduler = emailDeliveryScheduler;
    }

    @RabbitListener(queues = AsyncRabbitConfig.EMAIL_SEND_QUEUE)
    public void consume(EmailSendMessage message) {
        if (message == null || message.emailOutboxId() == null) {
            return;
        }
        emailDeliveryScheduler.deliverOneById(message.emailOutboxId());
    }
}
