package com.example.workorder.organization;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OrganizationResponse> listCompanies() {
        return jdbcTemplate.query(
                "SELECT id, name, enabled FROM companies ORDER BY enabled DESC, name ASC, id ASC",
                (rs, rowNum) -> new OrganizationResponse(rs.getLong("id"), rs.getString("name"), rs.getBoolean("enabled"), null, null, null, null));
    }

    public List<OrganizationResponse> listDepartments(Long companyId) {
        String sql = """
                SELECT d.id, d.name, d.enabled, d.company_id, c.name AS company_name
                FROM departments d
                JOIN companies c ON c.id = d.company_id
                """ + (companyId == null ? "" : " WHERE d.company_id = ?")
                + " ORDER BY d.enabled DESC, c.name ASC, d.name ASC, d.id ASC";
        Object[] params = companyId == null ? new Object[] {} : new Object[] {companyId};
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new OrganizationResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBoolean("enabled"),
                        rs.getLong("company_id"),
                        rs.getString("company_name"),
                        null,
                        null),
                params);
    }

    public List<OrganizationResponse> listTeams(Long departmentId) {
        String sql = """
                SELECT t.id, t.name, t.enabled, t.company_id, c.name AS company_name,
                       t.department_id, d.name AS department_name
                FROM teams t
                JOIN companies c ON c.id = t.company_id
                JOIN departments d ON d.id = t.department_id
                """ + (departmentId == null ? "" : " WHERE t.department_id = ?")
                + " ORDER BY t.enabled DESC, c.name ASC, d.name ASC, t.name ASC, t.id ASC";
        Object[] params = departmentId == null ? new Object[] {} : new Object[] {departmentId};
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new OrganizationResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBoolean("enabled"),
                        rs.getLong("company_id"),
                        rs.getString("company_name"),
                        rs.getLong("department_id"),
                        rs.getString("department_name")),
                params);
    }

    public OrganizationResponse createCompany(CreateOrganizationRequest request) {
        String name = requireName(request == null ? null : request.name());
        try {
            Long id = insert("INSERT INTO companies (name) VALUES (?)", name);
            return findCompany(id);
        } catch (DuplicateKeyException ex) {
            throw new OrganizationException("公司名称已存在");
        }
    }

    public OrganizationResponse createDepartment(CreateOrganizationRequest request) {
        String name = requireName(request == null ? null : request.name());
        Long companyId = requirePositive(request == null ? null : request.companyId(), "公司不能为空");
        requireEnabledCompany(companyId);
        try {
            Long id = insert("INSERT INTO departments (company_id, name) VALUES (?, ?)", companyId, name);
            return listDepartments(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        } catch (DuplicateKeyException ex) {
            throw new OrganizationException("部门名称已存在");
        }
    }

    public OrganizationResponse createTeam(CreateOrganizationRequest request) {
        String name = requireName(request == null ? null : request.name());
        Long departmentId = requirePositive(request == null ? null : request.departmentId(), "部门不能为空");
        Long companyId = companyIdByDepartment(departmentId);
        try {
            Long id = insert("INSERT INTO teams (company_id, department_id, name) VALUES (?, ?, ?)", companyId, departmentId, name);
            return listTeams(departmentId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        } catch (DuplicateKeyException ex) {
            throw new OrganizationException("团队名称已存在");
        }
    }

    public OrganizationResponse updateCompanyEnabled(Long id, UpdateOrganizationEnabledRequest request) {
        updateEnabled("companies", id, request);
        return findCompany(id);
    }

    public OrganizationResponse updateDepartmentEnabled(Long id, UpdateOrganizationEnabledRequest request) {
        updateEnabled("departments", id, request);
        Long companyId = companyIdByDepartment(id);
        return listDepartments(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public OrganizationResponse updateTeamEnabled(Long id, UpdateOrganizationEnabledRequest request) {
        updateEnabled("teams", id, request);
        Long departmentId = jdbcTemplate.queryForObject("SELECT department_id FROM teams WHERE id = ?", Long.class, id);
        return listTeams(departmentId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public void validateOrganization(Long companyId, Long departmentId, Long teamId, boolean requireConfirmedFields) {
        if (companyId == null && departmentId == null && teamId == null) {
            return;
        }
        if (departmentId != null) {
            Long actualCompanyId = companyIdByDepartment(departmentId);
            if (companyId != null && !companyId.equals(actualCompanyId)) {
                throw new OrganizationException("部门不属于所选公司");
            }
            requireEnabledDepartment(departmentId);
        } else if (teamId != null || requireConfirmedFields) {
            throw new OrganizationException("部门不能为空");
        }
        if (teamId != null) {
            TeamRef team = teamById(teamId);
            if (departmentId != null && !departmentId.equals(team.departmentId())) {
                throw new OrganizationException("团队不属于所选部门");
            }
            if (companyId != null && !companyId.equals(team.companyId())) {
                throw new OrganizationException("团队不属于所选公司");
            }
            if (!team.enabled()) {
                throw new OrganizationException("团队已停用");
            }
        }
        if (companyId != null) {
            requireEnabledCompany(companyId);
        }
    }

    public Long companyIdByDepartment(Long departmentId) {
        try {
            Long companyId = jdbcTemplate.queryForObject("SELECT company_id FROM departments WHERE id = ?", Long.class, departmentId);
            requireEnabledDepartment(departmentId);
            return companyId;
        } catch (EmptyResultDataAccessException ex) {
            throw new OrganizationException("部门不存在");
        }
    }

    private OrganizationResponse findCompany(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, name, enabled FROM companies WHERE id = ?",
                    (rs, rowNum) -> new OrganizationResponse(rs.getLong("id"), rs.getString("name"), rs.getBoolean("enabled"), null, null, null, null),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new OrganizationException("公司不存在");
        }
    }

    private void requireEnabledCompany(Long id) {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM companies WHERE id = ?", Boolean.class, id);
        if (!Boolean.TRUE.equals(enabled)) {
            throw new OrganizationException("公司已停用");
        }
    }

    private void requireEnabledDepartment(Long id) {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM departments WHERE id = ?", Boolean.class, id);
        if (!Boolean.TRUE.equals(enabled)) {
            throw new OrganizationException("部门已停用");
        }
    }

    private TeamRef teamById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT company_id, department_id, enabled FROM teams WHERE id = ?",
                    (rs, rowNum) -> new TeamRef(rs.getLong("company_id"), rs.getLong("department_id"), rs.getBoolean("enabled")),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new OrganizationException("团队不存在");
        }
    }

    private void updateEnabled(String table, Long id, UpdateOrganizationEnabledRequest request) {
        Boolean enabled = request == null ? null : request.enabled();
        if (id == null || id < 1) {
            throw new OrganizationException("组织参数不正确");
        }
        if (enabled == null) {
            throw new OrganizationException("启用状态不能为空");
        }
        int updated = jdbcTemplate.update("UPDATE " + table + " SET enabled = ? WHERE id = ?", enabled, id);
        if (updated != 1) {
            throw new OrganizationException("组织不存在");
        }
    }

    private Long insert(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new OrganizationException("创建组织失败");
        }
        return key.longValue();
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new OrganizationException("名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 120) {
            throw new OrganizationException("名称不能超过 120 个字符");
        }
        return trimmed;
    }

    private Long requirePositive(Long id, String message) {
        if (id == null || id < 1) {
            throw new OrganizationException(message);
        }
        return id;
    }

    private record TeamRef(Long companyId, Long departmentId, boolean enabled) {
    }
}
