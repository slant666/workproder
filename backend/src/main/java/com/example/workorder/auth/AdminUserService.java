package com.example.workorder.auth;

import com.example.workorder.organization.OrganizationService;
import com.example.workorder.realtime.RealtimeEventPublisher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final OrganizationService organizationService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Autowired
    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            OrganizationService organizationService,
            ObjectProvider<RealtimeEventPublisher> realtimeEventPublisherProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.organizationService = organizationService;
        this.realtimeEventPublisher = realtimeEventPublisherProvider.getIfAvailable();
    }

    public AdminUserService(JdbcTemplate jdbcTemplate, OrganizationService organizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.organizationService = organizationService;
        this.realtimeEventPublisher = null;
    }

    public AdminUserService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new OrganizationService(jdbcTemplate));
    }

    public PagedAdminUserResponse list(AdminUserListQuery query) {
        NormalizedListQuery normalized = normalize(query);
        List<Object> params = new ArrayList<>();
        String where = buildWhere(normalized.keyword(), params);

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users u" + where,
                Long.class,
                params.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalized.pageSize());
        int offset = (normalized.page() - 1) * normalized.pageSize();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(normalized.pageSize());
        pageParams.add(offset);
        List<AdminUserResponse> items = jdbcTemplate.query(
                adminUserSelect() + where + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
                this::mapUser,
                pageParams.toArray());
        return new PagedAdminUserResponse(items, total, normalized.page(), normalized.pageSize(), totalPages);
    }

    @Transactional
    public AdminUserResponse updateEnabled(Long targetUserId, UpdateUserEnabledRequest request, CurrentUser actor) {
        if (request == null || request.enabled() == null) {
            throw new AdminUserException("启用状态不能为空");
        }
        AdminUserResponse existing = findById(targetUserId);
        if (actor.id().equals(existing.id()) && !request.enabled()) {
            throw new AdminUserException("管理员不能禁用当前登录的自己");
        }
        if (existing.enabled() == request.enabled()) {
            return existing;
        }

        int updated = jdbcTemplate.update("UPDATE users SET enabled = ? WHERE id = ?", request.enabled(), existing.id());
        if (updated != 1) {
            throw new AdminUserException("用户状态更新失败");
        }
        recordAudit(actor, existing.id(), "user_enabled_update", "enabled", String.valueOf(existing.enabled()), String.valueOf(request.enabled()));
        publishAuthContextChanged(existing.id(), "enabled");
        return findById(existing.id());
    }

    @Transactional
    public AdminUserResponse updateRole(Long targetUserId, UpdateUserRoleRequest request, CurrentUser actor) {
        String nextRole = normalizeRole(request == null ? null : request.role());
        AdminUserResponse existing = findById(targetUserId);
        if (actor.id().equals(existing.id()) && Role.USER.name().equals(nextRole)) {
            throw new AdminUserException("管理员不能降级当前登录的自己");
        }
        if (existing.role().equals(nextRole)) {
            return existing;
        }

        int updated = jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", nextRole, existing.id());
        if (updated != 1) {
            throw new AdminUserException("用户角色更新失败");
        }
        syncPrimaryRole(existing.id(), nextRole);
        recordAudit(actor, existing.id(), "user_role_update", "role", existing.role(), nextRole);
        publishAuthContextChanged(existing.id(), "role");
        return findById(existing.id());
    }

    @Transactional
    public AdminUserResponse updateOrganization(Long targetUserId, UpdateUserOrganizationRequest request, CurrentUser actor) {
        AdminUserResponse existing = findById(targetUserId);
        Long companyId = request == null ? null : request.companyId();
        Long departmentId = request == null ? null : request.departmentId();
        Long teamId = request == null ? null : request.teamId();
        boolean confirmed = Boolean.TRUE.equals(request == null ? null : request.orgConfirmed());
        if (confirmed && departmentId == null) {
            throw new AdminUserException("确认组织归属前必须选择部门");
        }
        organizationService.validateOrganization(companyId, departmentId, teamId, confirmed);
        int updated = jdbcTemplate.update(
                "UPDATE users SET company_id = ?, department_id = ?, team_id = ?, org_confirmed = ? WHERE id = ?",
                companyId,
                departmentId,
                teamId,
                confirmed,
                existing.id());
        if (updated != 1) {
            throw new AdminUserException("用户组织归属更新失败");
        }
        recordAudit(actor, existing.id(), "user_org_update", "organization", orgValue(existing), orgValue(companyId, departmentId, teamId, confirmed));
        publishAuthContextChanged(existing.id(), "organization");
        return findById(existing.id());
    }

    @Transactional
    public AdminUserResponse updateDepartmentAdmin(Long targetUserId, UpdateDepartmentAdminRequest request, CurrentUser actor) {
        AdminUserResponse existing = findById(targetUserId);
        Long departmentId = request == null ? null : request.departmentId();
        if (departmentId == null || departmentId < 1) {
            throw new AdminUserException("部门不能为空");
        }
        organizationService.companyIdByDepartment(departmentId);
        boolean enabled = Boolean.TRUE.equals(request.departmentAdmin());
        if (enabled && (!existing.enabled() || existing.departmentId() == null || !departmentId.equals(existing.departmentId()) || !existing.orgConfirmed())) {
            throw new AdminUserException("只能授权已确认归属该部门的启用用户");
        }
        if (enabled) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO department_admins (user_id, department_id) VALUES (?, ?)",
                        existing.id(),
                        departmentId);
            } catch (DuplicateKeyException ignored) {
            }
            addUserRole(existing.id(), "DEPARTMENT_ADMIN");
        } else {
            jdbcTemplate.update(
                    "DELETE FROM department_admins WHERE user_id = ? AND department_id = ?",
                    existing.id(),
                    departmentId);
            removeUserRole(existing.id(), "DEPARTMENT_ADMIN");
        }
        recordAudit(actor, existing.id(), "department_admin_update", "department_admin", null, String.valueOf(enabled));
        publishAuthContextChanged(existing.id(), "department_admin");
        return findById(existing.id());
    }

    private AdminUserResponse findById(Long id) {
        if (id == null || id < 1) {
            throw new AdminUserException("用户不存在");
        }
        try {
            return jdbcTemplate.queryForObject(adminUserSelect() + " WHERE u.id = ?", this::mapUser, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new AdminUserException("用户不存在");
        }
    }

    private String adminUserSelect() {
        return """
                SELECT u.id, u.username, u.nickname, u.role, u.enabled,
                       u.company_id, c.name AS company_name,
                       u.department_id, d.name AS department_name,
                       u.team_id, t.name AS team_name,
                       u.org_confirmed, u.created_at, u.updated_at,
                       EXISTS (
                           SELECT 1 FROM department_admins da
                           WHERE da.user_id = u.id
                             AND da.department_id = u.department_id
                       ) AS department_admin
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                LEFT JOIN departments d ON d.id = u.department_id
                LEFT JOIN teams t ON t.id = u.team_id
                """;
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new AdminUserException("角色不能为空");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        try {
            return Role.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new AdminUserException("角色只能是 USER 或 ADMIN");
        }
    }

    private void syncPrimaryRole(Long userId, String role) {
        try {
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", userId, role);
        } catch (RuntimeException ignored) {
            // Older test schemas may not include RBAC tables yet; users.role remains authoritative there.
        }
    }

    private void addUserRole(Long userId, String role) {
        try {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", userId, role);
        } catch (DuplicateKeyException ignored) {
        } catch (RuntimeException ignored) {
            // Older test schemas may not include RBAC tables yet.
        }
    }

    private void removeUserRole(Long userId, String role) {
        try {
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ? AND role_code = ?", userId, role);
        } catch (RuntimeException ignored) {
            // Older test schemas may not include RBAC tables yet.
        }
    }

    private NormalizedListQuery normalize(AdminUserListQuery query) {
        String keyword = query == null || query.keyword() == null ? "" : query.keyword().trim();
        int page = query == null || query.page() == null || query.page() < 1 ? DEFAULT_PAGE : query.page();
        int pageSize = query == null || query.pageSize() == null || query.pageSize() < 1 ? DEFAULT_PAGE_SIZE : query.pageSize();
        return new NormalizedListQuery(keyword, page, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    private String buildWhere(String keyword, List<Object> params) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        params.add(like);
        params.add(like);
        return " WHERE LOWER(u.username) LIKE ? OR LOWER(u.nickname) LIKE ?";
    }

    private AdminUserResponse mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUserResponse(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("role"),
                rs.getBoolean("enabled"),
                (Long) rs.getObject("company_id"),
                rs.getString("company_name"),
                (Long) rs.getObject("department_id"),
                rs.getString("department_name"),
                (Long) rs.getObject("team_id"),
                rs.getString("team_name"),
                rs.getBoolean("org_confirmed"),
                rs.getBoolean("department_admin"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String orgValue(AdminUserResponse user) {
        return orgValue(user.companyId(), user.departmentId(), user.teamId(), user.orgConfirmed());
    }

    private String orgValue(Long companyId, Long departmentId, Long teamId, boolean confirmed) {
        return "company=" + companyId + ",department=" + departmentId + ",team=" + teamId + ",confirmed=" + confirmed;
    }

    private void recordAudit(CurrentUser actor, Long targetUserId, String action, String fieldName, String oldValue, String newValue) {
        jdbcTemplate.update(
                """
                INSERT INTO user_management_audit_logs
                    (actor_id, target_user_id, action, field_name, old_value, new_value, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                actor.id(),
                targetUserId,
                action,
                fieldName,
                oldValue,
                newValue,
                null);
    }

    private void publishAuthContextChanged(Long userId, String reason) {
        if (realtimeEventPublisher != null) {
            realtimeEventPublisher.notifyUser(userId, "AUTH_CONTEXT_CHANGED", null, null, null, Map.of("reason", reason));
        }
    }

    private record NormalizedListQuery(String keyword, int page, int pageSize) {
    }
}
