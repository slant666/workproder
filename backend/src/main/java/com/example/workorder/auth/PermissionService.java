package com.example.workorder.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

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
}
