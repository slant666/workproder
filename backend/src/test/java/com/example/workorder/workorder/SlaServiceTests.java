package com.example.workorder.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.workorder.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SlaServiceTests {

    private JdbcTemplate jdbcTemplate;
    private SlaService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sla-service;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS notifications");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_sla_events");
        jdbcTemplate.execute("DROP TABLE IF EXISTS department_admins");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(30) NOT NULL,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE department_admins (
                    user_id BIGINT NOT NULL,
                    department_id BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_orders (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(120) NOT NULL,
                    description TEXT NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    priority VARCHAR(10) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    creator_id BIGINT NOT NULL,
                    handler_id BIGINT NULL,
                    department_id BIGINT NULL,
                    first_response_due_at TIMESTAMP NULL,
                    resolution_due_at TIMESTAMP NULL,
                    first_responded_at TIMESTAMP NULL,
                    resolved_at TIMESTAMP NULL,
                    sla_status VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_order_sla_events (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    work_order_id BIGINT NOT NULL,
                    event_type VARCHAR(40) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (work_order_id, event_type)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE notifications (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    recipient_id BIGINT NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    work_order_id BIGINT NULL,
                    read_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        insertUser(1L, "admin", "Admin", "ADMIN");
        insertUser(2L, "dept-admin", "Dept Admin", "USER");
        insertUser(3L, "handler", "Handler", "CUSTOMER_SERVICE");
        jdbcTemplate.update("INSERT INTO department_admins (user_id, department_id) VALUES (?, ?)", 2L, 10L);
        service = new SlaService(
                jdbcTemplate,
                new NotificationService(jdbcTemplate),
                Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void scanOpenWorkOrdersMarksOverdueAndNotifiesRecipientsOnce() {
        insertWorkOrder(100L, "Overdue", "\u9ad8", "\u5f85\u5904\u7406", "2026-08-11 16:59:00", "2026-08-11 22:00:00");

        service.scanOpenWorkOrders();
        service.scanOpenWorkOrders();

        assertThat(jdbcTemplate.queryForObject("SELECT sla_status FROM work_orders WHERE id = 100", String.class))
                .isEqualTo("FIRST_RESPONSE_OVERDUE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_sla_events", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList("SELECT recipient_id FROM notifications ORDER BY recipient_id", Long.class))
                .containsExactly(1L, 2L, 3L);
        assertThat(jdbcTemplate.queryForObject("SELECT DISTINCT type FROM notifications", String.class))
                .isEqualTo("SLA_OVERDUE");
    }

    @Test
    void scanOpenWorkOrdersMarksNearOverdueWithNearNotificationType() {
        insertWorkOrder(101L, "Near", "\u9ad8", "\u5f85\u5904\u7406", "2026-08-11 18:20:00", "2026-08-11 22:00:00");

        service.scanOpenWorkOrders();

        assertThat(jdbcTemplate.queryForObject("SELECT sla_status FROM work_orders WHERE id = 101", String.class))
                .isEqualTo("NEAR_OVERDUE");
        assertThat(jdbcTemplate.queryForObject("SELECT DISTINCT type FROM notifications", String.class))
                .isEqualTo("SLA_NEAR_OVERDUE");
    }

    private void insertUser(Long id, String username, String nickname, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, 'hash', ?, TRUE)",
                id,
                username,
                nickname,
                role);
    }

    private void insertWorkOrder(Long id, String title, String priority, String status, String firstResponseDueAt, String resolutionDueAt) {
        jdbcTemplate.update(
                """
                INSERT INTO work_orders (
                    id, title, description, type, priority, status, creator_id, handler_id, department_id,
                    first_response_due_at, resolution_due_at
                ) VALUES (?, ?, 'description', 'type', ?, ?, 4, 3, 10, ?, ?)
                """,
                id,
                title,
                priority,
                status,
                firstResponseDueAt,
                resolutionDueAt);
    }
}
