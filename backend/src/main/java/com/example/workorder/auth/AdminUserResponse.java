package com.example.workorder.auth;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String username,
        String nickname,
        String role,
        boolean enabled,
        Long companyId,
        String companyName,
        Long departmentId,
        String departmentName,
        Long teamId,
        String teamName,
        boolean orgConfirmed,
        boolean departmentAdmin,
        Instant createdAt,
        Instant updatedAt) {

    public AdminUserResponse(
            Long id,
            String username,
            String nickname,
            String role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
        this(id, username, nickname, role, enabled, null, null, null, null, null, null, false, false, createdAt, updatedAt);
    }
}
