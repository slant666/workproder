package com.example.workorder.workorder;

import com.example.workorder.redis.RedisSupportService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderStatisticsService {

    private static final String PENDING_STATUS = "\u5f85\u5904\u7406";
    private static final String PROCESSING_STATUS = "\u5904\u7406\u4e2d";
    private static final String WAITING_CONFIRMATION_STATUS = "\u5f85\u786e\u8ba4";
    private static final String COMPLETED_STATUS = "\u5df2\u5b8c\u6210";
    private static final String CANCELLED_STATUS = "\u5df2\u53d6\u6d88";
    private static final List<String> STATUS_ORDER = List.of(
            PENDING_STATUS, PROCESSING_STATUS, WAITING_CONFIRMATION_STATUS, COMPLETED_STATUS, CANCELLED_STATUS);
    private static final List<String> PRIORITY_ORDER = List.of("\u4f4e", "\u4e2d", "\u9ad8");
    private static final Duration OVERDUE_THRESHOLD = Duration.ofHours(48);
    private static final String AVERAGE_PROCESSING_RULE = "\u9996\u6b21\u63a5\u5355\u5230\u7528\u6237\u786e\u8ba4\u5b8c\u6210";
    private static final String OVERDUE_RULE = "\u72b6\u6001\u4e3a\u5f85\u5904\u7406\u4e14\u521b\u5efa\u65f6\u95f4\u8d85\u8fc7 48 \u5c0f\u65f6";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final RedisSupportService redisSupportService;
    private Boolean slaColumnsAvailable;

    public WorkOrderStatisticsService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC(), null);
    }

    @Autowired
    public WorkOrderStatisticsService(JdbcTemplate jdbcTemplate, RedisSupportService redisSupportService) {
        this(jdbcTemplate, Clock.systemUTC(), redisSupportService);
    }

    WorkOrderStatisticsService(JdbcTemplate jdbcTemplate, Clock clock) {
        this(jdbcTemplate, clock, null);
    }

    WorkOrderStatisticsService(JdbcTemplate jdbcTemplate, Clock clock, RedisSupportService redisSupportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.redisSupportService = redisSupportService;
    }

    public WorkOrderStatisticsResponse dashboard(WorkOrderStatisticsQuery query) {
        NormalizedStatisticsQuery normalized = normalize(query);
        WorkOrderStatisticsQuery normalizedQuery = new WorkOrderStatisticsQuery(
                normalized.createdFromText(),
                normalized.createdToText());
        if (redisSupportService != null) {
            WorkOrderStatisticsResponse cached = redisSupportService.getStatistics(normalizedQuery);
            if (cached != null) {
                return cached;
            }
        }
        List<Object> params = new ArrayList<>();
        String where = buildCreatedAtWhere(normalized, params);

        WorkOrderStatisticsResponse response = new WorkOrderStatisticsResponse(
                total(where, params),
                groupedCounts("status", STATUS_ORDER, where, params),
                groupedCounts("priority", PRIORITY_ORDER, where, params),
                dailyNewCounts(where, params),
                averageProcessingMinutes(where, params),
                adminProcessingCounts(where, params),
                overdueUnhandledCount(where, params),
                slaStatusCount("NEAR_OVERDUE", where, params),
                slaStatusCount("FIRST_RESPONSE_OVERDUE", where, params),
                slaStatusCount("RESOLUTION_OVERDUE", where, params),
                slaOverduePriorityCounts(where, params),
                AVERAGE_PROCESSING_RULE,
                OVERDUE_RULE);
        if (redisSupportService != null) {
            redisSupportService.putStatistics(normalizedQuery, response);
        }
        return response;
    }

    private long total(String where, List<Object> params) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_orders wo" + where, Long.class, params.toArray());
        return total == null ? 0 : total;
    }

    private List<WorkOrderCountResponse> groupedCounts(
            String column,
            List<String> orderedLabels,
            String where,
            List<Object> params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : orderedLabels) {
            counts.put(label, 0L);
        }
        jdbcTemplate.query(
                "SELECT wo." + column + " AS label, COUNT(*) AS count FROM work_orders wo"
                        + where + " GROUP BY wo." + column,
                (RowCallbackHandler) rs -> {
                    counts.put(rs.getString("label"), rs.getLong("count"));
                },
                params.toArray());
        return counts.entrySet().stream()
                .map(entry -> new WorkOrderCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DailyWorkOrderCountResponse> dailyNewCounts(String where, List<Object> params) {
        return jdbcTemplate.query(
                """
                SELECT DATE(wo.created_at) AS created_date, COUNT(*) AS count
                FROM work_orders wo
                """ + where + " GROUP BY DATE(wo.created_at) ORDER BY DATE(wo.created_at) ASC",
                (rs, rowNum) -> new DailyWorkOrderCountResponse(
                        rs.getObject("created_date", LocalDate.class),
                        rs.getLong("count")),
                params.toArray());
    }

    private long averageProcessingMinutes(String where, List<Object> params) {
        List<ProcessingWindow> windows = jdbcTemplate.query(
                """
                SELECT accepted.accepted_at, completed.completed_at
                FROM work_orders wo
                JOIN (
                    SELECT work_order_id, MIN(created_at) AS accepted_at
                    FROM work_order_status_transitions
                    WHERE new_status = ? AND action = 'accept'
                    GROUP BY work_order_id
                ) accepted ON accepted.work_order_id = wo.id
                JOIN (
                    SELECT work_order_id, MIN(created_at) AS completed_at
                    FROM work_order_status_transitions
                    WHERE new_status = ?
                    GROUP BY work_order_id
                ) completed ON completed.work_order_id = wo.id
                """ + where,
                (rs, rowNum) -> new ProcessingWindow(
                        rs.getTimestamp("accepted_at").toInstant(),
                        rs.getTimestamp("completed_at").toInstant()),
                withLeadingParams(params, PROCESSING_STATUS, COMPLETED_STATUS).toArray());
        List<Long> minutes = windows.stream()
                .map(window -> Duration.between(window.acceptedAt(), window.completedAt()).toMinutes())
                .filter(value -> value >= 0)
                .toList();
        if (minutes.isEmpty()) {
            return 0;
        }
        long totalMinutes = minutes.stream().mapToLong(Long::longValue).sum();
        return Math.round((double) totalMinutes / minutes.size());
    }

    private List<AdminWorkOrderCountResponse> adminProcessingCounts(String where, List<Object> params) {
        return jdbcTemplate.query(
                """
                SELECT u.id AS handler_id, u.username AS handler_username, u.nickname AS handler_nickname, COUNT(*) AS count
                FROM work_orders wo
                JOIN users u ON u.id = wo.handler_id
                """ + where + " GROUP BY u.id, u.username, u.nickname ORDER BY count DESC, u.username ASC, u.id ASC",
                (rs, rowNum) -> new AdminWorkOrderCountResponse(
                        rs.getLong("handler_id"),
                        rs.getString("handler_username"),
                        rs.getString("handler_nickname"),
                        rs.getLong("count")),
                params.toArray());
    }

    private long overdueUnhandledCount(String where, List<Object> params) {
        List<Object> allParams = new ArrayList<>(params);
        allParams.add(PENDING_STATUS);
        allParams.add(Timestamp.from(Instant.now(clock).minus(OVERDUE_THRESHOLD)));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_orders wo" + where
                        + (where.isBlank() ? " WHERE" : " AND")
                        + " wo.status = ? AND wo.created_at < ?",
                Long.class,
                allParams.toArray());
        return count == null ? 0 : count;
    }

    private long slaStatusCount(String slaStatus, String where, List<Object> params) {
        if (!hasSlaColumns()) {
            return 0;
        }
        List<Object> allParams = new ArrayList<>(params);
        allParams.add(slaStatus);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_orders wo" + where
                        + (where.isBlank() ? " WHERE" : " AND")
                        + " wo.sla_status = ?",
                Long.class,
                allParams.toArray());
        return count == null ? 0 : count;
    }

    private List<WorkOrderCountResponse> slaOverduePriorityCounts(String where, List<Object> params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : PRIORITY_ORDER) {
            counts.put(label, 0L);
        }
        if (!hasSlaColumns()) {
            return counts.entrySet().stream()
                    .map(entry -> new WorkOrderCountResponse(entry.getKey(), entry.getValue()))
                    .toList();
        }
        jdbcTemplate.query(
                "SELECT wo.priority AS label, COUNT(*) AS count FROM work_orders wo"
                        + where
                        + (where.isBlank() ? " WHERE" : " AND")
                        + " wo.sla_status IN ('FIRST_RESPONSE_OVERDUE', 'RESOLUTION_OVERDUE') GROUP BY wo.priority",
                (RowCallbackHandler) rs -> counts.put(rs.getString("label"), rs.getLong("count")),
                params.toArray());
        return counts.entrySet().stream()
                .map(entry -> new WorkOrderCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean hasSlaColumns() {
        if (slaColumnsAvailable != null) {
            return slaColumnsAvailable;
        }
        try {
            jdbcTemplate.queryForList("SELECT sla_status FROM work_orders WHERE 1 = 0");
            slaColumnsAvailable = true;
        } catch (RuntimeException ex) {
            slaColumnsAvailable = false;
        }
        return slaColumnsAvailable;
    }

    private String buildCreatedAtWhere(NormalizedStatisticsQuery query, List<Object> params) {
        List<String> clauses = new ArrayList<>();
        if (query.createdFrom() != null) {
            clauses.add("wo.created_at >= ?");
            params.add(Timestamp.from(query.createdFrom()));
        }
        if (query.createdToExclusive() != null) {
            clauses.add("wo.created_at < ?");
            params.add(Timestamp.from(query.createdToExclusive()));
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private NormalizedStatisticsQuery normalize(WorkOrderStatisticsQuery query) {
        String createdFromText = blankToNull(query == null ? null : query.createdFrom());
        String createdToText = blankToNull(query == null ? null : query.createdTo());
        Instant createdFrom = parseDate(createdFromText, "\u5f00\u59cb\u65e5\u671f\u683c\u5f0f\u4e0d\u6b63\u786e");
        Instant createdToExclusive = parseDate(createdToText, "\u7ed3\u675f\u65e5\u671f\u683c\u5f0f\u4e0d\u6b63\u786e");
        if (createdToExclusive != null) {
            createdToExclusive = createdToExclusive.plus(Duration.ofDays(1));
        }
        if (createdFrom != null && createdToExclusive != null && !createdFrom.isBefore(createdToExclusive)) {
            throw new WorkOrderException("\u5f00\u59cb\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u7ed3\u675f\u65e5\u671f");
        }
        return new NormalizedStatisticsQuery(createdFrom, createdToExclusive, createdFromText, createdToText);
    }

    private Instant parseDate(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new WorkOrderException(message);
        }
    }

    private List<Object> withLeadingParams(List<Object> params, Object... leadingParams) {
        List<Object> allParams = new ArrayList<>();
        allParams.addAll(List.of(leadingParams));
        allParams.addAll(params);
        return allParams;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedStatisticsQuery(
            Instant createdFrom,
            Instant createdToExclusive,
            String createdFromText,
            String createdToText) {
    }

    private record ProcessingWindow(Instant acceptedAt, Instant completedAt) {
    }
}
