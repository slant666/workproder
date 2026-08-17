package com.example.workorder.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "delivery-enabled", havingValue = "false", matchIfMissing = true)
public class NoopEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email delivery disabled; would send to {} with subject {}", to, subject);
    }
}
