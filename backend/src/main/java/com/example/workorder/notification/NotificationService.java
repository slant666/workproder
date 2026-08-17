package com.example.workorder.notification;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.email.EmailOutboxService;
import com.example.workorder.realtime.RealtimeEventPublisher;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final EmailOutboxService emailOutboxService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Autowired
    public NotificationService(JdbcTemplate jdbcTemplate, EmailOutboxService emailOutboxService, RealtimeEventPublisher realtimeEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailOutboxService = emailOutboxService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    public NotificationService(JdbcTemplate jdbcTemplate, EmailOutboxService emailOutboxService) {
        this(jdbcTemplate, emailOutboxService, null);
    }

    public PagedNotificationResponse list(CurrentUser user, Integer page, Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id = ?",
                Long.class,
                user.id());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedPageSize);
        List<NotificationResponse> items = jdbcTemplate.query(
                """
                SELECT id, type, title, content, work_order_id, read_at, created_at
                FROM notifications
                WHERE recipient_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new NotificationResponse(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("content"),
                        (Long) rs.getObject("work_order_id"),
                        rs.getTimestamp("read_at") != null,
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                user.id(),
                normalizedPageSize,
                (normalizedPage - 1) * normalizedPageSize);
        return new PagedNotificationResponse(items, total, normalizedPage, normalizedPageSize, totalPages);
    }

    public UnreadNotificationCountResponse unreadCount(CurrentUser user) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL",
                Long.class,
                user.id());
        return new UnreadNotificationCountResponse(count == null ? 0 : count);
    }

    @Transactional
    public void markRead(Long id, CurrentUser user) {
        int updated = jdbcTemplate.update(
                "UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE id = ? AND recipient_id = ? AND read_at IS NULL",
                id,
                user.id());
        if (updated == 0 && !ownsNotification(id, user.id())) {
            throw new ForbiddenException();
        }
        publishUnreadCountChanged(user.id());
    }

    @Transactional
    public void markAllRead(CurrentUser user) {
        jdbcTemplate.update(
                "UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE recipient_id = ? AND read_at IS NULL",
                user.id());
        publishUnreadCountChanged(user.id());
    }

    public void notifyUser(Long recipientId, String type, String title, String content, Long workOrderId) {
        if (recipientId == null) {
            return;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO notifications (recipient_id, type, title, content, work_order_id)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, recipientId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setObject(5, workOrderId);
            return ps;
        }, keyHolder);
        Long notificationId = generatedId(keyHolder);
        publishNotification(recipientId, type, title, content, workOrderId, notificationId);
        if (emailOutboxService != null) {
            emailOutboxService.enqueueUserNotification(recipientId, type, title, content, workOrderId);
        }
    }

    public void notifyUsers(List<Long> recipientIds, String type, String title, String content, Long workOrderId, Long excludedUserId) {
        Set<Long> uniqueRecipients = new LinkedHashSet<>(recipientIds == null ? List.of() : recipientIds);
        for (Long recipientId : uniqueRecipients) {
            if (recipientId != null && !recipientId.equals(excludedUserId)) {
                notifyUser(recipientId, type, title, content, workOrderId);
            }
        }
    }

    @Transactional
    public boolean recordSlaEvent(Long workOrderId, String eventType) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO work_order_sla_events (work_order_id, event_type) VALUES (?, ?)",
                    workOrderId,
                    eventType);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public List<Long> adminAndDepartmentAdminRecipients(Long departmentId) {
        List<Long> recipients = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE enabled = TRUE AND role = 'ADMIN'",
                Long.class));
        if (departmentId != null) {
            recipients.addAll(jdbcTemplate.queryForList(
                    """
                    SELECT u.id
                    FROM users u
                    JOIN department_admins da ON da.user_id = u.id
                    WHERE u.enabled = TRUE AND da.department_id = ?
                    """,
                    Long.class,
                    departmentId));
        }
        return recipients;
    }

    private boolean ownsNotification(Long id, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND recipient_id = ?",
                Long.class,
                id,
                userId);
        return count != null && count > 0;
    }

    private void publishNotification(Long recipientId, String type, String title, String content, Long workOrderId, Long notificationId) {
        if (realtimeEventPublisher == null) {
            return;
        }
        Long unreadCount = unreadCountByUserId(recipientId);
        realtimeEventPublisher.notifyUser(
                recipientId,
                "NOTIFICATION_CREATED",
                workOrderId,
                notificationId,
                unreadCount,
                Map.of("notificationType", type, "title", title, "content", content));
    }

    private void publishUnreadCountChanged(Long recipientId) {
        if (realtimeEventPublisher == null) {
            return;
        }
        realtimeEventPublisher.notifyUser(
                recipientId,
                "UNREAD_COUNT_CHANGED",
                null,
                null,
                unreadCountByUserId(recipientId),
                Map.of());
    }

    private Long unreadCountByUserId(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL",
                Long.class,
                userId);
        return count == null ? 0 : count;
    }

    private Long generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().size() == 1 && keyHolder.getKeyList().getFirst().size() == 1) {
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null) {
            return null;
        }
        Object id = keys.getOrDefault("id", keys.get("ID"));
        return id instanceof Number number ? number.longValue() : null;
    }
}
