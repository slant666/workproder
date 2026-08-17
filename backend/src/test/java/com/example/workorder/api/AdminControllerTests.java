package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.AdminUserListQuery;
import com.example.workorder.auth.AdminUserResponse;
import com.example.workorder.auth.AdminUserService;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.workorder.AdminHandlerResponse;
import com.example.workorder.workorder.AdminWorkOrderCountResponse;
import com.example.workorder.workorder.AssignWorkOrderRequest;
import com.example.workorder.workorder.DailyWorkOrderCountResponse;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderCountResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import com.example.workorder.workorder.WorkOrderStatisticsQuery;
import com.example.workorder.workorder.WorkOrderStatisticsResponse;
import com.example.workorder.workorder.WorkOrderStatisticsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AdminControllerTests {

    private final AdminController controller = new AdminController(
            new PermissionService(), new WorkOrderService(null), new FakeWorkOrderStatisticsService(), new FakeAdminUserService());

    @Test
    void adminCanOpenAdminOverview() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        Map<String, Object> response = controller.overview(session);

        assertThat(response).containsEntry("status", "ok").containsEntry("area", "admin");
    }

    @Test
    void userCannotOpenAdminOverview() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.overview(session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void adminCanListUsers() {
        FakeAdminUserService adminUserService = new FakeAdminUserService();
        AdminController controller = new AdminController(new PermissionService(), new FakeWorkOrderService(), new FakeWorkOrderStatisticsService(), adminUserService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        assertThat(controller.users("demo", 2, 20, session).total()).isEqualTo(1);
        assertThat(adminUserService.query).isEqualTo(new AdminUserListQuery("demo", 2, 20));
    }

    @Test
    void userCannotListUsers() {
        FakeAdminUserService adminUserService = new FakeAdminUserService();
        AdminController controller = new AdminController(new PermissionService(), new FakeWorkOrderService(), new FakeWorkOrderStatisticsService(), adminUserService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.users(null, null, null, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(adminUserService.query).isNull();
    }

    @Test
    void adminCanListWorkOrdersWithFilters() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        PagedWorkOrderResponse response = controller.workOrders(
                "printer", "\u5f85\u5904\u7406", "\u9ad8", 2L, 3L,
                "2026-08-01", "2026-08-08", "createdAtAsc", 2, 20, session);

        assertThat(response.total()).isEqualTo(1);
        assertThat(workOrderService.query).isEqualTo(new WorkOrderListQuery(
                "printer", "\u5f85\u5904\u7406", "\u9ad8", 2L, 3L,
                "2026-08-01", "2026-08-08", "createdAtAsc", 2, 20));
    }

    @Test
    void userCannotListAdminWorkOrders() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.workOrders(null, null, null, null, null, null, null, null, null, null, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(workOrderService.query).isNull();
    }

    @Test
    void adminCanViewWorkOrderStatisticsWithDateRange() {
        FakeWorkOrderStatisticsService statisticsService = new FakeWorkOrderStatisticsService();
        AdminController controller = new AdminController(
                new PermissionService(), new FakeWorkOrderService(), statisticsService, new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        WorkOrderStatisticsResponse response = controller.workOrderStatistics("2026-08-01", "2026-08-08", session);

        assertThat(response.total()).isEqualTo(3);
        assertThat(response.averageProcessingMinutes()).isEqualTo(120);
        assertThat(response.overdueUnhandledCount()).isEqualTo(1);
        assertThat(response.dailyNewCounts()).containsExactly(new DailyWorkOrderCountResponse(java.time.LocalDate.parse("2026-08-01"), 2));
        assertThat(statisticsService.query).isEqualTo(new WorkOrderStatisticsQuery("2026-08-01", "2026-08-08"));
    }

    @Test
    void userCannotViewWorkOrderStatistics() {
        FakeWorkOrderStatisticsService statisticsService = new FakeWorkOrderStatisticsService();
        AdminController controller = new AdminController(
                new PermissionService(), new FakeWorkOrderService(), statisticsService, new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.workOrderStatistics(null, null, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(statisticsService.query).isNull();
    }

    @Test
    void adminCanListEnabledHandlers() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        assertThat(controller.handlers(session))
                .containsExactly(new AdminHandlerResponse(3L, "handler", "Handler"));
    }

    @Test
    void userCannotListEnabledHandlers() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.handlers(session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void adminCanAssignHandler() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        WorkOrderResponse response = controller.assignHandler(10L, new AssignWorkOrderRequest(3L), session);

        assertThat(response.handlerId()).isEqualTo(3L);
        assertThat(workOrderService.assignedWorkOrderId).isEqualTo(10L);
        assertThat(workOrderService.assignRequest).isEqualTo(new AssignWorkOrderRequest(3L));
        assertThat(workOrderService.assigningAdmin.username()).isEqualTo("admin");
    }

    @Test
    void userCannotReachAssignHandler() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.assignHandler(10L, new AssignWorkOrderRequest(3L), session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(workOrderService.assignRequest).isNull();
    }

    @Test
    void adminCanRunAdminStateActions() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        assertThat(controller.acceptWorkOrder(10L, session).status()).isEqualTo("\u5904\u7406\u4e2d");
        assertThat(controller.submitWorkOrder(10L, session).status()).isEqualTo("\u5f85\u786e\u8ba4");
        assertThat(controller.returnWorkOrder(10L, session).status()).isEqualTo("\u5904\u7406\u4e2d");
        assertThat(workOrderService.acceptedId).isEqualTo(10L);
        assertThat(workOrderService.submittedId).isEqualTo(10L);
        assertThat(workOrderService.returnedId).isEqualTo(10L);
    }

    @Test
    void userCannotReachAdminStateActions() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService, new FakeWorkOrderStatisticsService(), new FakeAdminUserService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.acceptWorkOrder(10L, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThatThrownBy(() -> controller.submitWorkOrder(10L, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThatThrownBy(() -> controller.returnWorkOrder(10L, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(workOrderService.acceptedId).isNull();
        assertThat(workOrderService.submittedId).isNull();
        assertThat(workOrderService.returnedId).isNull();
    }

    private static class FakeAdminUserService extends AdminUserService {
        private AdminUserListQuery query;

        FakeAdminUserService() {
            super(null);
        }

        @Override
        public com.example.workorder.auth.PagedAdminUserResponse list(AdminUserListQuery query) {
            this.query = query;
            return new com.example.workorder.auth.PagedAdminUserResponse(
                    List.of(new AdminUserResponse(
                            2L, "demo", "Demo", "USER", true,
                            Instant.parse("2026-08-07T00:00:00Z"),
                            Instant.parse("2026-08-08T00:00:00Z"))),
                    1, 2, 20, 1);
        }
    }

    private static class FakeWorkOrderStatisticsService extends WorkOrderStatisticsService {
        private WorkOrderStatisticsQuery query;

        FakeWorkOrderStatisticsService() {
            super(null);
        }

        @Override
        public WorkOrderStatisticsResponse dashboard(WorkOrderStatisticsQuery query) {
            this.query = query;
            return new WorkOrderStatisticsResponse(
                    3,
                    List.of(new WorkOrderCountResponse("\u5f85\u5904\u7406", 1), new WorkOrderCountResponse("\u5df2\u5b8c\u6210", 2)),
                    List.of(new WorkOrderCountResponse("\u9ad8", 2), new WorkOrderCountResponse("\u4e2d", 1)),
                    List.of(new DailyWorkOrderCountResponse(java.time.LocalDate.parse("2026-08-01"), 2)),
                    120,
                    List.of(new AdminWorkOrderCountResponse(1L, "admin", "Admin", 2)),
                    1,
                    "\u9996\u6b21\u63a5\u5355\u5230\u7528\u6237\u786e\u8ba4\u5b8c\u6210",
                    "\u72b6\u6001\u4e3a\u5f85\u5904\u7406\u4e14\u521b\u5efa\u65f6\u95f4\u8d85\u8fc7 48 \u5c0f\u65f6");
        }
    }

    private static class FakeWorkOrderService extends WorkOrderService {
        private WorkOrderListQuery query;
        private Long assignedWorkOrderId;
        private AssignWorkOrderRequest assignRequest;
        private CurrentUser assigningAdmin;
        private Long acceptedId;
        private Long submittedId;
        private Long returnedId;

        FakeWorkOrderService() {
            super(null);
        }

        @Override
        public PagedWorkOrderResponse listVisible(WorkOrderListQuery query, CurrentUser currentUser) {
            this.query = query;
            return responsePage();
        }

        @Override
        public PagedWorkOrderResponse listAllForAdmin(WorkOrderListQuery query) {
            this.query = query;
            return responsePage();
        }

        private PagedWorkOrderResponse responsePage() {
            return new PagedWorkOrderResponse(
                    List.of(new WorkOrderResponse(
                            1L, "Printer", "Description", "Device", "\u9ad8", "\u5f85\u5904\u7406",
                            2L, "creator", 3L, "handler", Instant.parse("2026-08-07T00:00:00Z"))),
                    1, 2, 20, 1);
        }

        @Override
        public List<AdminHandlerResponse> listEnabledAdminHandlers() {
            return List.of(new AdminHandlerResponse(3L, "handler", "Handler"));
        }

        @Override
        public WorkOrderResponse assignHandler(Long id, AssignWorkOrderRequest request, CurrentUser admin) {
            this.assignedWorkOrderId = id;
            this.assignRequest = request;
            this.assigningAdmin = admin;
            return new WorkOrderResponse(
                    id, "Printer", "Description", "Device", "\u9ad8", "\u5f85\u5904\u7406",
                    2L, "creator", request.handlerId(), "handler", Instant.parse("2026-08-07T00:00:00Z"));
        }

        @Override
        public WorkOrderResponse accept(Long id, CurrentUser admin) {
            acceptedId = id;
            return responseWithStatus(id, "\u5904\u7406\u4e2d");
        }

        @Override
        public WorkOrderResponse submitForConfirmation(Long id, CurrentUser admin) {
            submittedId = id;
            return responseWithStatus(id, "\u5f85\u786e\u8ba4");
        }

        @Override
        public WorkOrderResponse returnToProcessing(Long id, CurrentUser admin) {
            returnedId = id;
            return responseWithStatus(id, "\u5904\u7406\u4e2d");
        }

        private WorkOrderResponse responseWithStatus(Long id, String status) {
            return new WorkOrderResponse(
                    id, "Printer", "Description", "Device", "\u9ad8", status,
                    2L, "creator", 3L, "handler", Instant.parse("2026-08-07T00:00:00Z"));
        }
    }
}
