package com.example.workorder.auth;

import com.example.workorder.email.EmailOutboxService;
import java.util.Locale;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final JdbcTemplate jdbcTemplate;

    public EmailVerificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void verify(EmailVerificationRequest request) {
        String account = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        Long userId = findUserId(account);
        int updated = jdbcTemplate.update(
                """
                UPDATE email_verification_tokens
                SET used_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND token_hash = ?
                  AND used_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                userId,
                EmailOutboxService.sha256(request.code().trim()));
        if (updated != 1) {
            throw new AuthException("验证码不正确或已过期");
        }
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
    }

    private Long findUserId(String account) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ? OR email = ?",
                    Long.class,
                    account,
                    account);
        } catch (EmptyResultDataAccessException ex) {
            throw new AuthException("验证码不正确或已过期");
        }
    }
}
