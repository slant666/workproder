package com.example.workorder.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkOrderStatisticsServiceTests {

    private JdbcTemplate jdbcTemplate;
    private WorkOrderStatisticsService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workorder-statistics;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_status_transitions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
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
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_order_status_transitions (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    work_order_id BIGINT NOT NULL,
                    old_status VARCHAR(20) NOT NULL,
                    new_status VARCHAR(20) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(40) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        insertUser(1L, "admin", "Admin", "ADMIN");
        insertUser(2L, "handler", "Handler", "ADMIN");
        insertUser(3L, "demo", "Demo", "USER");
        service = new WorkOrderStatisticsService(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aggregatesWorkOrderStatisticsByDefinedRules() {
        insertWorkOrder(1L, "Overdue Pending", "\u9ad8", "\u5f85\u5904\u7406", 3L, null, "2026-08-01 00:00:00");
        insertWorkOrder(2L, "Processing", "\u4e2d", "\u5904\u7406\u4e2d", 3L, 1L, "2026-08-02 08:00:00");
        insertWorkOrder(3L, "Completed A", "\u9ad8", "\u5df2\u5b8c\u6210", 3L, 1L, "2026-08-02 09:00:00");
        insertWorkOrder(4L, "Completed B", "\u4f4e", "\u5df2\u5b8c\u6210", 3L, 2L, "2026-08-03 10:00:00");
        insertTransition(3L, "\u5f85\u5904\u7406", "\u5904\u7406\u4e2d", 1L, "accept", "2026-08-02 10:00:00");
        insertTransition(3L, "\u5f85\u786e\u8ba4", "\u5df2\u5b8c\u6210", 3L, "confirm", "2026-08-02 12:00:00");
        insertTransition(4L, "\u5f85\u5904\u7406", "\u5904\u7406\u4e2d", 2L, "accept", "2026-08-03 11:00:00");
        insertTransition(4L, "\u5f85\u786e\u8ba4", "\u5df2\u5b8c\u6210", 3L, "confirm", "2026-08-03 14:00:00");

        WorkOrderStatisticsResponse response = service.dashboard(new WorkOrderStatisticsQuery(null, null));

        assertThat(response.total()).isEqualTo(4);
        assertThat(response.statusCounts()).containsExactly(
                new WorkOrderCountResponse("\u5f85\u5904\u7406", 1),
                new WorkOrderCountResponse("\u5904\u7406\u4e2d", 1),
                new WorkOrderCountResponse("\u5f85\u786e\u8ba4", 0),
                new WorkOrderCountResponse("\u5df2\u5b8c\u6210", 2),
                new WorkOrderCountResponse("\u5df2\u53d6\u6d88", 0));
        assertThat(response.priorityCounts()).containsExactly(
                new WorkOrderCountResponse("\u4f4e", 1),
                new WorkOrderCountResponse("\u4e2d", 1),
                new WorkOrderCountResponse("\u9ad8", 2));
        assertThat(response.dailyNewCounts()).extracting(DailyWorkOrderCountResponse::count).containsExactly(1L, 2L, 1L);
        assertThat(response.averageProcessingMinutes()).isEqualTo(150);
        assertThat(response.adminProcessingCounts()).extracting(AdminWorkOrderCountResponse::handlerUsername)
                .containsExactly("admin", "handler");
        assertThat(response.overdueUnhandledCount()).isEqualTo(1);
        assertThat(response.averageProcessingRule()).contains("\u9996\u6b21\u63a5\u5355");
        assertThat(response.overdueRule()).contains("48");
    }

    @Test
    void filtersStatisticsByCreatedDateRange() {
        insertWorkOrder(1L, "Old", "\u9ad8", "\u5f85\u5904\u7406", 3L, null, "2026-08-01 00:00:00");
        insertWorkOrder(2L, "Processing", "\u4e2d", "\u5904\u7406\u4e2d", 3L, 1L, "2026-08-02 08:00:00");
        insertWorkOrder(3L, "Completed", "\u9ad8", "\u5df2\u5b8c\u6210", 3L, 1L, "2026-08-02 09:00:00");
        insertTransition(3L, "\u5f85\u5904\u7406", "\u5904\u7406\u4e2d", 1L, "accept", "2026-08-02 10:00:00");
        insertTransition(3L, "\u5f85\u786e\u8ba4", "\u5df2\u5b8c\u6210", 3L, "confirm", "2026-08-02 12:00:00");

        WorkOrderStatisticsResponse response = service.dashboard(new WorkOrderStatisticsQuery("2026-08-02", "2026-08-02"));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.dailyNewCounts()).extracting(DailyWorkOrderCountResponse::count).containsExactly(2L);
        assertThat(response.averageProcessingMinutes()).isEqualTo(120);
        assertThat(response.overdueUnhandledCount()).isZero();
    }

    @Test
    void returnsZeroAndEmptySeriesWhenNoDataExists() {
        WorkOrderStatisticsResponse response = service.dashboard(new WorkOrderStatisticsQuery(null, null));

        assertThat(response.total()).isZero();
        assertThat(response.statusCounts()).extracting(WorkOrderCountResponse::count).containsOnly(0L);
        assertThat(response.priorityCounts()).extracting(WorkOrderCountResponse::count).containsOnly(0L);
        assertThat(response.dailyNewCounts()).isEmpty();
        assertThat(response.averageProcessingMinutes()).isZero();
        assertThat(response.adminProcessingCounts()).isEmpty();
        assertThat(response.overdueUnhandledCount()).isZero();
    }

    @Test
    void rejectsInvalidDateRange() {
        assertThatThrownBy(() -> service.dashboard(new WorkOrderStatisticsQuery("bad", null)))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> service.dashboard(new WorkOrderStatisticsQuery("2026-08-03", "2026-08-02")))
                .isInstanceOf(WorkOrderException.class);
    }

    private void insertUser(Long id, String username, String nickname, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, ?, TRUE)",
                id,
                username,
                nickname,
                "hash",
                role);
    }

    private void insertWorkOrder(Long id, String title, String priority, String status, Long creatorId, Long handlerId, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO work_orders (id, title, description, type, priority, status, creator_id, handler_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                title,
                "description",
                "type",
                priority,
                status,
                creatorId,
                handlerId,
                createdAt);
    }

    private void insertTransition(Long workOrderId, String oldStatus, String newStatus, Long actorId, String action, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO work_order_status_transitions (work_order_id, old_status, new_status, actor_id, action, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                workOrderId,
                oldStatus,
                newStatus,
                actorId,
                action,
                createdAt);
    }
}