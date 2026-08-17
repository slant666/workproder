package com.example.workorder.organization;

public record CreateOrganizationRequest(String name, Long companyId, Long departmentId) {
}
