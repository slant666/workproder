package com.example.workorder.workorder;

import java.time.Instant;

public record WorkOrderAttachmentResponse(
        Long id,
        Long workOrderId,
        Long uploaderId,
        String uploaderUsername,
        String uploaderNickname,
        String originalFilename,
        String contentType,
        Long fileSize,
        Instant createdAt) {
}
