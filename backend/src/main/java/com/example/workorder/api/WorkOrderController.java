package com.example.workorder.api;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.workorder.CreateWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.UpdateWorkOrderRequest;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderOperationLogResponse;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final PermissionService permissionService;
    private final WorkOrderService workOrderService;

    public WorkOrderController(PermissionService permissionService, WorkOrderService workOrderService) {
        this.permissionService = permissionService;
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public PagedWorkOrderResponse list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.listVisible(new WorkOrderListQuery(keyword, status, priority, null, null, null, null, sort, page, pageSize), user);
    }

    @PostMapping
    public WorkOrderResponse create(@RequestBody CreateWorkOrderRequest request, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.create(request, user);
    }

    @GetMapping("/{id}")
    public WorkOrderResponse detail(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.getVisibleDetail(id, user);
    }

    @GetMapping("/{id}/logs")
    public List<WorkOrderOperationLogResponse> logs(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.listVisibleOperationLogs(id, user);
    }

    @PutMapping("/{id}")
    public WorkOrderResponse update(
            @PathVariable Long id,
            @RequestBody UpdateWorkOrderRequest request,
            HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.update(id, request, user);
    }

    @PostMapping("/{id}/cancel")
    public WorkOrderResponse cancel(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.cancel(id, user);
    }

    @PostMapping("/{id}/confirm")
    public WorkOrderResponse confirm(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.confirmCompletion(id, user);
    }
}
