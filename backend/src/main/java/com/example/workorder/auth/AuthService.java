package com.example.workorder.auth;

import com.example.workorder.redis.RedisSupportService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String LOGIN_FAILED_MESSAGE = "\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;
    private final Clock clock;
    private final RedisSupportService redisSupportService;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            RbacService rbacService,
            ObjectProvider<RedisSupportService> redisSupportServiceProvider) {
        this(jdbcTemplate, passwordEncoder, rbacService, Clock.systemUTC(), redisSupportServiceProvider.getIfAvailable());
    }

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, new RbacService(jdbcTemplate), Clock.systemUTC(), null);
    }

    AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, Clock clock) {
        this(jdbcTemplate, passwordEncoder, new RbacService(jdbcTemplate), clock, null);
    }

    AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, RbacService rbacService, Clock clock) {
        this(jdbcTemplate, passwordEncoder, rbacService, clock, null);
    }

    AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            RbacService rbacService,
            Clock clock,
            RedisSupportService redisSupportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.rbacService = rbacService;
        this.clock = clock;
        this.redisSupportService = redisSupportService;
    }

    public CurrentUser login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        rejectIfLocked(username);
        UserCredentials user = findCredentials(username);

        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            recordFailure(username);
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        loginAttempts.remove(username);
        if (redisSupportService != null) {
            redisSupportService.clearLoginFailures(username);
        }
        return loadCurrentUser(user.id());
    }

    private void rejectIfLocked(String username) {
        if (redisSupportService != null && redisSupportService.isLoginLocked(username, MAX_FAILED_ATTEMPTS)) {
            throw new LoginRateLimitException();
        }
        LoginAttempt attempt = loginAttempts.get(username);
        Instant now = Instant.now(clock);
        if (attempt != null && attempt.failedAttempts() >= MAX_FAILED_ATTEMPTS && attempt.lockedUntil().isAfter(now)) {
            throw new LoginRateLimitException();
        }
        if (attempt != null && attempt.failedAttempts() >= MAX_FAILED_ATTEMPTS && !attempt.lockedUntil().isAfter(now)) {
            loginAttempts.remove(username);
        }
    }

    private void recordFailure(String username) {
        if (redisSupportService != null && redisSupportService.recordLoginFailure(username) >= 0) {
            return;
        }
        Instant now = Instant.now(clock);
        loginAttempts.compute(username, (key, existing) -> {
            int failures = existing == null ? 1 : existing.failedAttempts() + 1;
            Instant lockedUntil = failures >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : now;
            return new LoginAttempt(failures, lockedUntil);
        });
    }

    private UserCredentials findCredentials(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, username, nickname, password_hash, role, enabled FROM users WHERE username = ?",
                    (rs, rowNum) -> new UserCredentials(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("nickname"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getBoolean("enabled")),
                    username);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private CurrentUser loadCurrentUser(Long id) {
        CurrentUser user = jdbcTemplate.queryForObject(
                UserSql.CURRENT_USER_SELECT + " WHERE u.id = ?",
                UserSql::mapCurrentUser,
                id);
        return withPermissions(user);
    }

    private CurrentUser withPermissions(CurrentUser user) {
        return new CurrentUser(
                user.id(),
                user.username(),
                user.nickname(),
                user.role(),
                rbacService.rolesForUser(user.id(), user.role()),
                rbacService.permissionsForUser(user.id(), user.role()),
                user.companyId(),
                user.companyName(),
                user.departmentId(),
                user.departmentName(),
                user.teamId(),
                user.teamName(),
                user.orgConfirmed());
    }

    private record UserCredentials(Long id, String username, String nickname, String passwordHash, String role, boolean enabled) {
    }

    private record LoginAttempt(int failedAttempts, Instant lockedUntil) {
    }
}
