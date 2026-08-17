package com.example.workorder.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkOrderListServiceTests {

    private static final String PENDING = "\u5f85\u5904\u7406";
    private static final String CANCELLED = "\u5df2\u53d6\u6d88";
    private static final String LOW = "\u4f4e";
    private static final String MEDIUM = "\u4e2d";
    private static final String HIGH = "\u9ad8";

    private JdbcTemplate jdbcTemplate;
    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:workorder-list;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS department_admins");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("DROP TABLE IF EXISTS teams");
        jdbcTemplate.execute("DROP TABLE IF EXISTS departments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS companies");
        jdbcTemplate.execute("""
                CREATE TABLE companies (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(80) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE departments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    company_id BIGINT NOT NULL,
                    name VARCHAR(80) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE teams (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    department_id BIGINT NOT NULL,
                    name VARCHAR(80) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    company_id BIGINT NULL,
                    department_id BIGINT NULL,
                    team_id BIGINT NULL,
                    org_confirmed BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE department_admins (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    department_id BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_orders (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(120) NOT NULL,
                    description TEXT NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    priority VARCHAR(10) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    creator_id BIGINT NOT NULL,
                    handler_id BIGINT NULL,
                    company_id BIGINT NULL,
                    department_id BIGINT NULL,
                    team_id BIGINT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("INSERT INTO companies (id, name, enabled) VALUES (1, 'Acme', TRUE)");
        jdbcTemplate.update("INSERT INTO departments (id, company_id, name, enabled) VALUES (1, 1, 'Finance', TRUE), (2, 1, 'HR', TRUE)");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled, company_id, department_id, org_confirmed) VALUES (?, ?, ?, ?, TRUE, ?, ?, TRUE)",
                "demo", "Demo", "hash", "USER", 1L, 1L);
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled, company_id, department_id, org_confirmed) VALUES (?, ?, ?, ?, TRUE, ?, ?, TRUE)",
                "other", "Other", "hash", "USER", 1L, 2L);
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled, company_id, department_id, org_confirmed) VALUES (?, ?, ?, ?, TRUE, ?, ?, TRUE)",
                "admin", "Admin", "hash", "ADMIN", 1L, 1L);
        workOrderService = new WorkOrderService(jdbcTemplate);
    }

    @Test
    void regularUserOnlyReceivesOwnPagedWorkOrders() {
        insertWorkOrder("Own A", MEDIUM, PENDING, 1L, "2026-08-07 08:00:00");
        insertWorkOrder("Own B", MEDIUM, PENDING, 1L, "2026-08-07 09:00:00");
        insertWorkOrder("Other A", MEDIUM, PENDING, 2L, "2026-08-07 10:00:00");

        PagedWorkOrderResponse response = workOrderService.listVisible(
                new WorkOrderListQuery(null, null, null, "createdAtDesc", 1, 10),
                user());

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items()).extracting(WorkOrderResponse::title).containsExactly("Own B", "Own A");
    }

    @Test
    void filtersByKeywordStatusAndPriority() {
        insertWorkOrder("Need Printer Toner", HIGH, PENDING, 1L, "2026-08-07 08:00:00");
        insertWorkOrder("Need Printer Cable", LOW, PENDING, 1L, "2026-08-07 09:00:00");
        insertWorkOrder("Need Printer Paper", HIGH, CANCELLED, 1L, "2026-08-07 10:00:00");

        PagedWorkOrderResponse response = workOrderService.listVisible(
                new WorkOrderListQuery("printer", PENDING, HIGH, "createdAtDesc", 1, 10),
                user());

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(WorkOrderResponse::title).containsExactly("Need Printer Toner");
    }

    @Test
    void supportsCreatedAtSortingAndBackendPagination() {
        insertWorkOrder("Sort Old", MEDIUM, PENDING, 1L, "2026-08-07 06:00:00");
        insertWorkOrder("Sort Middle", MEDIUM, PENDING, 1L, "2026-08-07 09:00:00");
        insertWorkOrder("Sort New", MEDIUM, PENDING, 1L, "2026-08-07 12:00:00");

        PagedWorkOrderResponse response = workOrderService.listVisible(
                new WorkOrderListQuery("Sort", null, null, "createdAtAsc", 2, 1),
                admin());

        assertThat(response.total()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items()).extracting(WorkOrderResponse::title).containsExactly("Sort Middle");
    }

    @Test
    void adminFiltersAllWorkOrdersByCreatorHandlerAndCreatedRange() {
        insertWorkOrder("Admin Match", HIGH, PENDING, 2L, 3L, "2026-08-07 10:00:00");
        insertWorkOrder("Wrong Creator", HIGH, PENDING, 1L, 3L, "2026-08-07 10:30:00");
        insertWorkOrder("Wrong Handler", HIGH, PENDING, 2L, null, "2026-08-07 11:00:00");
        insertWorkOrder("Out Of Range", HIGH, PENDING, 2L, 3L, "2026-08-09 10:00:00");

        PagedWorkOrderResponse response = workOrderService.listAllForAdmin(
                new WorkOrderListQuery("Admin", PENDING, HIGH, 2L, 3L, "2026-08-07", "2026-08-08", "createdAtDesc", 1, 10));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(WorkOrderResponse::title).containsExactly("Admin Match");
        assertThat(response.items().getFirst().handlerId()).isEqualTo(3L);
        assertThat(response.items().getFirst().handlerUsername()).isEqualTo("admin");
    }

    @Test
    void returnsEmptyItemsWhenPageExceedsResultRange() {
        insertWorkOrder("Only One", MEDIUM, PENDING, 1L, "2026-08-07 08:00:00");

        PagedWorkOrderResponse response = workOrderService.listVisible(
                new WorkOrderListQuery(null, null, null, "createdAtDesc", 3, 10),
                user());

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void rejectsInvalidQueryParameters() {
        assertThatThrownBy(() -> workOrderService.listVisible(new WorkOrderListQuery(null, "bad", null, null, 1, 10), user()))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listVisible(new WorkOrderListQuery(null, null, "urgent", null, 1, 10), user()))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listVisible(new WorkOrderListQuery(null, null, null, "idDesc", 1, 10), user()))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listVisible(new WorkOrderListQuery(null, null, null, null, 0, 10), user()))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listVisible(new WorkOrderListQuery(null, null, null, null, 1, 51), user()))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listAllForAdmin(new WorkOrderListQuery(null, null, null, 0L, null, null, null, null, 1, 10)))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listAllForAdmin(new WorkOrderListQuery(null, null, null, null, 0L, null, null, null, 1, 10)))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listAllForAdmin(new WorkOrderListQuery(null, null, null, null, null, "bad-date", null, null, 1, 10)))
                .isInstanceOf(WorkOrderException.class);
        assertThatThrownBy(() -> workOrderService.listAllForAdmin(new WorkOrderListQuery(null, null, null, null, null, "2026-08-09", "2026-08-08", null, 1, 10)))
                .isInstanceOf(WorkOrderException.class);
    }

    private void insertWorkOrder(String title, String priority, String status, Long creatorId, String createdAt) {
        insertWorkOrder(title, priority, status, creatorId, null, createdAt);
    }

    private void insertWorkOrder(String title, String priority, String status, Long creatorId, Long handlerId, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO work_orders (title, description, type, priority, status, creator_id, handler_id, company_id, department_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                title,
                "description",
                "type",
                priority,
                status,
                creatorId,
                handlerId,
                1L,
                creatorId.equals(2L) ? 2L : 1L,
                createdAt);
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "demo", "Demo", "USER", 1L, "Acme", 1L, "Finance", null, null, true);
    }

    private CurrentUser admin() {
        return new CurrentUser(3L, "admin", "Admin", "ADMIN", 1L, "Acme", 1L, "Finance", null, null, true);
    }
}
