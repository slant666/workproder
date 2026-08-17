package com.example.workorder.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EmailOutboxServiceTests {

    private JdbcTemplate jdbcTemplate;
    private EmailProperties properties;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:email-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS email_outbox");
        jdbcTemplate.execute("""
                CREATE TABLE email_outbox (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    recipient_user_id BIGINT NULL,
                    to_email VARCHAR(160) NOT NULL,
                    type VARCHAR(80) NOT NULL,
                    subject VARCHAR(200) NOT NULL,
                    body TEXT NOT NULL,
                    related_work_order_id BIGINT NULL,
                    dedupe_key VARCHAR(220) NOT NULL UNIQUE,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    attempt_count INT NOT NULL DEFAULT 0,
                    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_error VARCHAR(500) NULL,
                    sent_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        properties = new EmailProperties();
    }

    @Test
    void enqueueDeduplicatesByDedupeKey() {
        EmailOutboxService service = new EmailOutboxService(jdbcTemplate, properties);

        Long first = service.enqueue(1L, "User@Example.com", "TYPE", "Subject", "Body", 10L, "same-key");
        Long second = service.enqueue(1L, "user@example.com", "TYPE", "Subject", "Body", 10L, "same-key");

        assertThat(second).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_outbox", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT to_email FROM email_outbox WHERE id = ?", String.class, first))
                .isEqualTo("user@example.com");
    }

    @Test
    void deliveryFailureSchedulesRetryWithoutThrowing() {
        properties.setMaxAttempts(5);
        EmailDeliveryScheduler scheduler = new EmailDeliveryScheduler(
                jdbcTemplate,
                (to, subject, body) -> {
                    throw new RuntimeException("smtp timeout");
                },
                properties);
        EmailOutboxService service = new EmailOutboxService(jdbcTemplate, properties);
        Long id = service.enqueue(1L, "user@example.com", "TYPE", "Subject", "Body", 10L, "retry-key");

        scheduler.deliverPendingEmails();

        var row = jdbcTemplate.queryForMap("SELECT status, attempt_count, last_error FROM email_outbox WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("smtp timeout");
    }

    @Test
    void deliverySuccessMarksSent() {
        EmailDeliveryScheduler scheduler = new EmailDeliveryScheduler(jdbcTemplate, (to, subject, body) -> {
        }, properties);
        EmailOutboxService service = new EmailOutboxService(jdbcTemplate, properties);
        Long id = service.enqueue(1L, "user@example.com", "TYPE", "Subject", "Body", 10L, "sent-key");

        scheduler.deliverPendingEmails();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM email_outbox WHERE id = ?", String.class, id))
                .isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT sent_at IS NOT NULL FROM email_outbox WHERE id = ?", Boolean.class, id))
                .isTrue();
    }
}
