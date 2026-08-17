package com.example.workorder.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        Long workOrderId,
        boolean read,
        Instant readAt,
        Instant createdAt) {
}
