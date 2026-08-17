package com.example.workorder.organization;

public record OrganizationResponse(
        Long id,
        String name,
        Boolean enabled,
        Long companyId,
        String companyName,
        Long departmentId,
        String departmentName) {
}
