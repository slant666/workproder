package com.example.workorder.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;

    public AdminUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PagedAdminUserResponse list(AdminUserListQuery query) {
        NormalizedListQuery normalized = normalize(query);
        List<Object> params = new ArrayList<>();
        String where = buildWhere(normalized.keyword(), params);

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users" + where,
                Long.class,
                params.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalized.pageSize());
        int offset = (normalized.page() - 1) * normalized.pageSize();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(normalized.pageSize());
        pageParams.add(offset);
        List<AdminUserResponse> items = jdbcTemplate.query(
                """
                SELECT id, username, nickname, role, enabled, created_at, updated_at
                FROM users
                """ + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
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

        int updated = jdbcTemplate.update(
                "UPDATE users SET enabled = ? WHERE id = ?",
                request.enabled(),
                existing.id());
        if (updated != 1) {
            throw new AdminUserException("用户状态更新失败");
        }
        recordAudit(actor, existing.id(), "user_enabled_update", "enabled", String.valueOf(existing.enabled()), String.valueOf(request.enabled()));
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

        int updated = jdbcTemplate.update(
                "UPDATE users SET role = ? WHERE id = ?",
                nextRole,
                existing.id());
        if (updated != 1) {
            throw new AdminUserException("用户角色更新失败");
        }
        recordAudit(actor, existing.id(), "user_role_update", "role", existing.role(), nextRole);
        return findById(existing.id());
    }

    private AdminUserResponse findById(Long id) {
        if (id == null || id < 1) {
            throw new AdminUserException("用户不存在");
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, username, nickname, role, enabled, created_at, updated_at
                    FROM users
                    WHERE id = ?
                    """,
                    this::mapUser,
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new AdminUserException("用户不存在");
        }
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
        return " WHERE LOWER(username) LIKE ? OR LOWER(nickname) LIKE ?";
    }

    private AdminUserResponse mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUserResponse(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("role"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private void recordAudit(
            CurrentUser actor,
            Long targetUserId,
            String action,
            String fieldName,
            String oldValue,
            String newValue) {
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

    private record NormalizedListQuery(String keyword, int page, int pageSize) {
    }
}