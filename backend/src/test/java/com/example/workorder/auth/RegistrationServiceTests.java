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
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
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