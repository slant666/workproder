package com.example.workorder.api;

import com.example.workorder.auth.AdminUserListQuery;
import com.example.workorder.auth.AdminUserResponse;
import com.example.workorder.auth.AdminUserService;
import com.example.workorder.auth.PagedAdminUserResponse;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.RbacPermission;
import com.example.workorder.auth.UpdateDepartmentAdminRequest;
import com.example.workorder.auth.UpdateUserEnabledRequest;
import com.example.workorder.auth.UpdateUserOrganizationRequest;
import com.example.workorder.auth.UpdateUserRoleRequest;
import com.example.workorder.excel.ExcelFileResult;
import com.example.workorder.excel.ExcelService;
import com.example.workorder.excel.FileJobResponse;
import com.example.workorder.organization.CreateOrganizationRequest;
import com.example.workorder.organization.OrganizationResponse;
import com.example.workorder.organization.OrganizationService;
import com.example.workorder.organization.UpdateOrganizationEnabledRequest;
import com.example.workorder.workorder.AdminHandlerResponse;
import com.example.workorder.workorder.AssignWorkOrderRequest;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import com.example.workorder.workorder.WorkOrderStatisticsQuery;
import com.example.workorder.workorder.WorkOrderStatisticsResponse;
import com.example.workorder.workorder.WorkOrderStatisticsService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/admin")
public class AdminController {

    private final PermissionService permissionService;
    private final WorkOrderService workOrderService;
    private final WorkOrderStatisticsService workOrderStatisticsService;
    private final AdminUserService adminUserService;
    private final OrganizationService organizationService;
    private final ExcelService excelService;

    @Autowired
    public AdminController(
            PermissionService permissionService,
            WorkOrderService workOrderService,
            WorkOrderStatisticsService workOrderStatisticsService,
            AdminUserService adminUserService,
            OrganizationService organizationService,
            ExcelService excelService) {
        this.permissionService = permissionService;
        this.workOrderService = workOrderService;
        this.workOrderStatisticsService = workOrderStatisticsService;
        this.adminUserService = adminUserService;
        this.organizationService = organizationService;
        this.excelService = excelService;
    }

    public AdminController(
            PermissionService permissionService,
            WorkOrderService workOrderService,
            WorkOrderStatisticsService workOrderStatisticsService,
            AdminUserService adminUserService) {
        this(permissionService, workOrderService, workOrderStatisticsService, adminUserService, new OrganizationService(null), null);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpSession session) {
        permissionService.requireAnyPermission(
                session,
                RbacPermission.USER_VIEW,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN,
                RbacPermission.STATISTICS_VIEW,
                RbacPermission.ORGANIZATION_MANAGE);
        return Map.of("status", "ok", "area", "admin");
    }

    @GetMapping("/users")
    public PagedAdminUserResponse users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.USER_VIEW);
        return adminUserService.list(new AdminUserListQuery(keyword, page, pageSize));
    }

    @GetMapping("/users/import-template")
    public ResponseEntity<InputStreamResource> userImportTemplate(HttpSession session) throws IOException {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        ExcelFileResult result = excelService.userImportTemplate(admin);
        return download(result);
    }

    @PostMapping("/users/import-jobs")
    public FileJobResponse importUsers(@RequestParam("file") MultipartFile file, HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        return excelService.importUsers(file, admin);
    }

    @GetMapping("/file-jobs/{id}/error-report")
    public ResponseEntity<InputStreamResource> importErrorReport(@PathVariable Long id, HttpSession session) throws IOException {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        var path = excelService.errorReportPath(id, admin);
        return download(new ExcelFileResult(id, path, "import-errors-" + id + ".xlsx"));
    }

    @GetMapping("/file-jobs/{id}")
    public FileJobResponse fileJob(@PathVariable Long id, HttpSession session) {
        var user = permissionService.requireAnyPermission(
                session,
                RbacPermission.USER_UPDATE,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN);
        return excelService.findJob(id, user);
    }

    @GetMapping("/file-jobs/{id}/result")
    public ResponseEntity<InputStreamResource> fileJobResult(@PathVariable Long id, HttpSession session) throws IOException {
        var user = permissionService.requireAnyPermission(
                session,
                RbacPermission.USER_UPDATE,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN);
        var path = excelService.resultFilePath(id, user);
        return download(new ExcelFileResult(id, path, "work-orders-" + id + ".xlsx"));
    }

    @PutMapping("/users/{id}/enabled")
    public AdminUserResponse updateUserEnabled(
            @PathVariable Long id,
            @RequestBody UpdateUserEnabledRequest request,
            HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_DISABLE);
        return adminUserService.updateEnabled(id, request, admin);
    }

    @PutMapping("/users/{id}/role")
    public AdminUserResponse updateUserRole(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleRequest request,
            HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        return adminUserService.updateRole(id, request, admin);
    }

    @PutMapping("/users/{id}/organization")
    public AdminUserResponse updateUserOrganization(
            @PathVariable Long id,
            @RequestBody UpdateUserOrganizationRequest request,
            HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        return adminUserService.updateOrganization(id, request, admin);
    }

    @PutMapping("/users/{id}/department-admin")
    public AdminUserResponse updateDepartmentAdmin(
            @PathVariable Long id,
            @RequestBody UpdateDepartmentAdminRequest request,
            HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.USER_UPDATE);
        return adminUserService.updateDepartmentAdmin(id, request, admin);
    }

    @GetMapping("/companies")
    public List<OrganizationResponse> companies(HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.listCompanies();
    }

    @PostMapping("/companies")
    public OrganizationResponse createCompany(@RequestBody CreateOrganizationRequest request, HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.createCompany(request);
    }

    @PutMapping("/companies/{id}/enabled")
    public OrganizationResponse updateCompanyEnabled(
            @PathVariable Long id,
            @RequestBody UpdateOrganizationEnabledRequest request,
            HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.updateCompanyEnabled(id, request);
    }

    @GetMapping("/departments")
    public List<OrganizationResponse> departments(@RequestParam(required = false) Long companyId, HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.listDepartments(companyId);
    }

    @PostMapping("/departments")
    public OrganizationResponse createDepartment(@RequestBody CreateOrganizationRequest request, HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.createDepartment(request);
    }

    @PutMapping("/departments/{id}/enabled")
    public OrganizationResponse updateDepartmentEnabled(
            @PathVariable Long id,
            @RequestBody UpdateOrganizationEnabledRequest request,
            HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.updateDepartmentEnabled(id, request);
    }

    @GetMapping("/teams")
    public List<OrganizationResponse> teams(@RequestParam(required = false) Long departmentId, HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.listTeams(departmentId);
    }

    @PostMapping("/teams")
    public OrganizationResponse createTeam(@RequestBody CreateOrganizationRequest request, HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.createTeam(request);
    }

    @PutMapping("/teams/{id}/enabled")
    public OrganizationResponse updateTeamEnabled(
            @PathVariable Long id,
            @RequestBody UpdateOrganizationEnabledRequest request,
            HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.ORGANIZATION_MANAGE);
        return organizationService.updateTeamEnabled(id, request);
    }

    @GetMapping("/work-orders/statistics")
    public WorkOrderStatisticsResponse workOrderStatistics(
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.STATISTICS_VIEW);
        return workOrderStatisticsService.dashboard(new WorkOrderStatisticsQuery(createdFrom, createdTo));
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
        var user = permissionService.requireAnyPermission(
                session,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN);
        return workOrderService.listVisible(
                new WorkOrderListQuery(keyword, status, priority, creatorId, handlerId, createdFrom, createdTo, sort, page, pageSize),
                user);
    }

    @GetMapping("/work-orders/export")
    public ResponseEntity<InputStreamResource> exportWorkOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String sort,
            HttpSession session) throws IOException {
        var user = permissionService.requireAnyPermission(
                session,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN);
        ExcelFileResult result = excelService.exportWorkOrders(
                new WorkOrderListQuery(keyword, status, priority, creatorId, handlerId, createdFrom, createdTo, sort, 1, 50),
                user);
        return download(result);
    }

    @PostMapping("/work-orders/export-jobs")
    public FileJobResponse createWorkOrderExportJob(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String sort,
            HttpSession session) {
        var user = permissionService.requireAnyPermission(
                session,
                RbacPermission.TICKET_ASSIGN,
                RbacPermission.TICKET_ACCEPT,
                RbacPermission.TICKET_SUBMIT,
                RbacPermission.TICKET_RETURN);
        return excelService.createWorkOrderExportJob(
                new WorkOrderListQuery(keyword, status, priority, creatorId, handlerId, createdFrom, createdTo, sort, 1, 50),
                user);
    }

    @GetMapping("/handlers")
    public List<AdminHandlerResponse> handlers(HttpSession session) {
        permissionService.requirePermission(session, RbacPermission.TICKET_ASSIGN);
        return workOrderService.listEnabledAdminHandlers();
    }

    @PutMapping("/work-orders/{id}/handler")
    public WorkOrderResponse assignHandler(
            @PathVariable Long id,
            @RequestBody AssignWorkOrderRequest request,
            HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.TICKET_ASSIGN);
        return workOrderService.assignHandler(id, request, admin);
    }

    @PutMapping("/work-orders/{id}/accept")
    public WorkOrderResponse acceptWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.TICKET_ACCEPT);
        return workOrderService.accept(id, admin);
    }

    @PutMapping("/work-orders/{id}/submit")
    public WorkOrderResponse submitWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.TICKET_SUBMIT);
        return workOrderService.submitForConfirmation(id, admin);
    }

    @PutMapping("/work-orders/{id}/return")
    public WorkOrderResponse returnWorkOrder(@PathVariable Long id, HttpSession session) {
        var admin = permissionService.requirePermission(session, RbacPermission.TICKET_RETURN);
        return workOrderService.returnToProcessing(id, admin);
    }

    private ResponseEntity<InputStreamResource> download(ExcelFileResult result) throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(Files.size(result.path()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.filename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(Files.newInputStream(result.path())));
    }
}
