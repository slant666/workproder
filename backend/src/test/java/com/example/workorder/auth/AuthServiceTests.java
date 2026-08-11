package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
        createUser("demo", "Demo User", "password123", true);

        CurrentUser user = authService.login(new LoginRequest("demo", "password123"));

        assertThat(user.username()).isEqualTo("demo");
        assertThat(user.nickname()).isEqualTo("Demo User");
        assertThat(user.role()).isEqualTo("USER");
    }

    @Test
    void rejectsWrongPasswordWithUnifiedMessage() {
        createUser("demo", "Demo User", "password123", true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "bad-password")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void rejectsDisabledUserWithUnifiedMessage() {
        createUser("demo", "Demo User", "password123", false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "password123")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void locksUsernameAfterRepeatedFailedLogins() {
        createUser("demo", "Demo User", "password123", true);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "bad-password")))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("用户名或密码错误");
        }

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "password123")))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessage("登录失败次数过多，请稍后再试");
    }

    @Test
    void allowsLoginAgainAfterLockExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-11T00:00:00Z"));
        authService = new AuthService(jdbcTemplate, passwordEncoder, clock);
        createUser("demo", "Demo User", "password123", true);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "bad-password")))
                    .isInstanceOf(AuthException.class);
        }

        clock.advanceSeconds(16 * 60);
        CurrentUser user = authService.login(new LoginRequest("demo", "password123"));

        assertThat(user.username()).isEqualTo("demo");
    }

    private void createUser(String username, String nickname, String password, boolean enabled) {
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, role, enabled) VALUES (?, ?, ?, 'USER', ?)",
                username,
                nickname,
                passwordEncoder.encode(password),
                enabled);
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
