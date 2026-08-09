package com.example.workorder.api;

import com.example.workorder.auth.AdminUserListQuery;
import com.example.workorder.auth.AdminUserResponse;
import com.example.workorder.auth.AdminUserService;
import com.example.workorder.auth.PagedAdminUserResponse;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.UpdateUserEnabledRequest;
import com.example.workorder.auth.UpdateUserRoleRequest;
import com.example.workorder.workorder.AdminHandlerResponse;
import com.example.workorder.workorder.AssignWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PermissionService permissionService;
    private final WorkOrderService workOrderService;
    private final AdminUserService adminUserService;

    public AdminController(
            PermissionService permissionService,
            WorkOrderService workOrderService,
            AdminUserService adminUserService) {
        this.permissionService = permissionService;
        this.workOrderService = workOrderService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpSession session) {
        permissionService.requireAdmin(session);
        return Map.of("status", "ok", "area", "admin");
    }

    @GetMapping("/users")
    public PagedAdminUserResponse users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpSession session) {
        permissionService.requireAdmin(session);
        return adminUserService.list(new AdminUserListQuery(keyword, page, pageSize));
    }

    @PutMapping("/users/{id}/enabled")
    public AdminUserResponse updateUserEnabled(
            @PathVariable Long id,
            @RequestBody UpdateUserEnabledRequest request,
            HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return adminUserService.updateEnabled(id, request, admin);
    }

    @PutMapping("/users/{id}/role")
    public AdminUserResponse updateUserRole(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleRequest request,
            HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return adminUserService.updateRole(id, request, admin);
    }

    @GetMapping("/work-orders")
    public PagedWorkOrderResponse workOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpSession session) {
        permissionService.requireAdmin(session);
        return workOrderService.listAllForAdmin(
                new WorkOrderListQuery(keyword, status, priority, creatorId, handlerId, createdFrom, createdTo, sort, page, pageSize));
    }

    @GetMapping("/handlers")
    public List<AdminHandlerResponse> handlers(HttpSession session) {
        permissionService.requireAdmin(session);
        return workOrderService.listEnabledAdminHandlers();
    }

    @PutMapping("/work-orders/{id}/handler")
    public WorkOrderResponse assignHandler(
            @PathVariable Long id,
            @RequestBody AssignWorkOrderRequest request,
            HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return workOrderService.assignHandler(id, request, admin);
    }

    @PutMapping("/work-orders/{id}/accept")
    public WorkOrderResponse acceptWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return workOrderService.accept(id, admin);
    }

    @PutMapping("/work-orders/{id}/submit")
    public WorkOrderResponse submitWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return workOrderService.submitForConfirmation(id, admin);
    }

    @PutMapping("/work-orders/{id}/return")
    public WorkOrderResponse returnWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requireAdmin(session);
        return workOrderService.returnToProcessing(id, admin);
    }
}
