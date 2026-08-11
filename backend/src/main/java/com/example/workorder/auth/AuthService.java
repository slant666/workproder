package com.example.workorder.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Clock clock;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, Clock.systemUTC());
    }

    AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
        return new CurrentUser(user.id(), user.username(), user.nickname(), user.role());
    }

    private void rejectIfLocked(String username) {
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

    private record UserCredentials(Long id, String username, String nickname, String passwordHash, String role, boolean enabled) {
    }

    private record LoginAttempt(int failedAttempts, Instant lockedUntil) {
    }
}
