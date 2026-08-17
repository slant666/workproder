package com.example.workorder.auth;

import com.example.workorder.email.EmailOutboxService;
import java.util.Locale;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final JdbcTemplate jdbcTemplate;
    private final EmailOutboxService emailOutboxService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(JdbcTemplate jdbcTemplate, EmailOutboxService emailOutboxService, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailOutboxService = emailOutboxService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthException("两次输入的密码不一致");
        }
        String account = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        UserEmail user = findVerifiedUser(account);
        int tokenUpdated = jdbcTemplate.update(
                """
                UPDATE password_reset_tokens
                SET used_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND token_hash = ?
                  AND used_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                user.id(),
                EmailOutboxService.sha256(request.code().trim()));
        if (tokenUpdated != 1) {
            throw new AuthException("验证码不正确或已过期");
        }
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(request.password()),
                user.id());
    }

    private UserEmail findVerifiedUser(String account) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, email, email_verified_at
                    FROM users
                    WHERE enabled = TRUE
                      AND (username = ? OR email = ?)
                    """,
                    (rs, rowNum) -> new UserEmail(
                            rs.getLong("id"),
                            rs.getString("email"),
                            rs.getTimestamp("email_verified_at") != null),
                    account,
                    account);
        } catch (EmptyResultDataAccessException ex) {
            throw new AuthException("验证码不正确或已过期");
        }
    }

    @Transactional
    public void requestReset(PasswordResetRequest request) {
        String account = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        try {
            UserEmail user = jdbcTemplate.queryForObject(
                    """
                    SELECT id, email, email_verified_at
                    FROM users
                    WHERE enabled = TRUE
                      AND (username = ? OR email = ?)
                    """,
                    (rs, rowNum) -> new UserEmail(
                            rs.getLong("id"),
                            rs.getString("email"),
                            rs.getTimestamp("email_verified_at") != null),
                    account,
                    account);
            if (user != null && user.verified() && user.email() != null && !user.email().isBlank()) {
                emailOutboxService.enqueuePasswordResetEmail(user.id(), user.email());
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Keep the response indistinguishable so callers cannot enumerate accounts.
        }
    }

    private record UserEmail(Long id, String email, boolean verified) {
    }
}
