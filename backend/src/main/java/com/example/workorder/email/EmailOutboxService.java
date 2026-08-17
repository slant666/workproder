package com.example.workorder.email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final EmailProperties properties;
    private Boolean outboxAvailable;

    public EmailOutboxService(JdbcTemplate jdbcTemplate, EmailProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public void enqueueVerificationEmail(Long userId, String email) {
        if (isBlank(email)) {
            return;
        }
        String code = verificationCode();
        Long tokenId = insertToken(
                "email_verification_tokens",
                userId,
                code,
                Instant.now().plusSeconds(properties.getVerificationCodeTtlMinutes() * 60L));
        enqueue(
                userId,
                normalizeEmail(email),
                "EMAIL_VERIFICATION",
                "注册邮箱验证码",
                "你的注册验证码是：" + code + "。验证码将在 " + properties.getVerificationCodeTtlMinutes() + " 分钟后过期。",
                null,
                "email:verify:user:" + userId + ":token:" + tokenId);
    }

    @Transactional
    public void enqueuePasswordResetEmail(Long userId, String email) {
        if (isBlank(email)) {
            return;
        }
        String code = verificationCode();
        Long tokenId = insertToken(
                "password_reset_tokens",
                userId,
                code,
                Instant.now().plusSeconds(properties.getPasswordResetTtlMinutes() * 60L));
        enqueue(
                userId,
                normalizeEmail(email),
                "PASSWORD_RESET",
                "密码重置验证码",
                "你的密码重置验证码是：" + code + "。验证码将在 " + properties.getPasswordResetTtlMinutes() + " 分钟后过期。",
                null,
                "email:password-reset:user:" + userId + ":token:" + tokenId);
    }

    public void enqueueUserNotification(Long userId, String type, String title, String content, Long workOrderId) {
        if (!shouldEmailNotification(type) || userId == null || !isOutboxAvailable()) {
            return;
        }
        try {
            UserEmail userEmail = jdbcTemplate.queryForObject(
                    "SELECT email, email_verified_at FROM users WHERE id = ?",
                    (rs, rowNum) -> new UserEmail(rs.getString("email"), rs.getTimestamp("email_verified_at") != null),
                    userId);
            if (userEmail == null || isBlank(userEmail.email()) || !userEmail.verified()) {
                return;
            }
            enqueue(
                    userId,
                    normalizeEmail(userEmail.email()),
                    type,
                    title,
                    content,
                    workOrderId,
                    "email:notification:" + type + ":user:" + userId + ":work-order:" + workOrderId + ":content:" + sha256(content));
        } catch (DataAccessException ignored) {
            outboxAvailable = false;
        }
    }

    public Long enqueue(Long userId, String toEmail, String type, String subject, String body, Long workOrderId, String dedupeKey) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO email_outbox
                        (recipient_user_id, to_email, type, subject, body, related_work_order_id, dedupe_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId,
                    normalizeEmail(toEmail),
                    type,
                    subject,
                    body,
                    workOrderId,
                    dedupeKey);
            return jdbcTemplate.queryForObject("SELECT id FROM email_outbox WHERE dedupe_key = ?", Long.class, dedupeKey);
        } catch (DuplicateKeyException ex) {
            return jdbcTemplate.queryForObject("SELECT id FROM email_outbox WHERE dedupe_key = ?", Long.class, dedupeKey);
        }
    }

    private Long insertToken(String tableName, Long userId, String code, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO " + tableName + " (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId,
                sha256(code),
                Timestamp.from(expiresAt));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + tableName + " WHERE user_id = ?", Long.class, userId);
    }

    private boolean shouldEmailNotification(String type) {
        return "WORK_ORDER_ASSIGNED".equals(type)
                || "WORK_ORDER_STATUS_CHANGED".equals(type)
                || "SLA_NEAR_OVERDUE".equals(type)
                || "SLA_OVERDUE".equals(type);
    }

    private boolean isOutboxAvailable() {
        if (outboxAvailable != null) {
            return outboxAvailable;
        }
        try {
            jdbcTemplate.queryForList("SELECT id FROM email_outbox WHERE 1 = 0");
            outboxAvailable = true;
        } catch (RuntimeException ex) {
            outboxAvailable = false;
        }
        return outboxAvailable;
    }

    private String verificationCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record UserEmail(String email, boolean verified) {
    }
}
