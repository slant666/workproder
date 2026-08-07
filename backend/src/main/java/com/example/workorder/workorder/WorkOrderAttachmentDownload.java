package com.example.workorder.workorder;

import org.springframework.core.io.Resource;

public record WorkOrderAttachmentDownload(
        WorkOrderAttachmentResponse attachment,
        Resource resource) {
}
