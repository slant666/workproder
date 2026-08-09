package com.example.workorder.auth;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String username,
        String nickname,
        String role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}