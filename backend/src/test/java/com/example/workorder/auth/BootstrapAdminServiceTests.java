package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BootstrapAdminServiceTests {

    private JdbcTemplate jdbcTemplate;
    private BootstrapAdminService bootstrapAdminService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:bootstrap_admin;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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
        bootstrapAdminService = new BootstrapAdminService(jdbcTemplate, new BCryptPasswordEncoder(), "setup-token");
    }

    @Test
    void createsFirstAdminWithToken() {
        RegisterResponse response = bootstrapAdminService.createFirstAdmin(new BootstrapAdminRequest(
                "RootAdmin",
                "Root Admin",
                "password123",
                "password123"), "setup-token");

        assertThat(response.username()).isEqualTo("rootadmin");
        assertThat(response.role()).isEqualTo("ADMIN");

        var user = jdbcTemplate.queryForMap("SELECT username, password_hash, role FROM users WHERE username = ?", "rootadmin");
        assertThat(user.get("password_hash")).isNotEqualTo("password123");
        assertThat(user.get("password_hash").toString()).startsWith("$2");
        assertThat(user.get("role")).isEqualTo("ADMIN");
    }

    @Test
    void rejectsMissingOrWrongToken() {
        BootstrapAdminRequest request = new BootstrapAdminRequest("root", "Root", "password123", "password123");

        assertThatThrownBy(() -> bootstrapAdminService.createFirstAdmin(request, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
        assertThatThrownBy(() -> bootstrapAdminService.createFirstAdmin(request, "wrong"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }

    @Test
    void rejectsWhenAdminAlreadyExists() {
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, role) VALUES (?, ?, ?, ?)",
                "existing", "Existing", "hash", "ADMIN");
        BootstrapAdminRequest request = new BootstrapAdminRequest("root", "Root", "password123", "password123");

        assertThatThrownBy(() -> bootstrapAdminService.createFirstAdmin(request, "setup-token"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");
    }
}
