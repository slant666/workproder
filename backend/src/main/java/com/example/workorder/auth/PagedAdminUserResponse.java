package com.example.workorder.auth;

import java.util.List;

public record PagedAdminUserResponse(
        List<AdminUserResponse> items,
        long total,
        int page,
        int pageSize,
        int totalPages) {
}