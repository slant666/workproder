package com.example.workorder.auth;

public record UpdateUserOrganizationRequest(
        Long companyId,
        Long departmentId,
        Long teamId,
        Boolean orgConfirmed) {
}
