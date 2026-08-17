package com.example.workorder.workorder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.attachments")
public record WorkOrderAttachmentProperties(
        String uploadDir,
        long maxSizeBytes,
        String storageProvider,
        Minio minio) {

    @ConstructorBinding
    public WorkOrderAttachmentProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "uploads/work-order-attachments";
        }
        if (maxSizeBytes < 1) {
            maxSizeBytes = 10 * 1024 * 1024;
        }
        if (storageProvider == null || storageProvider.isBlank()) {
            storageProvider = "local";
        }
        if (minio == null) {
            minio = new Minio(null, null, null, null, null, null);
        }
    }

    public WorkOrderAttachmentProperties(String uploadDir, long maxSizeBytes) {
        this(uploadDir, maxSizeBytes, "local", new Minio(null, null, null, null, null, null));
    }

    public record Minio(
            String endpoint,
            String externalEndpoint,
            String accessKey,
            String secretKey,
            String bucket,
            String region) {

        @ConstructorBinding
        public Minio {
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "http://localhost:9000";
            }
            if (externalEndpoint == null || externalEndpoint.isBlank()) {
                externalEndpoint = endpoint;
            }
            if (accessKey == null || accessKey.isBlank()) {
                accessKey = "workorder";
            }
            if (secretKey == null || secretKey.isBlank()) {
                secretKey = "workorder-secret";
            }
            if (bucket == null || bucket.isBlank()) {
                bucket = "work-order-attachments";
            }
            if (region == null || region.isBlank()) {
                region = "us-east-1";
            }
        }
    }
}
