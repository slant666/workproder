package com.example.workorder.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkOrderServiceTests {

    private JdbcTemplate jdbcTemplate;
    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:workorders;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_orders (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(120) NOT NULL,
                    description TEXT NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    priority VARCHAR(10) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT '待处理',
                    creator_id BIGINT NOT NULL,
                    handler_id BIGINT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "demo", "演示用户", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "other", "其他用户", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "admin", "管理员", "hash", "ADMIN");
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "自己的工单", "自己的描述", "设备维修", "中", "待处理", 1L);
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "别人的工单", "敏感描述", "账号问题", "高", "待处理", 2L);
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "已取消工单", "不能再修改", "设备维修", "低", "已取消", 1L);
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "已完成工单", "不能再修改", "设备维修", "中", "已完成", 1L);
        workOrderService = new WorkOrderService(jdbcTemplate);
    }

    @Test
    void createsWorkOrderWithInitialStatusAndCurrentUser() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        WorkOrderResponse response = workOrderService.create(
                new CreateWorkOrderRequest("  打印机故障  ", "无法打印", "设备维修", "高"),
                currentUser);

        assertThat(response.id()).isPositive();
        assertThat(response.title()).isEqualTo("打印机故障");
        assertThat(response.description()).isEqualTo("无法打印");
        assertThat(response.type()).isEqualTo("设备维修");
        assertThat(response.priority()).isEqualTo("高");
        assertThat(response.status()).isEqualTo("待处理");
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(response.creatorUsername()).isEqualTo("demo");

        var stored = jdbcTemplate.queryForMap("SELECT status, creator_id FROM work_orders WHERE id = ?", response.id());
        assertThat(stored.get("STATUS")).isEqualTo("待处理");
        assertThat(((Number) stored.get("CREATOR_ID")).longValue()).isEqualTo(1L);
    }

    @Test
    void listsOnlyCurrentUsersWorkOrdersForRegularUser() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThat(workOrderService.listVisible(null, currentUser).items())
                .extracting(WorkOrderResponse::title)
                .contains("自己的工单")
                .doesNotContain("别人的工单");
    }

    @Test
    void listsAllWorkOrdersForAdmin() {
        CurrentUser admin = new CurrentUser(3L, "admin", "管理员", "ADMIN");

        assertThat(workOrderService.listVisible(null, admin).items())
                .extracting(WorkOrderResponse::title)
                .contains("自己的工单", "别人的工单");
    }

    @Test
    void regularUserCanViewOwnWorkOrderDetail() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        WorkOrderResponse response = workOrderService.getVisibleDetail(1L, currentUser);

        assertThat(response.title()).isEqualTo("自己的工单");
        assertThat(response.description()).isEqualTo("自己的描述");
        assertThat(response.creatorUsername()).isEqualTo("demo");
    }

    @Test
    void regularUserCannotViewOthersWorkOrderDetail() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.getVisibleDetail(2L, currentUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void adminCanViewAnyWorkOrderDetail() {
        CurrentUser admin = new CurrentUser(3L, "admin", "管理员", "ADMIN");

        WorkOrderResponse response = workOrderService.getVisibleDetail(2L, admin);

        assertThat(response.title()).isEqualTo("别人的工单");
        assertThat(response.description()).isEqualTo("敏感描述");
        assertThat(response.creatorUsername()).isEqualTo("other");
    }

    @Test
    void rejectsMissingWorkOrderDetail() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.getVisibleDetail(404L, currentUser))
                .isInstanceOf(WorkOrderNotFoundException.class)
                .hasMessage("工单不存在");
    }

    @Test
    void rejectsBlankTitle() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.create(
                new CreateWorkOrderRequest(" ", "无法打印", "设备维修", "中"),
                currentUser))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("标题不能为空");
    }

    @Test
    void rejectsBlankDescription() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.create(
                new CreateWorkOrderRequest("打印机故障", " ", "设备维修", "中"),
                currentUser))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("详细描述不能为空");
    }

    @Test
    void rejectsInvalidPriority() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.create(
                new CreateWorkOrderRequest("打印机故障", "无法打印", "设备维修", "紧急"),
                currentUser))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("优先级只能是低、中、高");
    }

    @Test
    void regularUserUpdatesOwnPendingWorkOrder() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        WorkOrderResponse response = workOrderService.update(
                1L,
                new UpdateWorkOrderRequest("  新标题  ", "新描述", "账号问题", "高"),
                currentUser);

        assertThat(response.title()).isEqualTo("新标题");
        assertThat(response.description()).isEqualTo("新描述");
        assertThat(response.type()).isEqualTo("账号问题");
        assertThat(response.priority()).isEqualTo("高");
        assertThat(response.status()).isEqualTo("待处理");
        assertThat(response.creatorId()).isEqualTo(1L);
    }

    @Test
    void regularUserCannotUpdateOthersWorkOrder() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.update(
                2L,
                new UpdateWorkOrderRequest("越权修改", "越权描述", "账号问题", "低"),
                currentUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");

        assertThat(workOrderService.getVisibleDetail(2L, new CurrentUser(3L, "admin", "管理员", "ADMIN")).title())
                .isEqualTo("别人的工单");
    }

    @Test
    void rejectsUpdatesForCancelledAndCompletedWorkOrders() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");
        UpdateWorkOrderRequest request = new UpdateWorkOrderRequest("新标题", "新描述", "设备维修", "高");

        assertThatThrownBy(() -> workOrderService.update(3L, request, currentUser))
                .isInstanceOf(WorkOrderStateException.class)
                .hasMessage("只有待处理工单可以修改或取消");
        assertThatThrownBy(() -> workOrderService.update(4L, request, currentUser))
                .isInstanceOf(WorkOrderStateException.class)
                .hasMessage("只有待处理工单可以修改或取消");
    }

    @Test
    void adminUpdatesOthersPendingWorkOrderButCannotUpdateCompletedWorkOrder() {
        CurrentUser admin = new CurrentUser(3L, "admin", "管理员", "ADMIN");

        WorkOrderResponse updated = workOrderService.update(
                2L,
                new UpdateWorkOrderRequest("管理员修改", "处理说明", "账号问题", "低"),
                admin);
        assertThat(updated.title()).isEqualTo("管理员修改");
        assertThat(updated.creatorId()).isEqualTo(2L);

        assertThatThrownBy(() -> workOrderService.update(
                4L,
                new UpdateWorkOrderRequest("不能修改", "处理说明", "账号问题", "低"),
                admin))
                .isInstanceOf(WorkOrderStateException.class);
    }

    @Test
    void rejectsInvalidPriorityWhenUpdating() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.update(
                1L,
                new UpdateWorkOrderRequest("新标题", "新描述", "设备维修", "紧急"),
                currentUser))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("优先级只能是低、中、高");
    }

    @Test
    void regularUserCancelsOwnPendingWorkOrderWithoutDeletingIt() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        WorkOrderResponse response = workOrderService.cancel(1L, currentUser);

        assertThat(response.status()).isEqualTo("已取消");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_orders WHERE id = ?", Integer.class, 1L))
                .isEqualTo(1);
    }

    @Test
    void regularUserCannotCancelOthersWorkOrder() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.cancel(2L, currentUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void rejectsCancellingCancelledOrCompletedWorkOrder() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "演示用户", "USER");

        assertThatThrownBy(() -> workOrderService.cancel(3L, currentUser))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.cancel(4L, currentUser))
                .isInstanceOf(WorkOrderStateException.class);
    }

    @Test
    void adminCancelsOthersPendingWorkOrderButCannotCancelCompletedWorkOrder() {
        CurrentUser admin = new CurrentUser(3L, "admin", "管理员", "ADMIN");

        assertThat(workOrderService.cancel(2L, admin).status()).isEqualTo("已取消");
        assertThatThrownBy(() -> workOrderService.cancel(4L, admin))
                .isInstanceOf(WorkOrderStateException.class);
    }
}
