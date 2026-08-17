package com.example.workorder.api;

import com.example.workorder.organization.OrganizationResponse;
import com.example.workorder.organization.OrganizationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/companies")
    public List<OrganizationResponse> companies() {
        return organizationService.listCompanies();
    }

    @GetMapping("/departments")
    public List<OrganizationResponse> departments(@RequestParam(required = false) Long companyId) {
        return organizationService.listDepartments(companyId);
    }

    @GetMapping("/teams")
    public List<OrganizationResponse> teams(@RequestParam(required = false) Long departmentId) {
        return organizationService.listTeams(departmentId);
    }
}
