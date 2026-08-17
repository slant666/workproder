package com.example.workorder.auth;

import java.util.Set;

public record CurrentUser(
        Long id,
        String username,
        String nickname,
        String role,
        Set<String> roles,
        Set<String> permissions,
        Long companyId,
        String companyName,
        Long departmentId,
        String departmentName,
        Long teamId,
        String teamName,
        boolean orgConfirmed) {

    public CurrentUser(Long id, String username, String nickname, String role) {
        this(id, username, nickname, role, Set.of(role), defaultPermissions(role), 1L, "Default Company", id, "Default Department", null, null, true);
    }

    public CurrentUser(
            Long id,
            String username,
            String nickname,
            String role,
            Long companyId,
            String companyName,
            Long departmentId,
            String departmentName,
            Long teamId,
            String teamName,
            boolean orgConfirmed) {
        this(id, username, nickname, role, Set.of(role), defaultPermissions(role), companyId, companyName, departmentId, departmentName, teamId, teamName, orgConfirmed);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    private static Set<String> defaultPermissions(String role) {
        if ("CUSTOMER_SERVICE".equals(role)) {
            return Set.of(
                    RbacPermission.TICKET_VIEW,
                    RbacPermission.TICKET_COMMENT,
                    RbacPermission.TICKET_ATTACHMENT,
                    RbacPermission.TICKET_ACCEPT,
                    RbacPermission.TICKET_SUBMIT,
                    RbacPermission.TICKET_LOG_VIEW);
        }
        if ("DEPARTMENT_ADMIN".equals(role)) {
            return Set.of(
                    RbacPermission.TICKET_VIEW,
                    RbacPermission.TICKET_COMMENT,
                    RbacPermission.TICKET_ATTACHMENT,
                    RbacPermission.TICKET_ASSIGN,
                    RbacPermission.TICKET_ACCEPT,
                    RbacPermission.TICKET_SUBMIT,
                    RbacPermission.TICKET_RETURN,
                    RbacPermission.TICKET_LOG_VIEW,
                    RbacPermission.STATISTICS_VIEW);
        }
        if ("AUDITOR".equals(role)) {
            return Set.of(
                    RbacPermission.TICKET_VIEW,
                    RbacPermission.TICKET_LOG_VIEW,
                    RbacPermission.STATISTICS_VIEW);
        }
        if (Role.ADMIN.name().equals(role)) {
            return Set.of(
                    RbacPermission.TICKET_CREATE,
                    RbacPermission.TICKET_VIEW,
                    RbacPermission.TICKET_UPDATE,
                    RbacPermission.TICKET_CANCEL,
                    RbacPermission.TICKET_COMMENT,
                    RbacPermission.TICKET_ATTACHMENT,
                    RbacPermission.TICKET_ASSIGN,
                    RbacPermission.TICKET_ACCEPT,
                    RbacPermission.TICKET_SUBMIT,
                    RbacPermission.TICKET_RETURN,
                    RbacPermission.TICKET_CONFIRM,
                    RbacPermission.TICKET_LOG_VIEW,
                    RbacPermission.USER_VIEW,
                    RbacPermission.USER_UPDATE,
                    RbacPermission.USER_DISABLE,
                    RbacPermission.ORGANIZATION_MANAGE,
                    RbacPermission.STATISTICS_VIEW);
        }
        return Set.of(
                RbacPermission.TICKET_CREATE,
                RbacPermission.TICKET_VIEW,
                RbacPermission.TICKET_UPDATE,
                RbacPermission.TICKET_CANCEL,
                RbacPermission.TICKET_COMMENT,
                RbacPermission.TICKET_ATTACHMENT,
                RbacPermission.TICKET_LOG_VIEW,
                RbacPermission.TICKET_CONFIRM);
    }
}
