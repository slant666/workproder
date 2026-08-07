package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTests {

    private JdbcTemplate jdbcTemplate;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(jdbcTemplate, passwordEncoder);
    }

    @Test
    void logsInWithCorrectPassword() {
        createUser("demo", "演示用户", "password123", true);

        CurrentUser user = authService.login(new LoginRequest("demo", "password123"));

        assertThat(user.username()).isEqualTo("demo");
        assertThat(user.nickname()).isEqualTo("演示用户");
        assertThat(user.role()).isEqualTo("USER");
    }

    @Test
    void rejectsWrongPasswordWithUnifiedMessage() {
        createUser("demo", "演示用户", "password123", true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "bad-password")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void rejectsDisabledUserWithUnifiedMessage() {
        createUser("demo", "演示用户", "password123", false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "password123")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
    }

    private void createUser(String username, String nickname, String password, boolean enabled) {
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, 'USER', ?)",
                username,
                nickname,
                passwordEncoder.encode(password),
                enabled);
    }
}