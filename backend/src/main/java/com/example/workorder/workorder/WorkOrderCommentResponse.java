package com.example.workorder.workorder;

import java.time.Instant;

public record WorkOrderCommentResponse(
        Long id,
        Long workOrderId,
        Long authorId,
        String authorUsername,
        String authorNickname,
        String authorRole,
        String content,
        Instant createdAt) {
}
