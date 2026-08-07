package com.example.workorder.api;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.workorder.CreateWorkOrderCommentRequest;
import com.example.workorder.workorder.CreateWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.UpdateWorkOrderRequest;
import com.example.workorder.workorder.WorkOrderAttachmentDownload;
import com.example.workorder.workorder.WorkOrderAttachmentResponse;
import com.example.workorder.workorder.WorkOrderAttachmentService;
import com.example.workorder.workorder.WorkOrderCommentResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderOperationLogResponse;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final PermissionService permissionService;
    private final WorkOrderService workOrderService;
    private final WorkOrderAttachmentService attachmentService;

    public WorkOrderController(
            PermissionService permissionService,
            WorkOrderService workOrderService,
            WorkOrderAttachmentService attachmentService) {
        this.permissionService = permissionService;
        this.workOrderService = workOrderService;
        this.attachmentService = attachmentService;
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

    @GetMapping("/{id}/comments")
    public List<WorkOrderCommentResponse> comments(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.listVisibleComments(id, user);
    }

    @GetMapping("/{id}/attachments")
    public List<WorkOrderAttachmentResponse> attachments(@PathVariable Long id, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return attachmentService.listVisibleAttachments(id, user);
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkOrderAttachmentResponse uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return attachmentService.upload(id, file, user);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        WorkOrderAttachmentDownload download = attachmentService.download(id, attachmentId, user);
        String encodedFilename = URLEncoder.encode(download.attachment().originalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentLength(download.attachment().fileSize())
                .body(download.resource());
    }

    @PostMapping("/{id}/comments")
    public WorkOrderCommentResponse addComment(
            @PathVariable Long id,
            @RequestBody CreateWorkOrderCommentRequest request,
            HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        return workOrderService.addComment(id, request, user);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public void deleteComment(@PathVariable Long id, @PathVariable Long commentId, HttpSession session) {
        CurrentUser user = permissionService.requireUser(session);
        workOrderService.deleteComment(id, commentId, user);
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
