package com.example.workorder.excel;

import java.time.Instant;

public record FileJobResponse(
        Long id,
        String type,
        String status,
        String originalFilename,
        Integer totalCount,
        Integer successCount,
        Integer failedCount,
        boolean hasResultFile,
        boolean hasErrorReport,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt) {
}
