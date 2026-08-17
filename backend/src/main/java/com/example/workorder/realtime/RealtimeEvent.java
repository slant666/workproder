package com.example.workorder.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public record RealtimeEvent(
        long eventId,
        String type,
        Long entityId,
        Long notificationId,
        Long unreadCount,
        Instant occurredAt,
        Map<String, Object> payload) {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    public static RealtimeEvent of(String type, Long entityId, Long notificationId, Long unreadCount, Map<String, Object> payload) {
        return new RealtimeEvent(SEQUENCE.incrementAndGet(), type, entityId, notificationId, unreadCount, Instant.now(), payload == null ? Map.of() : payload);
    }
}
