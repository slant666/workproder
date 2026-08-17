package com.example.workorder.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.redis.RedisSupportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    void legacyCustomerServiceRoleGetsProcessingPermissions() {
        createUser("support", "Support User", "password123", true, "CUSTOMER_SERVICE");

        CurrentUser user = authService.login(new LoginRequest("support", "password123"));

        assertThat(user.role()).isEqualTo("CUSTOMER_SERVICE");
        assertThat(user.roles()).contains("CUSTOMER_SERVICE");
        assertThat(user.permissions()).contains("ticket:accept", "ticket:submit");
        assertThat(user.permissions()).doesNotContain("ticket:create");
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

    @Test
    void rejectsLoginWhenRedisFailureCounterIsLocked() {
        createUser("demo", "Demo User", "password123", true);
        FakeRedisSupportService redis = new FakeRedisSupportService();
        redis.locked = true;
        authService = new AuthService(
                jdbcTemplate,
                passwordEncoder,
                new RbacService(jdbcTemplate),
                Clock.systemUTC(),
                redis);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "password123")))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void recordsAndClearsLoginFailuresInRedisWhenAvailable() {
        createUser("demo", "Demo User", "password123", true);
        FakeRedisSupportService redis = new FakeRedisSupportService();
        authService = new AuthService(
                jdbcTemplate,
                passwordEncoder,
                new RbacService(jdbcTemplate),
                Clock.systemUTC(),
                redis);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "bad-password")))
                .isInstanceOf(AuthException.class);
        authService.login(new LoginRequest("demo", "password123"));

        assertThat(redis.recordedUsername).isEqualTo("demo");
        assertThat(redis.clearedUsername).isEqualTo("demo");
    }

    private void createUser(String username, String nickname, String password, boolean enabled) {
        createUser(username, nickname, password, enabled, "USER");
    }

    private void createUser(String username, String nickname, String password, boolean enabled, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, password_hash, role, enabled, company_id, department_id, org_confirmed) VALUES (?, ?, ?, ?, ?, 1, 1, TRUE)",
                username,
                nickname,
                passwordEncoder.encode(password),
                role,
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

    private static class FakeRedisSupportService extends RedisSupportService {
        private boolean locked;
        private String recordedUsername;
        private String clearedUsername;

        FakeRedisSupportService() {
            super((StringRedisTemplate) null, new ObjectMapper());
        }

        @Override
        public boolean isLoginLocked(String username, int maxFailedAttempts) {
            return locked;
        }

        @Override
        public int recordLoginFailure(String username) {
            recordedUsername = username;
            return 1;
        }

        @Override
        public void clearLoginFailures(String username) {
            clearedUsername = username;
        }
    }
}
