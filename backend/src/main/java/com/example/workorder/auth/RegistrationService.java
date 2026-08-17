package com.example.workorder.auth;

import com.example.workorder.email.EmailOutboxService;
import com.example.workorder.organization.OrganizationService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final OrganizationService organizationService;
    private final EmailOutboxService emailOutboxService;

    @Autowired
    public RegistrationService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            OrganizationService organizationService,
            EmailOutboxService emailOutboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.organizationService = organizationService;
        this.emailOutboxService = emailOutboxService;
    }

    public RegistrationService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, new OrganizationService(jdbcTemplate), null);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String nickname = request.nickname().trim();
        String email = normalizeEmail(request.email());

        if (!request.password().equals(request.confirmPassword())) {
            throw new RegistrationException("两次输入的密码不一致");
        }
        if (usernameExists(username)) {
            throw new RegistrationException("用户名已被使用");
        }
        organizationService.validateOrganization(request.companyId(), request.departmentId(), request.teamId(), false);

        String passwordHash = passwordEncoder.encode(request.password());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO users
                            (username, nickname, password_hash, role, email, company_id, department_id, team_id, org_confirmed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, username);
                statement.setString(2, nickname);
                statement.setString(3, passwordHash);
                statement.setString(4, DEFAULT_ROLE);
                statement.setString(5, email);
                statement.setObject(6, request.companyId());
                statement.setObject(7, request.departmentId());
                statement.setObject(8, request.teamId());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new RegistrationException("用户名已被使用");
        }

        Number id = keyHolder.getKey();
        Long userId = id == null ? null : id.longValue();
        if (emailOutboxService != null && email != null) {
            emailOutboxService.enqueueVerificationEmail(userId, email);
        }
        return jdbcTemplate.queryForObject(
                UserSql.REGISTER_SELECT + " WHERE u.id = ?",
                UserSql::mapRegister,
                userId);
    }

    private boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    private String normalizeEmail(String email) {
        return email == null || email.trim().isEmpty() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
