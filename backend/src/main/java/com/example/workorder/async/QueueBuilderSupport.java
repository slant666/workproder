package com.example.workorder.async;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

final class QueueBuilderSupport {

    private QueueBuilderSupport() {
    }

    static Queue durableQueue(String name, String deadLetterExchange, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }
}
