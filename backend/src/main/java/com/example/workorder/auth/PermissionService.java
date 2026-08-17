package com.example.workorder.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final RbacService rbacService;

    public PermissionService(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    public PermissionService() {
        this.rbacService = null;
    }

    public CurrentUser requireUser(HttpSession session) {
        Object currentUser = session.getAttribute(SessionKeys.CURRENT_USER);
        if (currentUser instanceof CurrentUser user) {
            return user;
        }
        throw new UnauthorizedException();
    }

    public CurrentUser requireAdmin(HttpSession session) {
        CurrentUser user = requireUser(session);
        if (Role.ADMIN.name().equals(user.role())) {
            return user;
        }
        throw new ForbiddenException();
    }

    public CurrentUser requirePermission(HttpSession session, String permission) {
        CurrentUser user = requireUser(session);
        if (hasPermission(user, permission)) {
            return user;
        }
        throw new ForbiddenException();
    }

    public void requirePermission(CurrentUser user, String permission) {
        if (!hasPermission(user, permission)) {
            throw new ForbiddenException();
        }
    }

    public CurrentUser requireAnyPermission(HttpSession session, String... permissions) {
        CurrentUser user = requireUser(session);
        for (String permission : permissions) {
            if (hasPermission(user, permission)) {
                return user;
            }
        }
        throw new ForbiddenException();
    }

    private boolean hasPermission(CurrentUser user, String permission) {
        if (user == null) {
            return false;
        }
        if (user.hasPermission(permission)) {
            return true;
        }
        return rbacService != null && rbacService.permissionsForUser(user.id(), user.role()).contains(permission);
    }
}
