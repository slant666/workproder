package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.workorder.AdminHandlerResponse;
import com.example.workorder.workorder.AssignWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AdminControllerTests {

    private final AdminController controller = new AdminController(new PermissionService(), new WorkOrderService(null));

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
    void adminCanListWorkOrdersWithFilters() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
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
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.workOrders(null, null, null, null, null, null, null, null, null, null, session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(workOrderService.query).isNull();
    }

    @Test
    void adminCanListEnabledHandlers() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        assertThat(controller.handlers(session))
                .containsExactly(new AdminHandlerResponse(3L, "handler", "Handler"));
    }

    @Test
    void userCannotListEnabledHandlers() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.handlers(session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void adminCanAssignHandler() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "admin", "Admin", "ADMIN"));

        WorkOrderResponse response = controller.assignHandler(10L, new AssignWorkOrderRequest(3L), session);

        assertThat(response.handlerId()).isEqualTo(3L);
        assertThat(workOrderService.assignedWorkOrderId).isEqualTo(10L);
        assertThat(workOrderService.assignRequest).isEqualTo(new AssignWorkOrderRequest(3L));
        assertThat(workOrderService.assigningAdmin.username()).isEqualTo("admin");
    }

    @Test
    void userCannotAssignHandler() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        AdminController controller = new AdminController(new PermissionService(), workOrderService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(2L, "demo", "Demo", "USER"));

        assertThatThrownBy(() -> controller.assignHandler(10L, new AssignWorkOrderRequest(3L), session))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThat(workOrderService.assignRequest).isNull();
    }

    private static class FakeWorkOrderService extends WorkOrderService {
        private WorkOrderListQuery query;
        private Long assignedWorkOrderId;
        private AssignWorkOrderRequest assignRequest;
        private CurrentUser assigningAdmin;

        FakeWorkOrderService() {
            super(null);
        }

        @Override
        public PagedWorkOrderResponse listAllForAdmin(WorkOrderListQuery query) {
            this.query = query;
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
    }
}
