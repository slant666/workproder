package com.example.workorder.auth;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final String DEFAULT_ROLE = "USER";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String nickname = request.nickname().trim();

        if (!request.password().equals(request.confirmPassword())) {
            throw new RegistrationException("两次输入的密码不一致");
        }

        if (usernameExists(username)) {
            throw new RegistrationException("用户名已被使用");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (username, nickname, password_hash, role) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, username);
                statement.setString(2, nickname);
                statement.setString(3, passwordHash);
                statement.setString(4, DEFAULT_ROLE);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new RegistrationException("用户名已被使用");
        }

        Number id = keyHolder.getKey();
        return new RegisterResponse(id == null ? null : id.longValue(), username, nickname, DEFAULT_ROLE);
    }

    private boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }
}