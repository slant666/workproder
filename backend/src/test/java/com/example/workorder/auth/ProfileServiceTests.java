package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProfileServiceTests {

    private JdbcTemplate jdbcTemplate;
    private PasswordEncoder passwordEncoder;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:profile;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("DROP TABLE IF EXISTS teams");
        jdbcTemplate.execute("DROP TABLE IF EXISTS departments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS companies");
        jdbcTemplate.execute("CREATE TABLE companies (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("CREATE TABLE departments (id BIGINT PRIMARY KEY AUTO_INCREMENT, company_id BIGINT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("CREATE TABLE teams (id BIGINT PRIMARY KEY AUTO_INCREMENT, company_id BIGINT, department_id BIGINT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    company_id BIGINT NULL,
                    department_id BIGINT NULL,
                    team_id BIGINT NULL,
                    org_confirmed BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("INSERT INTO companies (name, enabled) VALUES ('Default Company', TRUE)");
        jdbcTemplate.update("INSERT INTO departments (company_id, name, enabled) VALUES (1, 'Default Department', TRUE)");
        passwordEncoder = new BCryptPasswordEncoder();
        profileService = new ProfileService(jdbcTemplate, passwordEncoder);
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role, enabled, company_id, department_id, org_confirmed) VALUES (?, ?, ?, ?, TRUE, 1, 1, TRUE)",
                "demo", "旧昵称", passwordEncoder.encode("password123"), "USER");
    }

    @Test
    void updatesOnlyCurrentUsersNickname() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "旧昵称", "USER");

        CurrentUser updated = profileService.updateProfile(currentUser, new UpdateProfileRequest("新昵称"));

        assertThat(updated.username()).isEqualTo("demo");
        assertThat(updated.nickname()).isEqualTo("新昵称");
        assertThat(updated.role()).isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE id = 1", String.class)).isEqualTo("新昵称");
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = 1", String.class)).isEqualTo("USER");
    }

    @Test
    void changesPasswordAfterVerifyingCurrentPassword() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "旧昵称", "USER");

        profileService.changePassword(currentUser, new ChangePasswordRequest("password123", "newpass123", "newpass123"));

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = 1", String.class);
        assertThat(passwordEncoder.matches("newpass123", hash)).isTrue();
        assertThat(hash).isNotEqualTo("newpass123");
    }

    @Test
    void rejectsWrongCurrentPassword() {
        CurrentUser currentUser = new CurrentUser(1L, "demo", "旧昵称", "USER");

        assertThatThrownBy(() -> profileService.changePassword(
                currentUser,
                new ChangePasswordRequest("wrong", "newpass123", "newpass123")))
                .isInstanceOf(AuthException.class)
                .hasMessage("原密码不正确");
    }
}
