package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RegistrationServiceTests {

    private JdbcTemplate jdbcTemplate;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:registration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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
                    email VARCHAR(160) NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    company_id BIGINT NULL,
                    department_id BIGINT NULL,
                    team_id BIGINT NULL,
                    org_confirmed BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("INSERT INTO companies (name, enabled) VALUES ('Default Company', TRUE)");
        jdbcTemplate.update("INSERT INTO departments (company_id, name, enabled) VALUES (1, 'Default Department', TRUE)");
        registrationService = new RegistrationService(jdbcTemplate, new BCryptPasswordEncoder());
    }

    @Test
    void registersDefaultUserWithEncryptedPassword() {
        RegisterResponse response = registrationService.register(new RegisterRequest(
                "TestUser",
                "测试用户",
                "password123",
                "password123"));

        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.nickname()).isEqualTo("测试用户");
        assertThat(response.role()).isEqualTo("USER");

        var user = jdbcTemplate.queryForMap("SELECT username, password_hash, role FROM users WHERE username = ?", "testuser");
        assertThat(user.get("password_hash")).isNotEqualTo("password123");
        assertThat(user.get("password_hash").toString()).startsWith("$2");
        assertThat(user.get("role")).isEqualTo("USER");
    }

    @Test
    void registersUserWithEmailAndOrganizationApplication() {
        jdbcTemplate.update("INSERT INTO teams (company_id, department_id, name, enabled) VALUES (1, 1, 'Default Team', TRUE)");

        RegisterResponse response = registrationService.register(new RegisterRequest(
                "OrgUser",
                "组织用户",
                "password123",
                "password123",
                "org-user@example.com",
                1L,
                1L,
                1L));

        assertThat(response.companyId()).isEqualTo(1L);
        assertThat(response.companyName()).isEqualTo("Default Company");
        assertThat(response.departmentId()).isEqualTo(1L);
        assertThat(response.departmentName()).isEqualTo("Default Department");
        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(response.teamName()).isEqualTo("Default Team");
        assertThat(response.orgConfirmed()).isFalse();

        var user = jdbcTemplate.queryForMap(
                "SELECT email, company_id, department_id, team_id, org_confirmed FROM users WHERE username = ?",
                "orguser");
        assertThat(user.get("email")).isEqualTo("org-user@example.com");
        assertThat(user.get("company_id")).isEqualTo(1L);
        assertThat(user.get("department_id")).isEqualTo(1L);
        assertThat(user.get("team_id")).isEqualTo(1L);
        assertThat(user.get("org_confirmed")).isEqualTo(false);
    }

    @Test
    void allowsMultipleUsersToShareSameEmail() {
        registrationService.register(new RegisterRequest(
                "emailuser1",
                "邮箱用户一",
                "password123",
                "password123",
                "shared@example.com",
                null,
                null,
                null));
        registrationService.register(new RegisterRequest(
                "emailuser2",
                "邮箱用户二",
                "password123",
                "password123",
                "shared@example.com",
                null,
                null,
                null));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, "shared@example.com"))
                .isEqualTo(2L);
    }

    @Test
    void rejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest("demo", "用户", "password123", "password123");
        registrationService.register(request);

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("用户名已被使用");
    }

    @Test
    void rejectsMismatchedPasswords() {
        RegisterRequest request = new RegisterRequest("demo", "用户", "password123", "password456");

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("两次输入的密码不一致");
    }
}
