package com.example.workorder.auth;

import java.util.Locale;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String LOGIN_FAILED_MESSAGE = "用户名或密码错误";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public CurrentUser login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        UserCredentials user = findCredentials(username);

        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        return new CurrentUser(user.id(), user.username(), user.nickname(), user.role());
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
}