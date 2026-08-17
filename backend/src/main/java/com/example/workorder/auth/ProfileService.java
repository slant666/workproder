package com.example.workorder.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    @Autowired
    public ProfileService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, RbacService rbacService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.rbacService = rbacService;
    }

    public ProfileService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, new RbacService(jdbcTemplate));
    }

    @Transactional
    public CurrentUser updateProfile(CurrentUser currentUser, UpdateProfileRequest request) {
        String nickname = request.nickname().trim();
        if (nickname.isEmpty()) {
            throw new AuthException("昵称不能为空");
        }
        jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", nickname, currentUser.id());
        CurrentUser user = jdbcTemplate.queryForObject(
                UserSql.CURRENT_USER_SELECT + " WHERE u.id = ?",
                UserSql::mapCurrentUser,
                currentUser.id());
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

    @Transactional
    public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AuthException("两次输入的新密码不一致");
        }

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?",
                String.class,
                currentUser.id());
        if (passwordHash == null || !passwordEncoder.matches(request.currentPassword(), passwordHash)) {
            throw new AuthException("原密码不正确");
        }

        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(request.newPassword()),
                currentUser.id());
    }
}
