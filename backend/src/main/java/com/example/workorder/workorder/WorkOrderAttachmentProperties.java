package com.example.workorder.workorder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.attachments")
public record WorkOrderAttachmentProperties(
        String uploadDir,
        long maxSizeBytes) {

    @ConstructorBinding
    public WorkOrderAttachmentProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "uploads/work-order-attachments";
        }
        if (maxSizeBytes < 1) {
            maxSizeBytes = 10 * 1024 * 1024;
        }
    }
}
