package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.workorder.CreateWorkOrderCommentRequest;
import com.example.workorder.workorder.CreateWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.UpdateWorkOrderRequest;
import com.example.workorder.workorder.WorkOrderCommentResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderOperationLogResponse;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class WorkOrderControllerTests {

    private static final String PENDING = "\u5f85\u5904\u7406";
    private static final String CANCELLED = "\u5df2\u53d6\u6d88";
    private static final String HIGH = "\u9ad8";
    private static final String LOW = "\u4f4e";

    @Test
    void listsVisibleWorkOrdersForCurrentUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(1L, "demo", "Demo", "USER"));

        assertThat(controller.list(null, null, null, null, null, null, session).items()).isEmpty();
        assertThat(workOrderService.visibleUser.id()).isEqualTo(1L);
    }

    @Test
    void passesListQueryParametersToService() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(1L, "demo", "Demo", "USER"));

        controller.list("printer", PENDING, HIGH, "createdAtAsc", 2, 20, session);

        assertThat(workOrderService.query).isEqualTo(new WorkOrderListQuery("printer", PENDING, HIGH, "createdAtAsc", 2, 20));
        assertThat(workOrderService.visibleUser.id()).isEqualTo(1L);
    }

    @Test
    void createsWorkOrderForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(7L, "demo", "Demo", "USER"));

        WorkOrderResponse response = controller.create(
                new CreateWorkOrderRequest("Printer issue", "Cannot print", "Device", HIGH),
                session);

        assertThat(workOrderService.creator.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(PENDING);
        assertThat(response.creatorId()).isEqualTo(7L);
    }

    @Test
    void loadsDetailForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(3L, "admin", "Admin", "ADMIN"));

        WorkOrderResponse response = controller.detail(10L, session);

        assertThat(workOrderService.detailId).isEqualTo(10L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(3L);
        assertThat(response.title()).isEqualTo("Printer issue");
    }

    @Test
    void loadsOperationLogsForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(3L, "admin", "Admin", "ADMIN"));

        List<WorkOrderOperationLogResponse> response = controller.logs(10L, session);

        assertThat(workOrderService.logDetailId).isEqualTo(10L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(3L);
        assertThat(response).extracting(WorkOrderOperationLogResponse::action).containsExactly("create");
    }

    @Test
    void loadsCommentsForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(7L, "demo", "Demo", "USER"));

        List<WorkOrderCommentResponse> response = controller.comments(10L, session);

        assertThat(workOrderService.commentDetailId).isEqualTo(10L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(7L);
        assertThat(response).extracting(WorkOrderCommentResponse::content).containsExactly("Looks good");
    }

    @Test
    void addsCommentForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(7L, "demo", "Demo", "USER"));
        CreateWorkOrderCommentRequest request = new CreateWorkOrderCommentRequest("Hello");

        WorkOrderCommentResponse response = controller.addComment(10L, request, session);

        assertThat(workOrderService.addCommentWorkOrderId).isEqualTo(10L);
        assertThat(workOrderService.addCommentRequest).isSameAs(request);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(7L);
        assertThat(response.authorId()).isEqualTo(7L);
    }

    @Test
    void deletesCommentForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(3L, "admin", "Admin", "ADMIN"));

        controller.deleteComment(10L, 99L, session);

        assertThat(workOrderService.deleteCommentWorkOrderId).isEqualTo(10L);
        assertThat(workOrderService.deletedCommentId).isEqualTo(99L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(3L);
    }

    @Test
    void updatesWorkOrderForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(7L, "demo", "Demo", "USER"));
        UpdateWorkOrderRequest request = new UpdateWorkOrderRequest("New title", "New description", "Account", LOW);

        WorkOrderResponse response = controller.update(10L, request, session);

        assertThat(workOrderService.updatedId).isEqualTo(10L);
        assertThat(workOrderService.updateRequest).isSameAs(request);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("New title");
    }

    @Test
    void cancelsWorkOrderForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(3L, "admin", "Admin", "ADMIN"));

        WorkOrderResponse response = controller.cancel(10L, session);

        assertThat(workOrderService.cancelledId).isEqualTo(10L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(CANCELLED);
    }

    @Test
    void confirmsWorkOrderForCurrentSessionUser() {
        FakeWorkOrderService workOrderService = new FakeWorkOrderService();
        WorkOrderController controller = new WorkOrderController(new PermissionService(), workOrderService);
        MockHttpSession session = session(new CurrentUser(7L, "demo", "Demo", "USER"));

        WorkOrderResponse response = controller.confirm(10L, session);

        assertThat(workOrderService.confirmedId).isEqualTo(10L);
        assertThat(workOrderService.visibleUser.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("\u5df2\u5b8c\u6210");
    }

    private MockHttpSession session(CurrentUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, user);
        return session;
    }

    private static class FakeWorkOrderService extends WorkOrderService {
        private CurrentUser visibleUser;
        private CurrentUser creator;
        private Long detailId;
        private Long logDetailId;
        private Long commentDetailId;
        private Long addCommentWorkOrderId;
        private Long deleteCommentWorkOrderId;
        private Long deletedCommentId;
        private Long updatedId;
        private Long cancelledId;
        private Long confirmedId;
        private CreateWorkOrderCommentRequest addCommentRequest;
        private UpdateWorkOrderRequest updateRequest;
        private WorkOrderListQuery query;

        FakeWorkOrderService() {
            super(null);
        }

        @Override
        public PagedWorkOrderResponse listVisible(WorkOrderListQuery query, CurrentUser currentUser) {
            this.query = query;
            visibleUser = currentUser;
            return new PagedWorkOrderResponse(List.of(), 0, 1, 10, 0);
        }

        @Override
        public WorkOrderResponse create(CreateWorkOrderRequest request, CurrentUser creator) {
            this.creator = creator;
            return response(request.title(), creator.id(), creator.username());
        }

        @Override
        public WorkOrderResponse getVisibleDetail(Long id, CurrentUser currentUser) {
            detailId = id;
            visibleUser = currentUser;
            return response("Printer issue", 1L, "demo");
        }

        @Override
        public List<WorkOrderOperationLogResponse> listVisibleOperationLogs(Long id, CurrentUser currentUser) {
            logDetailId = id;
            visibleUser = currentUser;
            return List.of(new WorkOrderOperationLogResponse(
                    1L,
                    id,
                    currentUser.id(),
                    currentUser.username(),
                    currentUser.nickname(),
                    "create",
                    null,
                    null,
                    "Printer issue",
                    null,
                    Instant.parse("2026-08-07T00:00:00Z")));
        }

        @Override
        public List<WorkOrderCommentResponse> listVisibleComments(Long id, CurrentUser currentUser) {
            commentDetailId = id;
            visibleUser = currentUser;
            return List.of(new WorkOrderCommentResponse(
                    1L,
                    id,
                    currentUser.id(),
                    currentUser.username(),
                    currentUser.nickname(),
                    currentUser.role(),
                    "Looks good",
                    Instant.parse("2026-08-07T00:00:00Z")));
        }

        @Override
        public WorkOrderCommentResponse addComment(Long id, CreateWorkOrderCommentRequest request, CurrentUser currentUser) {
            addCommentWorkOrderId = id;
            addCommentRequest = request;
            visibleUser = currentUser;
            return new WorkOrderCommentResponse(
                    2L,
                    id,
                    currentUser.id(),
                    currentUser.username(),
                    currentUser.nickname(),
                    currentUser.role(),
                    request.content(),
                    Instant.parse("2026-08-07T01:00:00Z"));
        }

        @Override
        public void deleteComment(Long id, Long commentId, CurrentUser currentUser) {
            deleteCommentWorkOrderId = id;
            deletedCommentId = commentId;
            visibleUser = currentUser;
        }

        @Override
        public WorkOrderResponse update(Long id, UpdateWorkOrderRequest request, CurrentUser currentUser) {
            updatedId = id;
            updateRequest = request;
            visibleUser = currentUser;
            return response(request.title(), 1L, "demo");
        }

        @Override
        public WorkOrderResponse cancel(Long id, CurrentUser currentUser) {
            cancelledId = id;
            visibleUser = currentUser;
            return response("Printer issue", 1L, "demo", CANCELLED);
        }

        @Override
        public WorkOrderResponse confirmCompletion(Long id, CurrentUser currentUser) {
            confirmedId = id;
            visibleUser = currentUser;
            return response("Printer issue", 1L, "demo", "\u5df2\u5b8c\u6210");
        }

        private WorkOrderResponse response(String title, Long creatorId, String creatorUsername) {
            return response(title, creatorId, creatorUsername, PENDING);
        }

        private WorkOrderResponse response(String title, Long creatorId, String creatorUsername, String status) {
            return new WorkOrderResponse(
                    10L,
                    title,
                    "Cannot print",
                    "Device",
                    HIGH,
                    status,
                    creatorId,
                    creatorUsername,
                    Instant.parse("2026-08-07T00:00:00Z"));
        }
    }
}
