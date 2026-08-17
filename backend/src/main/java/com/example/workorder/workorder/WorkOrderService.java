package com.example.workorder.workorder;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.auth.RbacPermission;
import com.example.workorder.auth.Role;
import com.example.workorder.notification.NotificationService;
import com.example.workorder.redis.RedisSupportService;
import com.example.workorder.realtime.RealtimeEventPublisher;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {

    private static final String INITIAL_STATUS = "\u5f85\u5904\u7406";
    private static final String PROCESSING_STATUS = "\u5904\u7406\u4e2d";
    private static final String WAITING_CONFIRMATION_STATUS = "\u5f85\u786e\u8ba4";
    private static final String CANCELLED_STATUS = "\u5df2\u53d6\u6d88";
    private static final String COMPLETED_STATUS = "\u5df2\u5b8c\u6210";
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("\u4f4e", "\u4e2d", "\u9ad8");
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            INITIAL_STATUS, PROCESSING_STATUS, WAITING_CONFIRMATION_STATUS, COMPLETED_STATUS, CANCELLED_STATUS);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final RedisSupportService redisSupportService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private Boolean slaColumnsAvailable;

    @Autowired
    public WorkOrderService(
            JdbcTemplate jdbcTemplate,
            NotificationService notificationService,
            ObjectProvider<RedisSupportService> redisSupportServiceProvider,
            ObjectProvider<RealtimeEventPublisher> realtimeEventPublisherProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.redisSupportService = redisSupportServiceProvider.getIfAvailable();
        this.realtimeEventPublisher = realtimeEventPublisherProvider.getIfAvailable();
    }

    public WorkOrderService(JdbcTemplate jdbcTemplate, NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.redisSupportService = null;
        this.realtimeEventPublisher = null;
    }

    public WorkOrderService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public PagedWorkOrderResponse listVisible(WorkOrderListQuery query, CurrentUser currentUser) {
        NormalizedListQuery normalized = normalizeListQuery(query);
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(normalized, currentUser, false, params);
        return listByCriteria(normalized, where, params);
    }

    public PagedWorkOrderResponse listAllForAdmin(WorkOrderListQuery query) {
        NormalizedListQuery normalized = normalizeListQuery(query);
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(normalized, null, true, params);
        return listByCriteria(normalized, where, params);
    }

    public List<AdminHandlerResponse> listEnabledAdminHandlers() {
        return jdbcTemplate.query(
                """
                SELECT id, username, nickname
                FROM users
                WHERE enabled = TRUE
                  AND (role = ? OR EXISTS (
                      SELECT 1 FROM department_admins da WHERE da.user_id = users.id
                  ))
                ORDER BY username ASC, id ASC
                """,
                (rs, rowNum) -> new AdminHandlerResponse(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nickname")),
                Role.ADMIN.name());
    }

    public List<WorkOrderOperationLogResponse> listVisibleOperationLogs(Long id, CurrentUser currentUser) {
        WorkOrderResponse workOrder = findById(id);
        requireCanManage(workOrder, currentUser);
        return jdbcTemplate.query(
                """
                SELECT l.id, l.work_order_id, l.actor_id, u.username AS actor_username, u.nickname AS actor_nickname,
                       l.action, l.field_name, l.old_value, l.new_value, l.details_json, l.created_at
                FROM work_order_operation_logs l
                JOIN users u ON u.id = l.actor_id
                WHERE l.work_order_id = ?
                ORDER BY l.created_at ASC, l.id ASC
                """,
                this::mapOperationLog,
                id);
    }

    public List<WorkOrderCommentResponse> listVisibleComments(Long id, CurrentUser currentUser) {
        WorkOrderResponse workOrder = findById(id);
        requireCanManage(workOrder, currentUser);
        return jdbcTemplate.query(
                """
                SELECT c.id, c.work_order_id, c.author_id, u.username AS author_username,
                       u.nickname AS author_nickname, u.role AS author_role, c.content, c.created_at
                FROM work_order_comments c
                JOIN users u ON u.id = c.author_id
                WHERE c.work_order_id = ?
                ORDER BY c.created_at ASC, c.id ASC
                """,
                this::mapComment,
                id);
    }

    @Transactional
    public WorkOrderCommentResponse addComment(Long id, CreateWorkOrderCommentRequest request, CurrentUser currentUser) {
        WorkOrderResponse workOrder = findById(id);
        requireCanManage(workOrder, currentUser);
        if (CANCELLED_STATUS.equals(workOrder.status())) {
            throw new WorkOrderStateException("\u5df2\u53d6\u6d88\u5de5\u5355\u4e0d\u80fd\u7ee7\u7eed\u8bc4\u8bba");
        }
        String content = requireText(request == null ? null : request.content(), "\u8bc4\u8bba\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO work_order_comments (work_order_id, author_id, content)
                    VALUES (?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, id);
            ps.setLong(2, currentUser.id());
            ps.setString(3, content);
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new WorkOrderException("\u6dfb\u52a0\u8bc4\u8bba\u5931\u8d25");
        }
        recordCommentOperation(id, currentUser, "comment_add", key.longValue());
        notifyCommentParticipants(workOrder, currentUser);
        publishWorkOrderChanged("COMMENT_CREATED", id, Map.of("commentId", key.longValue()));
        return findCommentById(id, key.longValue());
    }

    @Transactional
    public void deleteComment(Long id, Long commentId, CurrentUser currentUser) {
        requireAdmin(currentUser);
        findById(id);
        WorkOrderCommentResponse comment = findCommentById(id, commentId);
        recordOperation(
                id,
                currentUser,
                "comment_delete",
                "comment",
                comment.content(),
                commentId == null ? null : commentId.toString(),
                "{\"commentId\":" + jsonNumber(commentId) + "}");
        int deleted = jdbcTemplate.update(
                "DELETE FROM work_order_comments WHERE id = ? AND work_order_id = ?",
                commentId,
                id);
        if (deleted != 1) {
            throw new WorkOrderException("\u5220\u9664\u8bc4\u8bba\u5931\u8d25");
        }
    }

    @Transactional
    public WorkOrderResponse assignHandler(Long id, AssignWorkOrderRequest request, CurrentUser admin) {
        if (request == null || request.handlerId() == null || request.handlerId() < 1) {
            throw new WorkOrderException("\u5904\u7406\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
        }

        WorkOrderResponse existing = findById(id);
        requireDepartmentAdminOrGlobal(admin, existing.departmentId());
        requireAssignable(existing);
        requireEligibleHandlerForDepartment(request.handlerId(), existing.departmentId());

        jdbcTemplate.update(
                "UPDATE work_orders SET handler_id = ? WHERE id = ?",
                request.handlerId(),
                id);
        jdbcTemplate.update(
                """
                INSERT INTO work_order_assignments (work_order_id, old_handler_id, new_handler_id, assigned_by)
                VALUES (?, ?, ?, ?)
                """,
                id,
                existing.handlerId(),
                request.handlerId(),
                admin.id());
        recordOperation(
                id,
                admin,
                "assign_handler",
                "handler",
                existing.handlerUsername(),
                usernameById(request.handlerId()),
                handlerDetails(existing.handlerId(), existing.handlerUsername(), request.handlerId()));
        notifyUser(request.handlerId(), "WORK_ORDER_ASSIGNED", "工单已分配", "你被分配了工单：" + existing.title(), id);
        publishWorkOrderChanged("WORK_ORDER_ASSIGNED", id, Map.of("handlerId", request.handlerId()));
        evictStatisticsCache();
        return findById(id);
    }

    private PagedWorkOrderResponse listByCriteria(NormalizedListQuery normalized, String where, List<Object> params) {
        long total = jdbcTemplate.queryForObject(
                countSelect() + where,
                Long.class,
                params.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalized.pageSize());
        int offset = (normalized.page() - 1) * normalized.pageSize();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(normalized.pageSize());
        pageParams.add(offset);
        List<WorkOrderResponse> items = jdbcTemplate.query(
                baseSelect() + where + orderBy(normalized.sort()) + " LIMIT ? OFFSET ?",
                this::mapWorkOrder,
                pageParams.toArray());
        return new PagedWorkOrderResponse(items, total, normalized.page(), normalized.pageSize(), totalPages);
    }

    @Transactional
    public WorkOrderResponse create(CreateWorkOrderRequest request, CurrentUser creator) {
        if (creator == null || !creator.orgConfirmed() || creator.departmentId() == null) {
            throw new WorkOrderException("请先完成部门归属确认");
        }
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            WorkOrderResponse existing = tryClaimIdempotencyKey(creator, idempotencyKey);
            if (existing != null) {
                return existing;
            }
        }
        String title = requireText(request.title(), "\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a");
        String description = requireText(request.description(), "\u8be6\u7ec6\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a");
        String type = requireText(request.type(), "\u5de5\u5355\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        String priority = requireText(request.priority(), "\u4f18\u5148\u7ea7\u4e0d\u80fd\u4e3a\u7a7a");
        if (!ALLOWED_PRIORITIES.contains(priority)) {
            throw new WorkOrderException("\u4f18\u5148\u7ea7\u53ea\u80fd\u662f\u4f4e\u3001\u4e2d\u3001\u9ad8");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            boolean includeSla = hasSlaColumns();
            PreparedStatement ps = connection.prepareStatement(
                    includeSla
                            ? """
                              INSERT INTO work_orders
                                  (title, description, type, priority, status, creator_id, company_id, department_id, team_id,
                                   first_response_due_at, resolution_due_at, sla_status)
                              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                              """
                            : """
                              INSERT INTO work_orders
                                  (title, description, type, priority, status, creator_id, company_id, department_id, team_id)
                              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                              """,
                    Statement.RETURN_GENERATED_KEYS);
            Instant now = Instant.now();
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, type);
            ps.setString(4, priority);
            ps.setString(5, INITIAL_STATUS);
            ps.setLong(6, creator.id());
            ps.setObject(7, creator.companyId());
            ps.setObject(8, creator.departmentId());
            ps.setObject(9, creator.teamId());
            if (includeSla) {
                ps.setTimestamp(10, Timestamp.from(now.plus(firstResponseDuration(priority))));
                ps.setTimestamp(11, Timestamp.from(now.plus(resolutionDuration(priority))));
                ps.setString(12, "NORMAL");
            }
            return ps;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new WorkOrderException("\u521b\u5efa\u5de5\u5355\u5931\u8d25");
        }
        if (idempotencyKey != null) {
            bindIdempotencyKey(creator, idempotencyKey, key.longValue());
        }
        recordOperation(key.longValue(), creator, "create", null, null, title, null);
        evictStatisticsCache();
        publishWorkOrderChanged("WORK_ORDER_CREATED", key.longValue(), Map.of("creatorId", creator.id(), "departmentId", creator.departmentId()));
        return getVisibleDetail(key.longValue(), creator);
    }

    private WorkOrderResponse tryClaimIdempotencyKey(CurrentUser creator, String idempotencyKey) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO work_order_idempotency_keys (creator_id, idempotency_key) VALUES (?, ?)",
                    creator.id(),
                    idempotencyKey);
            return null;
        } catch (DuplicateKeyException ex) {
            Long workOrderId = findIdempotentWorkOrderId(creator.id(), idempotencyKey);
            if (workOrderId == null) {
                throw new WorkOrderException("工单正在创建中，请稍后刷新查看");
            }
            return getVisibleDetail(workOrderId, creator);
        } catch (RuntimeException ex) {
            if (isMissingIdempotencyTable(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private void bindIdempotencyKey(CurrentUser creator, String idempotencyKey, Long workOrderId) {
        try {
            jdbcTemplate.update(
                    """
                    UPDATE work_order_idempotency_keys
                    SET work_order_id = ?
                    WHERE creator_id = ? AND idempotency_key = ? AND work_order_id IS NULL
                    """,
                    workOrderId,
                    creator.id(),
                    idempotencyKey);
        } catch (RuntimeException ex) {
            if (!isMissingIdempotencyTable(ex)) {
                throw ex;
            }
        }
    }

    private Long findIdempotentWorkOrderId(Long creatorId, String idempotencyKey) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT work_order_id
                    FROM work_order_idempotency_keys
                    WHERE creator_id = ? AND idempotency_key = ?
                    """,
                    Long.class,
                    creatorId,
                    idempotencyKey);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 80) {
            throw new WorkOrderException("请求唯一标识过长");
        }
        if (!normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new WorkOrderException("请求唯一标识格式不正确");
        }
        return normalized;
    }

    private boolean isMissingIdempotencyTable(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("work_order_idempotency_keys");
    }

    public WorkOrderResponse getVisibleDetail(Long id, CurrentUser currentUser) {
        WorkOrderResponse response = findById(id);
        requireCanManage(response, currentUser);
        return response;
    }

    public WorkOrderResponse requireVisibleWorkOrder(Long id, CurrentUser currentUser) {
        return getVisibleDetail(id, currentUser);
    }

    @Transactional
    public WorkOrderResponse update(Long id, UpdateWorkOrderRequest request, CurrentUser currentUser) {
        WorkOrderResponse existing = findById(id);
        requireCanManage(existing, currentUser);
        requirePending(existing);

        String title = requireText(request.title(), "\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a");
        String description = requireText(request.description(), "\u8be6\u7ec6\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a");
        String type = requireText(request.type(), "\u5de5\u5355\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        String priority = requireText(request.priority(), "\u4f18\u5148\u7ea7\u4e0d\u80fd\u4e3a\u7a7a");
        if (!ALLOWED_PRIORITIES.contains(priority)) {
            throw new WorkOrderException("\u4f18\u5148\u7ea7\u53ea\u80fd\u662f\u4f4e\u3001\u4e2d\u3001\u9ad8");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE work_orders
                SET title = ?, description = ?, type = ?, priority = ?
                WHERE id = ? AND status = ?
                """,
                title,
                description,
                type,
                priority,
                id,
                INITIAL_STATUS);
        requireUpdated(updated, id);
        recordFieldChange(id, currentUser, "title", existing.title(), title);
        recordFieldChange(id, currentUser, "description", existing.description(), description);
        recordFieldChange(id, currentUser, "type", existing.type(), type);
        recordFieldChange(id, currentUser, "priority", existing.priority(), priority);
        evictStatisticsCache();
        return findById(id);
    }

    public WorkOrderResponse cancel(Long id, CurrentUser currentUser) {
        WorkOrderResponse existing = findById(id);
        requireCreator(existing, currentUser);

        return transitionStatus(existing, INITIAL_STATUS, CANCELLED_STATUS, currentUser, "cancel");
    }

    @Transactional
    public WorkOrderResponse accept(Long id, CurrentUser admin) {
        WorkOrderResponse existing = findById(id);
        requireUserPermission(admin, RbacPermission.TICKET_ACCEPT);
        requireCanManage(existing, admin);
        if (existing.handlerId() != null && !existing.handlerId().equals(admin.id())) {
            throw new ForbiddenException();
        }
        if (existing.handlerId() == null) {
            jdbcTemplate.update("UPDATE work_orders SET handler_id = ? WHERE id = ?", admin.id(), id);
            jdbcTemplate.update(
                    """
                    INSERT INTO work_order_assignments (work_order_id, old_handler_id, new_handler_id, assigned_by)
                    VALUES (?, ?, ?, ?)
                    """,
                    id,
                    null,
                    admin.id(),
                    admin.id());
            recordOperation(
                    id,
                    admin,
                    "assign_handler",
                    "handler",
                    null,
                    admin.username(),
                    handlerDetails(null, null, admin.id()));
            existing = findById(id);
        }
        return transitionStatus(existing, INITIAL_STATUS, PROCESSING_STATUS, admin, "accept");
    }

    @Transactional
    public WorkOrderResponse submitForConfirmation(Long id, CurrentUser admin) {
        WorkOrderResponse existing = findById(id);
        requireUserPermission(admin, RbacPermission.TICKET_SUBMIT);
        requireHandler(existing, admin);
        return transitionStatus(existing, PROCESSING_STATUS, WAITING_CONFIRMATION_STATUS, admin, "submit");
    }

    @Transactional
    public WorkOrderResponse returnToProcessing(Long id, CurrentUser admin) {
        WorkOrderResponse existing = findById(id);
        requireUserPermission(admin, RbacPermission.TICKET_RETURN);
        requireHandler(existing, admin);
        return transitionStatus(existing, WAITING_CONFIRMATION_STATUS, PROCESSING_STATUS, admin, "return");
    }

    @Transactional
    public WorkOrderResponse confirmCompletion(Long id, CurrentUser currentUser) {
        WorkOrderResponse existing = findById(id);
        requireCreator(existing, currentUser);
        return transitionStatus(existing, WAITING_CONFIRMATION_STATUS, COMPLETED_STATUS, currentUser, "confirm");
    }

    private WorkOrderResponse findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    baseSelect() + " WHERE wo.id = ?",
                    this::mapWorkOrder,
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new WorkOrderNotFoundException();
        }
    }

    private String baseSelect() {
        return """
                SELECT wo.id, wo.title, wo.description, wo.type, wo.priority, wo.status,
                       wo.creator_id, u.username AS creator_username,
                       wo.handler_id, h.username AS handler_username,
                       wo.company_id, c.name AS company_name,
                       wo.department_id, d.name AS department_name,
                       wo.team_id, t.name AS team_name,
                       """ + slaSelectColumns() + """
                       wo.created_at
                FROM work_orders wo
                JOIN users u ON u.id = wo.creator_id
                LEFT JOIN users h ON h.id = wo.handler_id
                LEFT JOIN companies c ON c.id = wo.company_id
                LEFT JOIN departments d ON d.id = wo.department_id
                LEFT JOIN teams t ON t.id = wo.team_id
                """;
    }

    private String countSelect() {
        return """
                SELECT COUNT(*)
                FROM work_orders wo
                JOIN users u ON u.id = wo.creator_id
                LEFT JOIN users h ON h.id = wo.handler_id
                LEFT JOIN companies c ON c.id = wo.company_id
                LEFT JOIN departments d ON d.id = wo.department_id
                LEFT JOIN teams t ON t.id = wo.team_id
                """;
    }

    private String buildListWhere(NormalizedListQuery query, CurrentUser currentUser, boolean globalAdminView, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (!globalAdminView && currentUser != null && !Role.ADMIN.name().equals(currentUser.role())) {
            conditions.add("""
                    (
                        wo.creator_id = ?
                        OR wo.handler_id = ?
                        OR (
                            wo.department_id IS NOT NULL
                            AND ? = TRUE
                            AND wo.department_id = ?
                        )
                        OR EXISTS (
                            SELECT 1 FROM department_admins da
                            WHERE da.user_id = ?
                              AND da.department_id = wo.department_id
                        )
                    )
                    """);
            params.add(currentUser.id());
            params.add(currentUser.id());
            params.add(currentUser.orgConfirmed());
            params.add(currentUser.departmentId());
            params.add(currentUser.id());
        }
        if (query.keyword() != null) {
            conditions.add("LOWER(wo.title) LIKE LOWER(?) ESCAPE '\\'");
            params.add("%" + escapeLike(query.keyword()) + "%");
        }
        if (query.status() != null) {
            conditions.add("wo.status = ?");
            params.add(query.status());
        }
        if (query.priority() != null) {
            conditions.add("wo.priority = ?");
            params.add(query.priority());
        }
        if (query.creatorId() != null) {
            conditions.add("wo.creator_id = ?");
            params.add(query.creatorId());
        }
        if (query.handlerId() != null) {
            conditions.add("wo.handler_id = ?");
            params.add(query.handlerId());
        }
        if (query.createdFrom() != null) {
            conditions.add("wo.created_at >= ?");
            params.add(Timestamp.from(query.createdFrom()));
        }
        if (query.createdToExclusive() != null) {
            conditions.add("wo.created_at < ?");
            params.add(Timestamp.from(query.createdToExclusive()));
        }
        return conditions.isEmpty() ? " " : " WHERE " + String.join(" AND ", conditions) + " ";
    }

    private NormalizedListQuery normalizeListQuery(WorkOrderListQuery query) {
        String keyword = blankToNull(query == null ? null : query.keyword());
        String status = blankToNull(query == null ? null : query.status());
        String priority = blankToNull(query == null ? null : query.priority());
        String sort = blankToNull(query == null ? null : query.sort());
        Long creatorId = query == null ? null : query.creatorId();
        Long handlerId = query == null ? null : query.handlerId();
        Instant createdFrom = parseBoundary(query == null ? null : query.createdFrom(), false, "createdFrom");
        Instant createdToExclusive = parseBoundary(query == null ? null : query.createdTo(), true, "createdTo");
        int page = query == null || query.page() == null ? DEFAULT_PAGE : query.page();
        int pageSize = query == null || query.pageSize() == null ? DEFAULT_PAGE_SIZE : query.pageSize();

        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new WorkOrderException("\u5de5\u5355\u72b6\u6001\u4e0d\u6b63\u786e");
        }
        if (priority != null && !ALLOWED_PRIORITIES.contains(priority)) {
            throw new WorkOrderException("\u4f18\u5148\u7ea7\u53ea\u80fd\u662f\u4f4e\u3001\u4e2d\u3001\u9ad8");
        }
        if (sort == null) {
            sort = "createdAtDesc";
        }
        if (!"createdAtDesc".equals(sort) && !"createdAtAsc".equals(sort)) {
            throw new WorkOrderException("\u6392\u5e8f\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        if (page < 1) {
            throw new WorkOrderException("\u9875\u7801\u5fc5\u987b\u5927\u4e8e\u7b49\u4e8e 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new WorkOrderException("\u6bcf\u9875\u6570\u91cf\u5fc5\u987b\u5728 1 \u5230 50 \u4e4b\u95f4");
        }
        if (creatorId != null && creatorId < 1) {
            throw new WorkOrderException("\u521b\u5efa\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        if (handlerId != null && handlerId < 1) {
            throw new WorkOrderException("\u5904\u7406\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        if (createdFrom != null && createdToExclusive != null && !createdFrom.isBefore(createdToExclusive)) {
            throw new WorkOrderException("\u521b\u5efa\u65f6\u95f4\u8303\u56f4\u4e0d\u6b63\u786e");
        }
        return new NormalizedListQuery(keyword, status, priority, creatorId, handlerId, createdFrom, createdToExclusive, sort, page, pageSize);
    }

    private Instant parseBoundary(String value, boolean endExclusive, String fieldName) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                if (endExclusive) {
                    date = date.plusDays(1);
                }
                return date.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new WorkOrderException(fieldName + " \u683c\u5f0f\u4e0d\u6b63\u786e");
        }
    }

    private String orderBy(String sort) {
        String direction = "createdAtAsc".equals(sort) ? "ASC" : "DESC";
        return " ORDER BY wo.created_at " + direction + ", wo.id " + direction;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private WorkOrderResponse mapWorkOrder(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkOrderResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("type"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getLong("creator_id"),
                rs.getString("creator_username"),
                (Long) rs.getObject("handler_id"),
                rs.getString("handler_username"),
                (Long) rs.getObject("company_id"),
                rs.getString("company_name"),
                (Long) rs.getObject("department_id"),
                rs.getString("department_name"),
                (Long) rs.getObject("team_id"),
                rs.getString("team_name"),
                timestampToInstant(rs.getTimestamp("first_response_due_at")),
                timestampToInstant(rs.getTimestamp("resolution_due_at")),
                timestampToInstant(rs.getTimestamp("first_responded_at")),
                timestampToInstant(rs.getTimestamp("resolved_at")),
                rs.getString("sla_status"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String slaSelectColumns() {
        if (hasSlaColumns()) {
            return """
                   wo.first_response_due_at, wo.resolution_due_at,
                   wo.first_responded_at, wo.resolved_at, wo.sla_status,
                   """;
        }
        return """
               NULL AS first_response_due_at, NULL AS resolution_due_at,
               NULL AS first_responded_at, NULL AS resolved_at, 'NORMAL' AS sla_status,
               """;
    }

    private boolean hasSlaColumns() {
        if (slaColumnsAvailable != null) {
            return slaColumnsAvailable;
        }
        try {
            jdbcTemplate.queryForList("SELECT first_response_due_at FROM work_orders WHERE 1 = 0");
            slaColumnsAvailable = true;
        } catch (RuntimeException ex) {
            slaColumnsAvailable = false;
        }
        return slaColumnsAvailable;
    }

    private WorkOrderOperationLogResponse mapOperationLog(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkOrderOperationLogResponse(
                rs.getLong("id"),
                rs.getLong("work_order_id"),
                rs.getLong("actor_id"),
                rs.getString("actor_username"),
                rs.getString("actor_nickname"),
                rs.getString("action"),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("details_json"),
                rs.getTimestamp("created_at").toInstant());
    }

    private WorkOrderCommentResponse mapComment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkOrderCommentResponse(
                rs.getLong("id"),
                rs.getLong("work_order_id"),
                rs.getLong("author_id"),
                rs.getString("author_username"),
                rs.getString("author_nickname"),
                rs.getString("author_role"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant());
    }

    private WorkOrderCommentResponse findCommentById(Long workOrderId, Long commentId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT c.id, c.work_order_id, c.author_id, u.username AS author_username,
                           u.nickname AS author_nickname, u.role AS author_role, c.content, c.created_at
                    FROM work_order_comments c
                    JOIN users u ON u.id = c.author_id
                    WHERE c.work_order_id = ? AND c.id = ?
                    """,
                    this::mapComment,
                    workOrderId,
                    commentId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WorkOrderException("\u8bc4\u8bba\u4e0d\u5b58\u5728");
        }
    }

    private Number generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().size() == 1 && keyHolder.getKeyList().getFirst().size() == 1) {
            return keyHolder.getKey();
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null) {
            return null;
        }
        Object id = keys.getOrDefault("id", keys.get("ID"));
        return id instanceof Number number ? number : null;
    }

    private void requireCanManage(WorkOrderResponse workOrder, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException();
        }
        if (Role.ADMIN.name().equals(currentUser.role())
                || workOrder.creatorId().equals(currentUser.id())
                || (workOrder.handlerId() != null && workOrder.handlerId().equals(currentUser.id()))
                || (workOrder.departmentId() != null
                    && currentUser.orgConfirmed()
                    && workOrder.departmentId().equals(currentUser.departmentId()))
                || isDepartmentAdmin(currentUser.id(), workOrder.departmentId())) {
            return;
        }
        throw new ForbiddenException();
    }

    private void requireUserPermission(CurrentUser currentUser, String permission) {
        if (currentUser == null || !currentUser.hasPermission(permission)) {
            throw new ForbiddenException();
        }
    }

    private boolean isDepartmentAdmin(Long userId, Long departmentId) {
        if (userId == null || departmentId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM department_admins WHERE user_id = ? AND department_id = ?",
                Integer.class,
                userId,
                departmentId);
        return count != null && count > 0;
    }

    private void requireDepartmentAdminOrGlobal(CurrentUser currentUser, Long departmentId) {
        if (currentUser == null) {
            throw new ForbiddenException();
        }
        if (Role.ADMIN.name().equals(currentUser.role()) || isDepartmentAdmin(currentUser.id(), departmentId)) {
            return;
        }
            throw new ForbiddenException();
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || !Role.ADMIN.name().equals(currentUser.role())) {
            throw new ForbiddenException();
        }
    }

    private void requireCreator(WorkOrderResponse workOrder, CurrentUser currentUser) {
        if (currentUser == null || !workOrder.creatorId().equals(currentUser.id())) {
            throw new ForbiddenException();
        }
    }

    private void requireHandler(WorkOrderResponse workOrder, CurrentUser admin) {
        if (workOrder.handlerId() == null || !workOrder.handlerId().equals(admin.id())) {
            throw new ForbiddenException();
        }
    }

    private void requirePending(WorkOrderResponse workOrder) {
        if (!INITIAL_STATUS.equals(workOrder.status())) {
            throw new WorkOrderStateException("\u53ea\u6709\u5f85\u5904\u7406\u5de5\u5355\u53ef\u4ee5\u4fee\u6539\u6216\u53d6\u6d88");
        }
    }

    private void requireAssignable(WorkOrderResponse workOrder) {
        if (!INITIAL_STATUS.equals(workOrder.status())) {
            throw new WorkOrderStateException("\u5df2\u5b8c\u6210\u6216\u5df2\u53d6\u6d88\u5de5\u5355\u4e0d\u80fd\u91cd\u65b0\u5206\u914d");
        }
    }

    private WorkOrderResponse transitionStatus(
            WorkOrderResponse existing,
            String expectedStatus,
            String nextStatus,
            CurrentUser actor,
            String action) {
        if (!expectedStatus.equals(existing.status())) {
            throw new WorkOrderStateException("\u5de5\u5355\u72b6\u6001\u4e0d\u5141\u8bb8\u6267\u884c\u8be5\u64cd\u4f5c");
        }
        int updated = jdbcTemplate.update(
                "UPDATE work_orders SET status = ? WHERE id = ? AND status = ?",
                nextStatus,
                existing.id(),
                expectedStatus);
        if (updated != 1) {
            throw new WorkOrderStateException("\u5de5\u5355\u72b6\u6001\u4e0d\u5141\u8bb8\u6267\u884c\u8be5\u64cd\u4f5c");
        }
        jdbcTemplate.update(
                """
                INSERT INTO work_order_status_transitions (work_order_id, old_status, new_status, actor_id, action)
                VALUES (?, ?, ?, ?, ?)
                """,
                existing.id(),
                expectedStatus,
                nextStatus,
                actor.id(),
                action);
        if ("accept".equals(action) && hasSlaColumns()) {
            jdbcTemplate.update("UPDATE work_orders SET first_responded_at = COALESCE(first_responded_at, CURRENT_TIMESTAMP) WHERE id = ?", existing.id());
        }
        if ("confirm".equals(action) && hasSlaColumns()) {
            jdbcTemplate.update("UPDATE work_orders SET resolved_at = COALESCE(resolved_at, CURRENT_TIMESTAMP), sla_status = 'COMPLETED' WHERE id = ?", existing.id());
        }
        recordOperation(existing.id(), actor, action, "status", expectedStatus, nextStatus, null);
        notifyUser(existing.creatorId(), "WORK_ORDER_STATUS_CHANGED", "工单状态已更新", "你的工单「" + existing.title() + "」已变更为：" + nextStatus, existing.id());
        evictStatisticsCache();
        publishWorkOrderChanged("WORK_ORDER_STATUS_CHANGED", existing.id(), Map.of("status", nextStatus));
        return findById(existing.id());
    }

    private void evictStatisticsCache() {
        if (redisSupportService != null) {
            redisSupportService.evictStatistics();
        }
    }

    public void recordCommentOperation(Long workOrderId, CurrentUser actor, String action, Long commentId) {
        recordOperation(workOrderId, actor, action, "comment", null, commentId == null ? null : commentId.toString(), null);
    }

    public void recordAttachmentOperation(Long workOrderId, CurrentUser actor, String action, String filename) {
        recordOperation(workOrderId, actor, action, "attachment", null, filename, null);
    }

    private void recordFieldChange(
            Long workOrderId,
            CurrentUser actor,
            String fieldName,
            String oldValue,
            String newValue) {
        if (oldValue == null ? newValue == null : oldValue.equals(newValue)) {
            return;
        }
        recordOperation(workOrderId, actor, "update", fieldName, oldValue, newValue, null);
    }

    private void recordOperation(
            Long workOrderId,
            CurrentUser actor,
            String action,
            String fieldName,
            String oldValue,
            String newValue,
            String detailsJson) {
        jdbcTemplate.update(
                """
                INSERT INTO work_order_operation_logs
                    (work_order_id, actor_id, action, field_name, old_value, new_value, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                workOrderId,
                actor.id(),
                action,
                fieldName,
                oldValue,
                newValue,
                detailsJson);
    }

    private Duration firstResponseDuration(String priority) {
        return switch (priority) {
            case "高" -> Duration.ofHours(1);
            case "中" -> Duration.ofHours(8);
            default -> Duration.ofHours(24);
        };
    }

    private Duration resolutionDuration(String priority) {
        return switch (priority) {
            case "高" -> Duration.ofHours(4);
            case "中" -> Duration.ofHours(24);
            default -> Duration.ofHours(72);
        };
    }

    private void notifyUser(Long recipientId, String type, String title, String content, Long workOrderId) {
        if (notificationService != null) {
            notificationService.notifyUser(recipientId, type, title, content, workOrderId);
        }
    }

    private void publishWorkOrderChanged(String type, Long workOrderId, Map<String, Object> payload) {
        if (realtimeEventPublisher != null) {
            realtimeEventPublisher.broadcast(type, workOrderId, payload);
        }
    }

    private void notifyCommentParticipants(WorkOrderResponse workOrder, CurrentUser author) {
        if (notificationService == null) {
            return;
        }
        List<Long> recipients = new ArrayList<>();
        recipients.add(workOrder.creatorId());
        recipients.add(workOrder.handlerId());
        recipients.addAll(jdbcTemplate.queryForList(
                "SELECT DISTINCT author_id FROM work_order_comments WHERE work_order_id = ?",
                Long.class,
                workOrder.id()));
        notificationService.notifyUsers(
                recipients,
                "WORK_ORDER_COMMENTED",
                "工单有新评论",
                "工单「" + workOrder.title() + "」有新评论",
                workOrder.id(),
                author.id());
    }

    private String usernameById(Long userId) {
        if (userId == null) {
            return null;
        }
        return jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
    }

    private String handlerDetails(Long oldHandlerId, String oldHandlerUsername, Long newHandlerId) {
        return "{\"oldHandlerId\":" + jsonNumber(oldHandlerId)
                + ",\"oldHandlerUsername\":" + jsonString(oldHandlerUsername)
                + ",\"newHandlerId\":" + jsonNumber(newHandlerId)
                + ",\"newHandlerUsername\":" + jsonString(usernameById(newHandlerId))
                + "}";
    }

    private String jsonNumber(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void requireEligibleHandlerForDepartment(Long handlerId, Long departmentId) {
        try {
            Boolean valid = jdbcTemplate.queryForObject(
                    """
                    SELECT CASE
                        WHEN enabled = TRUE
                         AND (
                            role = ?
                            OR EXISTS (
                                SELECT 1 FROM department_admins da
                                WHERE da.user_id = users.id
                                  AND da.department_id = ?
                            )
                         )
                        THEN TRUE ELSE FALSE END
                    FROM users
                    WHERE id = ?
                    """,
                    Boolean.class,
                    Role.ADMIN.name(),
                    departmentId,
                    handlerId);
            if (!Boolean.TRUE.equals(valid)) {
                throw new WorkOrderException("处理人必须是启用状态的全局管理员或工单所属部门管理员");
            }
        } catch (EmptyResultDataAccessException ex) {
            throw new WorkOrderException("\u5904\u7406\u4eba\u4e0d\u5b58\u5728");
        }
    }

    private void requireUpdated(int updated, Long id) {
        if (updated != 1) {
            WorkOrderResponse current = findById(id);
            requirePending(current);
            throw new WorkOrderException("\u5de5\u5355\u66f4\u65b0\u5931\u8d25");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new WorkOrderException(message);
        }
        return value.trim();
    }

    private record NormalizedListQuery(
            String keyword,
            String status,
            String priority,
            Long creatorId,
            Long handlerId,
            Instant createdFrom,
            Instant createdToExclusive,
            String sort,
            int page,
            int pageSize) {
    }
}
