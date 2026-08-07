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
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_status_transitions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_assignments");
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
        jdbcTemplate.execute("""
                CREATE TABLE work_order_assignments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    work_order_id BIGINT NOT NULL,
                    old_handler_id BIGINT NULL,
                    new_handler_id BIGINT NOT NULL,
                    assigned_by BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "demo", "演示用户", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "other", "其他用户", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "admin", "管理员", "hash", "ADMIN");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, TRUE)",
                "handler", "Handler", "hash", "ADMIN");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, ?, FALSE)",
                "disabled", "Disabled", "hash", "ADMIN");
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
    void adminCannotCancelOthersWorkOrder() {
        CurrentUser admin = new CurrentUser(3L, "admin", "管理员", "ADMIN");

        assertThatThrownBy(() -> workOrderService.cancel(2L, admin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.cancel(4L, admin))
                .isInstanceOf(ForbiddenException.class);
    }
    @Test
    void supportsLegalStatusTransitionsAndRecordsHistory() {
        CurrentUser creator = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser handler = new CurrentUser(4L, "handler", "Handler", "ADMIN");

        WorkOrderResponse accepted = workOrderService.accept(1L, handler);
        WorkOrderResponse submitted = workOrderService.submitForConfirmation(1L, handler);
        WorkOrderResponse returned = workOrderService.returnToProcessing(1L, handler);
        WorkOrderResponse resubmitted = workOrderService.submitForConfirmation(1L, handler);
        WorkOrderResponse completed = workOrderService.confirmCompletion(1L, creator);

        assertThat(accepted.status()).isEqualTo("\u5904\u7406\u4e2d");
        assertThat(accepted.handlerId()).isEqualTo(4L);
        assertThat(submitted.status()).isEqualTo("\u5f85\u786e\u8ba4");
        assertThat(returned.status()).isEqualTo("\u5904\u7406\u4e2d");
        assertThat(resubmitted.status()).isEqualTo("\u5f85\u786e\u8ba4");
        assertThat(completed.status()).isEqualTo("\u5df2\u5b8c\u6210");
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM work_order_status_transitions WHERE work_order_id = ? ORDER BY id",
                String.class,
                1L))
                .containsExactly("accept", "submit", "return", "submit", "confirm");
    }

    @Test
    void rejectsIllegalStatusTransitions() {
        CurrentUser creator = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser handler = new CurrentUser(4L, "handler", "Handler", "ADMIN");

        assertThatThrownBy(() -> workOrderService.submitForConfirmation(1L, handler))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.confirmCompletion(1L, creator))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.returnToProcessing(1L, handler))
                .isInstanceOf(ForbiddenException.class);

        workOrderService.accept(1L, handler);
        assertThatThrownBy(() -> workOrderService.cancel(1L, creator))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.confirmCompletion(1L, creator))
                .isInstanceOf(WorkOrderStateException.class);

        workOrderService.submitForConfirmation(1L, handler);
        assertThatThrownBy(() -> workOrderService.cancel(1L, creator))
                .isInstanceOf(WorkOrderStateException.class);

        workOrderService.confirmCompletion(1L, creator);
        assertThatThrownBy(() -> workOrderService.accept(1L, handler))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.submitForConfirmation(1L, handler))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.returnToProcessing(1L, handler))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.cancel(1L, creator))
                .isInstanceOf(WorkOrderStateException.class);
    }

    @Test
    void enforcesRoleSpecificStatusActions() {
        CurrentUser creator = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser other = new CurrentUser(2L, "other", "Other", "USER");
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");
        CurrentUser handler = new CurrentUser(4L, "handler", "Handler", "ADMIN");

        assertThatThrownBy(() -> workOrderService.accept(1L, creator))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.cancel(1L, admin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.cancel(1L, other))
                .isInstanceOf(ForbiddenException.class);

        workOrderService.accept(1L, handler);
        assertThatThrownBy(() -> workOrderService.submitForConfirmation(1L, admin))
                .isInstanceOf(ForbiddenException.class);
        workOrderService.submitForConfirmation(1L, handler);
        assertThatThrownBy(() -> workOrderService.confirmCompletion(1L, other))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.confirmCompletion(1L, admin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> workOrderService.returnToProcessing(1L, admin))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void terminalStatusesCannotTransition() {
        CurrentUser creator = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser handler = new CurrentUser(4L, "handler", "Handler", "ADMIN");

        assertThatThrownBy(() -> workOrderService.accept(3L, handler))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.accept(4L, handler))
                .isInstanceOf(WorkOrderStateException.class);
        assertThatThrownBy(() -> workOrderService.confirmCompletion(4L, creator))
                .isInstanceOf(WorkOrderStateException.class);
    }

    @Test
    void listsOnlyEnabledAdminHandlers() {
        assertThat(workOrderService.listEnabledAdminHandlers())
                .extracting(AdminHandlerResponse::username)
                .containsExactly("admin", "handler");
    }

    @Test
    void adminAssignsAndReassignsPendingWorkOrderWithHistory() {
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");

        WorkOrderResponse assigned = workOrderService.assignHandler(1L, new AssignWorkOrderRequest(4L), admin);
        WorkOrderResponse reassigned = workOrderService.assignHandler(1L, new AssignWorkOrderRequest(3L), admin);

        assertThat(assigned.handlerId()).isEqualTo(4L);
        assertThat(assigned.handlerUsername()).isEqualTo("handler");
        assertThat(reassigned.handlerId()).isEqualTo(3L);
        assertThat(reassigned.handlerUsername()).isEqualTo("admin");
        assertThat(jdbcTemplate.queryForList(
                "SELECT old_handler_id, new_handler_id, assigned_by FROM work_order_assignments WHERE work_order_id = ? ORDER BY id",
                1L))
                .hasSize(2)
                .satisfies(rows -> {
                    assertThat(rows.get(0).get("OLD_HANDLER_ID")).isNull();
                    assertThat(((Number) rows.get(0).get("NEW_HANDLER_ID")).longValue()).isEqualTo(4L);
                    assertThat(((Number) rows.get(0).get("ASSIGNED_BY")).longValue()).isEqualTo(3L);
                    assertThat(((Number) rows.get(1).get("OLD_HANDLER_ID")).longValue()).isEqualTo(4L);
                    assertThat(((Number) rows.get(1).get("NEW_HANDLER_ID")).longValue()).isEqualTo(3L);
                    assertThat(((Number) rows.get(1).get("ASSIGNED_BY")).longValue()).isEqualTo(3L);
                });
    }

    @Test
    void rejectsAssigningToRegularUserDisabledAdminOrMissingUser() {
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");

        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(1L), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("\u5904\u7406\u4eba\u5fc5\u987b\u662f\u542f\u7528\u72b6\u6001\u7684\u7ba1\u7406\u5458");
        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(5L), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("\u5904\u7406\u4eba\u5fc5\u987b\u662f\u542f\u7528\u72b6\u6001\u7684\u7ba1\u7406\u5458");
        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(404L), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("\u5904\u7406\u4eba\u4e0d\u5b58\u5728");
    }

    @Test
    void regularUserCannotAssignHandler() {
        CurrentUser user = new CurrentUser(1L, "demo", "Demo", "USER");

        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(4L), user))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void rejectsAssigningCancelledOrCompletedWorkOrder() {
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");

        assertThatThrownBy(() -> workOrderService.assignHandler(3L, new AssignWorkOrderRequest(4L), admin))
                .isInstanceOf(WorkOrderStateException.class)
                .hasMessage("\u5df2\u5b8c\u6210\u6216\u5df2\u53d6\u6d88\u5de5\u5355\u4e0d\u80fd\u91cd\u65b0\u5206\u914d");
        assertThatThrownBy(() -> workOrderService.assignHandler(4L, new AssignWorkOrderRequest(4L), admin))
                .isInstanceOf(WorkOrderStateException.class)
                .hasMessage("\u5df2\u5b8c\u6210\u6216\u5df2\u53d6\u6d88\u5de5\u5355\u4e0d\u80fd\u91cd\u65b0\u5206\u914d");
    }

    @Test
    void rejectsInvalidHandlerIdWhenAssigning() {
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");

        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(null), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("\u5904\u7406\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
        assertThatThrownBy(() -> workOrderService.assignHandler(1L, new AssignWorkOrderRequest(0L), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("\u5904\u7406\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
    }
}
