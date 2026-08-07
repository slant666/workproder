package com.example.workorder.workorder;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.attachments")
public record WorkOrderAttachmentProperties(
        String uploadDir,
        long maxSizeBytes) {

    public WorkOrderAttachmentProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "uploads/work-order-attachments";
        }
        if (maxSizeBytes < 1) {
            maxSizeBytes = 10 * 1024 * 1024;
        }
    }
}
