package com.example.workorder.workorder;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.auth.Role;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public WorkOrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PagedWorkOrderResponse listVisible(WorkOrderListQuery query, CurrentUser currentUser) {
        NormalizedListQuery normalized = normalizeListQuery(query);
        List<Object> params = new ArrayList<>();
        Long forcedCreatorId = Role.ADMIN.name().equals(currentUser.role()) ? null : currentUser.id();
        String where = buildListWhere(normalized, forcedCreatorId, params);
        return listByCriteria(normalized, where, params);
    }

    public PagedWorkOrderResponse listAllForAdmin(WorkOrderListQuery query) {
        NormalizedListQuery normalized = normalizeListQuery(query);
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(normalized, null, params);
        return listByCriteria(normalized, where, params);
    }

    public List<AdminHandlerResponse> listEnabledAdminHandlers() {
        return jdbcTemplate.query(
                """
                SELECT id, username, nickname
                FROM users
                WHERE role = ? AND enabled = TRUE
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

    @Transactional
    public WorkOrderResponse assignHandler(Long id, AssignWorkOrderRequest request, CurrentUser admin) {
        if (admin == null || !Role.ADMIN.name().equals(admin.role())) {
            throw new ForbiddenException();
        }
        if (request == null || request.handlerId() == null || request.handlerId() < 1) {
            throw new WorkOrderException("\u5904\u7406\u4eba\u53c2\u6570\u4e0d\u6b63\u786e");
        }

        WorkOrderResponse existing = findById(id);
        requireAssignable(existing);
        requireEnabledAdminHandler(request.handlerId());

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
        String title = requireText(request.title(), "\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a");
        String description = requireText(request.description(), "\u8be6\u7ec6\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a");
        String type = requireText(request.type(), "\u5de5\u5355\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        String priority = requireText(request.priority(), "\u4f18\u5148\u7ea7\u4e0d\u80fd\u4e3a\u7a7a");
        if (!ALLOWED_PRIORITIES.contains(priority)) {
            throw new WorkOrderException("\u4f18\u5148\u7ea7\u53ea\u80fd\u662f\u4f4e\u3001\u4e2d\u3001\u9ad8");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO work_orders (title, description, type, priority, status, creator_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, type);
            ps.setString(4, priority);
            ps.setString(5, INITIAL_STATUS);
            ps.setLong(6, creator.id());
            return ps;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new WorkOrderException("\u521b\u5efa\u5de5\u5355\u5931\u8d25");
        }
        recordOperation(key.longValue(), creator, "create", null, null, title, null);
        return getVisibleDetail(key.longValue(), creator);
    }

    public WorkOrderResponse getVisibleDetail(Long id, CurrentUser currentUser) {
        WorkOrderResponse response = findById(id);
        requireCanManage(response, currentUser);
        return response;
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
        return findById(id);
    }

    public WorkOrderResponse cancel(Long id, CurrentUser currentUser) {
        WorkOrderResponse existing = findById(id);
        requireCreator(existing, currentUser);

        return transitionStatus(existing, INITIAL_STATUS, CANCELLED_STATUS, currentUser, "cancel");
    }

    @Transactional
    public WorkOrderResponse accept(Long id, CurrentUser admin) {
        requireAdmin(admin);
        WorkOrderResponse existing = findById(id);
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
        requireAdmin(admin);
        WorkOrderResponse existing = findById(id);
        requireHandler(existing, admin);
        return transitionStatus(existing, PROCESSING_STATUS, WAITING_CONFIRMATION_STATUS, admin, "submit");
    }

    @Transactional
    public WorkOrderResponse returnToProcessing(Long id, CurrentUser admin) {
        requireAdmin(admin);
        WorkOrderResponse existing = findById(id);
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
                       wo.handler_id, h.username AS handler_username, wo.created_at
                FROM work_orders wo
                JOIN users u ON u.id = wo.creator_id
                LEFT JOIN users h ON h.id = wo.handler_id
                """;
    }

    private String countSelect() {
        return """
                SELECT COUNT(*)
                FROM work_orders wo
                JOIN users u ON u.id = wo.creator_id
                LEFT JOIN users h ON h.id = wo.handler_id
                """;
    }

    private String buildListWhere(NormalizedListQuery query, Long forcedCreatorId, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (forcedCreatorId != null) {
            conditions.add("wo.creator_id = ?");
            params.add(forcedCreatorId);
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
                rs.getTimestamp("created_at").toInstant());
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
        if (!Role.ADMIN.name().equals(currentUser.role()) && !workOrder.creatorId().equals(currentUser.id())) {
            throw new ForbiddenException();
        }
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
        recordOperation(existing.id(), actor, action, "status", expectedStatus, nextStatus, null);
        return findById(existing.id());
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

    private void requireEnabledAdminHandler(Long handlerId) {
        try {
            Boolean valid = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN role = ? AND enabled = TRUE THEN TRUE ELSE FALSE END FROM users WHERE id = ?",
                    Boolean.class,
                    Role.ADMIN.name(),
                    handlerId);
            if (!Boolean.TRUE.equals(valid)) {
                throw new WorkOrderException("\u5904\u7406\u4eba\u5fc5\u987b\u662f\u542f\u7528\u72b6\u6001\u7684\u7ba1\u7406\u5458");
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
