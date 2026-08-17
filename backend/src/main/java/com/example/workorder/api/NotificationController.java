package com.example.workorder.api;

import com.example.workorder.auth.PermissionService;
import com.example.workorder.notification.NotificationService;
import com.example.workorder.notification.PagedNotificationResponse;
import com.example.workorder.notification.UnreadNotificationCountResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final PermissionService permissionService;
    private final NotificationService notificationService;

    public NotificationController(PermissionService permissionService, NotificationService notificationService) {
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public PagedNotificationResponse list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpSession session) {
        return notificationService.list(permissionService.requireUser(session), page, pageSize);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(HttpSession session) {
        return notificationService.unreadCount(permissionService.requireUser(session));
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, HttpSession session) {
        notificationService.markRead(id, permissionService.requireUser(session));
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(HttpSession session) {
        notificationService.markAllRead(permissionService.requireUser(session));
    }
}
