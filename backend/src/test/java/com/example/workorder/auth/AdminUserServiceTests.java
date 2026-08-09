package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AdminUserServiceTests {

    private JdbcTemplate jdbcTemplate;
    private AdminUserService service;
    private final CurrentUser admin = new CurrentUser(1L, "admin", "Admin", "ADMIN");

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:admin_users;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_management_audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_management_audit_logs (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    actor_id BIGINT NOT NULL,
                    target_user_id BIGINT NOT NULL,
                    action VARCHAR(60) NOT NULL,
                    field_name VARCHAR(60) NOT NULL,
                    old_value TEXT NULL,
                    new_value TEXT NULL,
                    details_json TEXT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        service = new AdminUserService(jdbcTemplate);
        createUser("admin", "Admin", "ADMIN", true);
        createUser("demo", "演示用户", "USER", true);
        createUser("disabled", "禁用用户", "USER", false);
    }

    @Test
    void listsUsersWithKeywordAndPagination() {
        PagedAdminUserResponse response = service.list(new AdminUserListQuery("演示", 1, 1));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(1);
        assertThat(response.items()).extracting(AdminUserResponse::username).containsExactly("demo");
    }

    @Test
    void normalizesPageBoundaries() {
        PagedAdminUserResponse response = service.list(new AdminUserListQuery(null, 0, 100));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(50);
        assertThat(response.total()).isEqualTo(3);
    }

    @Test
    void adminCanDisableAndEnableOtherUserWithAuditLog() {
        AdminUserResponse disabled = service.updateEnabled(2L, new UpdateUserEnabledRequest(false), admin);
        AdminUserResponse enabled = service.updateEnabled(2L, new UpdateUserEnabledRequest(true), admin);

        assertThat(disabled.enabled()).isFalse();
        assertThat(enabled.enabled()).isTrue();
        assertThat(countAuditRows("user_enabled_update")).isEqualTo(2);
    }

    @Test
    void adminCannotDisableSelf() {
        assertThatThrownBy(() -> service.updateEnabled(1L, new UpdateUserEnabledRequest(false), admin))
                .isInstanceOf(AdminUserException.class)
                .hasMessage("管理员不能禁用当前登录的自己");
    }

    @Test
    void adminCanPromoteUserAndCannotDemoteSelf() {
        AdminUserResponse promoted = service.updateRole(2L, new UpdateUserRoleRequest("ADMIN"), admin);

        assertThat(promoted.role()).isEqualTo("ADMIN");
        assertThat(countAuditRows("user_role_update")).isEqualTo(1);
        assertThatThrownBy(() -> service.updateRole(1L, new UpdateUserRoleRequest("USER"), admin))
                .isInstanceOf(AdminUserException.class)
                .hasMessage("管理员不能降级当前登录的自己");
    }

    @Test
    void rejectsInvalidRole() {
        assertThatThrownBy(() -> service.updateRole(2L, new UpdateUserRoleRequest("OWNER"), admin))
                .isInstanceOf(AdminUserException.class)
                .hasMessage("角色只能是 USER 或 ADMIN");
    }

    @Test
    void unchangedStateDoesNotWriteAuditLog() {
        AdminUserResponse existing = service.updateEnabled(2L, new UpdateUserEnabledRequest(true), admin);

        assertThat(existing.enabled()).isTrue();
        assertThat(countAuditRows("user_enabled_update")).isZero();
    }

    private void createUser(String username, String nickname, String role, boolean enabled) {
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, 'hash', ?, ?)",
                username,
                nickname,
                role,
                enabled);
    }

    private Integer countAuditRows(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_management_audit_logs WHERE action = ?",
                Integer.class,
                action);
    }
}