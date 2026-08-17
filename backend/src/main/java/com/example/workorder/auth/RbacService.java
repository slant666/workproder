package com.example.workorder.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RbacService {

    private final JdbcTemplate jdbcTemplate;

    public RbacService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> permissionsForUser(Long userId, String legacyRole) {
        Set<String> permissions = new LinkedHashSet<>();
        if (userId != null && hasRbacTables()) {
            List<String> rows = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT rp.permission_code
                    FROM user_roles ur
                    JOIN roles r ON r.code = ur.role_code AND r.enabled = TRUE
                    JOIN role_permissions rp ON rp.role_code = r.code
                    WHERE ur.user_id = ?
                    ORDER BY rp.permission_code
                    """,
                    String.class,
                    userId);
            permissions.addAll(rows);
        }
        if (permissions.isEmpty()) {
            permissions.addAll(defaultPermissions(legacyRole));
        }
        return permissions;
    }

    public Set<String> rolesForUser(Long userId, String legacyRole) {
        Set<String> roles = new LinkedHashSet<>();
        if (userId != null && hasRbacTables()) {
            roles.addAll(jdbcTemplate.queryForList(
                    "SELECT role_code FROM user_roles WHERE user_id = ? ORDER BY role_code",
                    String.class,
                    userId));
        }
        if (roles.isEmpty() && legacyRole != null) {
            roles.add(legacyRole);
        }
        return roles;
    }

    private boolean hasRbacTables() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Long.class);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Set<String> defaultPermissions(String legacyRole) {
        Set<String> permissions = new LinkedHashSet<>();
        permissions.add(RbacPermission.TICKET_VIEW);
        permissions.add(RbacPermission.TICKET_CREATE);
        permissions.add(RbacPermission.TICKET_UPDATE);
        permissions.add(RbacPermission.TICKET_CANCEL);
        permissions.add(RbacPermission.TICKET_COMMENT);
        permissions.add(RbacPermission.TICKET_ATTACHMENT);
        permissions.add(RbacPermission.TICKET_LOG_VIEW);
        permissions.add(RbacPermission.TICKET_CONFIRM);
        if ("CUSTOMER_SERVICE".equals(legacyRole)) {
            permissions.remove(RbacPermission.TICKET_CREATE);
            permissions.remove(RbacPermission.TICKET_UPDATE);
            permissions.remove(RbacPermission.TICKET_CANCEL);
            permissions.remove(RbacPermission.TICKET_CONFIRM);
            permissions.add(RbacPermission.TICKET_ACCEPT);
            permissions.add(RbacPermission.TICKET_SUBMIT);
        }
        if ("DEPARTMENT_ADMIN".equals(legacyRole)) {
            permissions.remove(RbacPermission.TICKET_CREATE);
            permissions.remove(RbacPermission.TICKET_UPDATE);
            permissions.remove(RbacPermission.TICKET_CANCEL);
            permissions.remove(RbacPermission.TICKET_CONFIRM);
            permissions.add(RbacPermission.TICKET_ASSIGN);
            permissions.add(RbacPermission.TICKET_ACCEPT);
            permissions.add(RbacPermission.TICKET_SUBMIT);
            permissions.add(RbacPermission.TICKET_RETURN);
            permissions.add(RbacPermission.STATISTICS_VIEW);
        }
        if ("AUDITOR".equals(legacyRole)) {
            permissions.clear();
            permissions.add(RbacPermission.TICKET_VIEW);
            permissions.add(RbacPermission.TICKET_LOG_VIEW);
            permissions.add(RbacPermission.STATISTICS_VIEW);
        }
        if (Role.ADMIN.name().equals(legacyRole)) {
            permissions.add(RbacPermission.TICKET_ASSIGN);
            permissions.add(RbacPermission.TICKET_ACCEPT);
            permissions.add(RbacPermission.TICKET_SUBMIT);
            permissions.add(RbacPermission.TICKET_RETURN);
            permissions.add(RbacPermission.TICKET_LOG_VIEW);
            permissions.add(RbacPermission.USER_VIEW);
            permissions.add(RbacPermission.USER_UPDATE);
            permissions.add(RbacPermission.USER_DISABLE);
            permissions.add(RbacPermission.ORGANIZATION_MANAGE);
            permissions.add(RbacPermission.STATISTICS_VIEW);
        }
        return permissions;
    }
}
