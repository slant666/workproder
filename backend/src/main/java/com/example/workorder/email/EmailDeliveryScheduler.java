package com.example.workorder.email;

import com.example.workorder.async.AsyncTaskPublisher;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final AsyncTaskPublisher asyncTaskPublisher;
    private Boolean outboxAvailable;

    public EmailDeliveryScheduler(JdbcTemplate jdbcTemplate, EmailSender emailSender, EmailProperties properties) {
        this(jdbcTemplate, emailSender, properties, null);
    }

    @Autowired
    public EmailDeliveryScheduler(
            JdbcTemplate jdbcTemplate,
            EmailSender emailSender,
            EmailProperties properties,
            @Nullable AsyncTaskPublisher asyncTaskPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailSender = emailSender;
        this.properties = properties;
        this.asyncTaskPublisher = asyncTaskPublisher;
    }

    @Scheduled(fixedDelayString = "${work-order.email.scan-interval-ms:60000}")
    public void deliverPendingEmails() {
        if (!isOutboxAvailable()) {
            return;
        }
        List<PendingEmail> emails = jdbcTemplate.query(
                """
                SELECT id, to_email, subject, body, attempt_count
                FROM email_outbox
                WHERE status IN ('PENDING', 'FAILED')
                  AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new PendingEmail(
                        rs.getLong("id"),
                        rs.getString("to_email"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getInt("attempt_count")),
                properties.getBatchSize());
        for (PendingEmail email : emails) {
            if (asyncTaskPublisher == null || !asyncTaskPublisher.publishEmailSend(email.id())) {
                deliverOne(email);
            }
        }
    }

    @Transactional
    public void deliverOneById(Long id) {
        try {
            PendingEmail email = jdbcTemplate.queryForObject(
                    """
                    SELECT id, to_email, subject, body, attempt_count
                    FROM email_outbox
                    WHERE id = ? AND status IN ('PENDING', 'FAILED')
                    """,
                    (rs, rowNum) -> new PendingEmail(
                            rs.getLong("id"),
                            rs.getString("to_email"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getInt("attempt_count")),
                    id);
            deliverOne(email);
        } catch (EmptyResultDataAccessException ignored) {
        }
    }

    @Transactional
    void deliverOne(PendingEmail email) {
        int claimed = jdbcTemplate.update(
                """
                UPDATE email_outbox
                SET status = 'SENDING'
                WHERE id = ? AND status IN ('PENDING', 'FAILED')
                """,
                email.id());
        if (claimed != 1) {
            return;
        }
        try {
            emailSender.send(email.toEmail(), email.subject(), email.body());
            jdbcTemplate.update(
                    "UPDATE email_outbox SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, last_error = NULL WHERE id = ?",
                    email.id());
        } catch (RuntimeException ex) {
            int nextAttemptCount = email.attemptCount() + 1;
            boolean dead = nextAttemptCount >= properties.getMaxAttempts();
            jdbcTemplate.update(
                    """
                    UPDATE email_outbox
                    SET status = ?, attempt_count = ?, next_attempt_at = ?, last_error = ?
                    WHERE id = ?
                    """,
                    dead ? "DEAD" : "FAILED",
                    nextAttemptCount,
                    Timestamp.from(Instant.now().plus(backoff(nextAttemptCount))),
                    truncate(ex.getMessage()),
                    email.id());
        }
    }

    private Duration backoff(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            case 4 -> Duration.ofHours(1);
            default -> Duration.ofHours(6);
        };
    }

    private boolean isOutboxAvailable() {
        if (outboxAvailable != null) {
            return outboxAvailable;
        }
        try {
            jdbcTemplate.queryForList("SELECT id FROM email_outbox WHERE 1 = 0");
            outboxAvailable = true;
        } catch (RuntimeException ex) {
            outboxAvailable = false;
        }
        return outboxAvailable;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    record PendingEmail(Long id, String toEmail, String subject, String body, int attemptCount) {
    }
}
