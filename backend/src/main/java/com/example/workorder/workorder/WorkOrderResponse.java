package com.example.workorder.workorder;

import java.time.Instant;

public record WorkOrderResponse(
        Long id,
        String title,
        String description,
        String type,
        String priority,
        String status,
        Long creatorId,
        String creatorUsername,
        Long handlerId,
        String handlerUsername,
        Instant createdAt) {

    public WorkOrderResponse(
            Long id,
            String title,
            String description,
            String type,
            String priority,
            String status,
            Long creatorId,
            String creatorUsername,
            Instant createdAt) {
        this(id, title, description, type, priority, status, creatorId, creatorUsername, null, null, createdAt);
    }
}
