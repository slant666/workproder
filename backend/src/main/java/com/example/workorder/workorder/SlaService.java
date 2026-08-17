package com.example.workorder.workorder;

import com.example.workorder.notification.NotificationService;
import com.example.workorder.redis.RedisSupportService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaService {

    private static final String COMPLETED_STATUS = "\u5df2\u5b8c\u6210";
    private static final String CANCELLED_STATUS = "\u5df2\u53d6\u6d88";
    private static final Duration FIRST_RESPONSE_NEAR_THRESHOLD = Duration.ofMinutes(30);
    private static final Duration RESOLUTION_NEAR_THRESHOLD = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final RedisSupportService redisSupportService;
    private final Clock clock;

    @Autowired
    public SlaService(
            JdbcTemplate jdbcTemplate,
            NotificationService notificationService,
            ObjectProvider<RedisSupportService> redisSupportServiceProvider) {
        this(jdbcTemplate, notificationService, Clock.systemUTC(), redisSupportServiceProvider.getIfAvailable());
    }

    SlaService(JdbcTemplate jdbcTemplate, NotificationService notificationService, Clock clock) {
        this(jdbcTemplate, notificationService, clock, null);
    }

    SlaService(
            JdbcTemplate jdbcTemplate,
            NotificationService notificationService,
            Clock clock,
            RedisSupportService redisSupportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.clock = clock;
        this.redisSupportService = redisSupportService;
    }

    @Scheduled(fixedDelayString = "${work-order.sla.scan-interval-ms:300000}")
    @Transactional
    public void scanOpenWorkOrders() {
        Instant now = Instant.now(clock);
        List<SlaCandidate> candidates = jdbcTemplate.query(
                """
                SELECT id, title, status, creator_id, handler_id, department_id,
                       first_response_due_at, resolution_due_at, first_responded_at, resolved_at
                FROM work_orders
                WHERE status <> ? AND status <> ?
                """,
                (rs, rowNum) -> new SlaCandidate(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getLong("creator_id"),
                        (Long) rs.getObject("handler_id"),
                        (Long) rs.getObject("department_id"),
                        toInstant(rs.getTimestamp("first_response_due_at")),
                        toInstant(rs.getTimestamp("resolution_due_at")),
                        toInstant(rs.getTimestamp("first_responded_at")),
                        toInstant(rs.getTimestamp("resolved_at"))),
                COMPLETED_STATUS,
                CANCELLED_STATUS);
        for (SlaCandidate candidate : candidates) {
            evaluate(candidate, now);
        }
        evictStatisticsCache();
    }

    private void evaluate(SlaCandidate candidate, Instant now) {
        String worstStatus = "NORMAL";
        if (candidate.firstRespondedAt() == null && candidate.firstResponseDueAt() != null) {
            if (!now.isBefore(candidate.firstResponseDueAt())) {
                worstStatus = "FIRST_RESPONSE_OVERDUE";
                notifyOnce(candidate, "FIRST_RESPONSE_OVERDUE", "\u5de5\u5355\u9996\u6b21\u54cd\u5e94\u5df2\u8d85\u65f6");
            } else if (!now.isBefore(candidate.firstResponseDueAt().minus(FIRST_RESPONSE_NEAR_THRESHOLD))) {
                worstStatus = "NEAR_OVERDUE";
                notifyOnce(candidate, "FIRST_RESPONSE_NEAR_OVERDUE", "\u5de5\u5355\u5373\u5c06\u9996\u6b21\u54cd\u5e94\u8d85\u65f6");
            }
        }
        if (candidate.resolvedAt() == null && candidate.resolutionDueAt() != null) {
            if (!now.isBefore(candidate.resolutionDueAt())) {
                worstStatus = "RESOLUTION_OVERDUE";
                notifyOnce(candidate, "RESOLUTION_OVERDUE", "\u5de5\u5355\u89e3\u51b3\u5df2\u8d85\u65f6");
            } else if ("NORMAL".equals(worstStatus) && !now.isBefore(candidate.resolutionDueAt().minus(RESOLUTION_NEAR_THRESHOLD))) {
                worstStatus = "NEAR_OVERDUE";
                notifyOnce(candidate, "RESOLUTION_NEAR_OVERDUE", "\u5de5\u5355\u5373\u5c06\u89e3\u51b3\u8d85\u65f6");
            }
        }
        jdbcTemplate.update("UPDATE work_orders SET sla_status = ? WHERE id = ?", worstStatus, candidate.id());
    }

    private void notifyOnce(SlaCandidate candidate, String eventType, String title) {
        if (!notificationService.recordSlaEvent(candidate.id(), eventType)) {
            return;
        }
        List<Long> recipients = new ArrayList<>(notificationService.adminAndDepartmentAdminRecipients(candidate.departmentId()));
        recipients.add(candidate.handlerId());
        notificationService.notifyUsers(
                recipients,
                eventType.contains("NEAR") ? "SLA_NEAR_OVERDUE" : "SLA_OVERDUE",
                title,
                "\u5de5\u5355\u300a" + candidate.title() + "\u300b" + title,
                candidate.id(),
                null);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void evictStatisticsCache() {
        if (redisSupportService != null) {
            redisSupportService.evictStatistics();
        }
    }

    private record SlaCandidate(
            Long id,
            String title,
            String status,
            Long creatorId,
            Long handlerId,
            Long departmentId,
            Instant firstResponseDueAt,
            Instant resolutionDueAt,
            Instant firstRespondedAt,
            Instant resolvedAt) {
    }
}
