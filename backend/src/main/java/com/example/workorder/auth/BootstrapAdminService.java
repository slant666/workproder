package com.example.workorder.auth;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BootstrapAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapToken;

    public BootstrapAdminService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-token:}") String bootstrapToken) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapToken = bootstrapToken;
    }

    @Transactional
    public RegisterResponse createFirstAdmin(BootstrapAdminRequest request, String providedToken) {
        if (!StringUtils.hasText(bootstrapToken) || !bootstrapToken.equals(providedToken)) {
            throw new ForbiddenException();
        }
        if (adminExists()) {
            throw new ForbiddenException();
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new RegistrationException("Passwords do not match");
        }

        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String nickname = request.nickname().trim();
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
                statement.setString(4, Role.ADMIN.name());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new RegistrationException("Username is already in use");
        }

        Number id = keyHolder.getKey();
        return new RegisterResponse(id == null ? null : id.longValue(), username, nickname, Role.ADMIN.name());
    }

    private boolean adminExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = ?",
                Integer.class,
                Role.ADMIN.name());
        return count != null && count > 0;
    }
}
