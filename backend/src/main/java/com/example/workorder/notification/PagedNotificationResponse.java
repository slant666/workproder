package com.example.workorder.notification;

import java.util.List;

public record PagedNotificationResponse(
        List<NotificationResponse> items,
        long total,
        int page,
        int pageSize,
        int totalPages) {
}
