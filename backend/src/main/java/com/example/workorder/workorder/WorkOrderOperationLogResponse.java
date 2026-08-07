package com.example.workorder.workorder;

import java.time.Instant;

public record WorkOrderOperationLogResponse(
        Long id,
        Long workOrderId,
        Long actorId,
        String actorUsername,
        String actorNickname,
        String action,
        String fieldName,
        String oldValue,
        String newValue,
        String detailsJson,
        Instant createdAt) {
}
