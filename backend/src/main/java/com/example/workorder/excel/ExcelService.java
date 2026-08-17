package com.example.workorder.excel;

import com.example.workorder.async.AsyncTaskPublisher;
import com.example.workorder.auth.AdminUserException;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelService {

    private static final ZoneId EXPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int EXPORT_PAGE_SIZE = 50;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int IMPORT_MAX_ROWS = 5_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final WorkOrderService workOrderService;
    private final PasswordEncoder passwordEncoder;
    private final Path storageRoot;
    private final AsyncTaskPublisher asyncTaskPublisher;
    private final ObjectMapper objectMapper;

    public ExcelService(
            JdbcTemplate jdbcTemplate,
            WorkOrderService workOrderService,
            PasswordEncoder passwordEncoder,
            @Value("${app.excel.storage-dir:uploads/excel-jobs}") String storageDir) {
        this(jdbcTemplate, workOrderService, passwordEncoder, storageDir, null, new ObjectMapper());
    }

    @Autowired
    public ExcelService(
            JdbcTemplate jdbcTemplate,
            WorkOrderService workOrderService,
            PasswordEncoder passwordEncoder,
            @Value("${app.excel.storage-dir:uploads/excel-jobs}") String storageDir,
            @Nullable AsyncTaskPublisher asyncTaskPublisher,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.workOrderService = workOrderService;
        this.passwordEncoder = passwordEncoder;
        this.storageRoot = Path.of(storageDir);
        this.asyncTaskPublisher = asyncTaskPublisher;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Transactional
    public ExcelFileResult exportWorkOrders(WorkOrderListQuery query, CurrentUser actor) {
        Long jobId = createJob("WORK_ORDER_EXPORT", actor.id(), null, filterJson(query));
        markRunning(jobId);
        try {
            Files.createDirectories(storageRoot.resolve("exports"));
            String filename = "work-orders-" + FILE_TIME.format(LocalDateTime.now(EXPORT_ZONE)) + ".xlsx";
            Path path = storageRoot.resolve("exports").resolve(filename);
            int count = writeWorkOrderExport(path, query, actor);
            markSuccess(jobId, path, null, count, count, 0);
            return new ExcelFileResult(jobId, path, filename);
        } catch (IOException | RuntimeException ex) {
            markFailed(jobId, ex.getMessage());
            throw ex instanceof RuntimeException runtime ? runtime : new ExcelException("导出工单失败");
        }
    }

    @Transactional
    public FileJobResponse createWorkOrderExportJob(WorkOrderListQuery query, CurrentUser actor) {
        Long jobId = createJob("WORK_ORDER_EXPORT", actor.id(), null, filterJson(query));
        publishFileExport(jobId);
        return findJob(jobId);
    }

    @Transactional
    public void processWorkOrderExportJob(Long jobId) {
        FileJobExecution job = claimExportJob(jobId);
        if (job == null) {
            return;
        }
        try {
            Files.createDirectories(storageRoot.resolve("exports"));
            String filename = "work-orders-" + jobId + "-" + FILE_TIME.format(LocalDateTime.now(EXPORT_ZONE)) + ".xlsx";
            Path path = storageRoot.resolve("exports").resolve(filename);
            int count = writeWorkOrderExport(path, queryFromFilterJson(job.filterJson()), actorById(job.createdBy()));
            markSuccess(jobId, path, null, count, count, 0);
        } catch (IOException | RuntimeException ex) {
            markFailed(jobId, ex.getMessage());
            throw ex instanceof RuntimeException runtime ? runtime : new ExcelException("导出工单失败");
        }
    }

    public Path resultFilePath(Long jobId, CurrentUser actor) {
        Path path = filePath(jobId, actor.id(), "result_file_path");
        if (path == null) {
            throw new ExcelException("导出文件还未生成");
        }
        return path;
    }

    @Scheduled(fixedDelayString = "${work-order.excel.export-publish-interval-ms:60000}")
    public void publishPendingWorkOrderExportJobs() {
        if (asyncTaskPublisher == null) {
            return;
        }
        List<Long> jobIds = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM file_jobs
                WHERE type = 'WORK_ORDER_EXPORT' AND status = 'PENDING'
                ORDER BY id ASC
                LIMIT 20
                """,
                Long.class);
        for (Long jobId : jobIds) {
            publishFileExport(jobId);
        }
    }

    public ExcelFileResult userImportTemplate(CurrentUser actor) {
        try {
            Long jobId = createJob("USER_IMPORT_TEMPLATE", actor.id(), null, null);
            Files.createDirectories(storageRoot.resolve("templates"));
            String filename = "user-import-template.xlsx";
            Path path = storageRoot.resolve("templates").resolve(filename);
            try (Workbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(path)) {
                Sheet sheet = workbook.createSheet("用户导入");
                writeRow(sheet, 0, List.of("用户名", "昵称", "邮箱", "角色", "公司", "部门", "团队", "是否启用"));
                writeRow(sheet, 1, List.of("zhangsan", "张三", "shared@example.com", "USER", "默认公司", "默认部门", "默认团队", "是"));
                autosize(sheet, 8);
                Sheet notes = workbook.createSheet("填写说明");
                writeRow(notes, 0, List.of("字段", "说明"));
                writeRow(notes, 1, List.of("用户名", "必填，4-30 个字符，不能重复"));
                writeRow(notes, 2, List.of("昵称", "必填"));
                writeRow(notes, 3, List.of("邮箱", "选填，可多人共用"));
                writeRow(notes, 4, List.of("角色", "USER / CUSTOMER_SERVICE / DEPARTMENT_ADMIN / ADMIN / AUDITOR"));
                writeRow(notes, 5, List.of("公司/部门/团队", "按名称匹配，部门存在时会标记组织已确认"));
                writeRow(notes, 6, List.of("是否启用", "是/否，空值默认是"));
                autosize(notes, 2);
                workbook.write(out);
            }
            markSuccess(jobId, path, null, 0, 0, 0);
            return new ExcelFileResult(jobId, path, filename);
        } catch (IOException ex) {
            throw new ExcelException("生成导入模板失败");
        }
    }

    @Transactional
    public FileJobResponse importUsers(MultipartFile file, CurrentUser actor) {
        if (file == null || file.isEmpty()) {
            throw new ExcelException("请选择要导入的 Excel 文件");
        }
        Long jobId = createJob("USER_IMPORT", actor.id(), file.getOriginalFilename(), null);
        markRunning(jobId);
        List<ImportError> errors = new ArrayList<>();
        int total = 0;
        int success = 0;
        Set<String> seenUsernames = new HashSet<>();
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row)) {
                    continue;
                }
                total++;
                if (total > IMPORT_MAX_ROWS) {
                    errors.add(new ImportError(i + 1, "超过单次导入上限 " + IMPORT_MAX_ROWS + " 行"));
                    continue;
                }
                String error = importUserRow(row, seenUsernames);
                if (error == null) {
                    success++;
                } else {
                    errors.add(new ImportError(i + 1, error));
                }
            }
            Path errorReport = errors.isEmpty() ? null : writeErrorReport(sheet, errors, jobId);
            markSuccess(jobId, null, errorReport, total, success, errors.size());
            return findJob(jobId);
        } catch (IOException | RuntimeException ex) {
            markFailed(jobId, ex.getMessage());
            throw ex instanceof RuntimeException runtime ? runtime : new ExcelException("导入用户失败");
        }
    }

    public FileJobResponse findJob(Long id) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, type, status, original_filename, result_file_path, error_report_path,
                       total_count, success_count, failed_count, error_message, created_at, finished_at
                FROM file_jobs WHERE id = ?
                """,
                (rs, rowNum) -> new FileJobResponse(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("original_filename"),
                        rs.getInt("total_count"),
                        rs.getInt("success_count"),
                        rs.getInt("failed_count"),
                        rs.getString("result_file_path") != null,
                        rs.getString("error_report_path") != null,
                        rs.getString("error_message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant()),
                id);
    }

    public FileJobResponse findJob(Long id, CurrentUser actor) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_jobs WHERE id = ? AND created_by = ?",
                Long.class,
                id,
                actor.id());
        if (count == null || count == 0) {
            throw new AdminUserException("文件任务不存在");
        }
        return findJob(id);
    }

    public Path errorReportPath(Long jobId, CurrentUser actor) {
        Path path = filePath(jobId, actor.id(), "error_report_path");
        if (path == null) {
            throw new ExcelException("错误报告不存在");
        }
        return path;
    }

    private int writeWorkOrderExport(Path path, WorkOrderListQuery query, CurrentUser actor) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(path)) {
            Sheet sheet = workbook.createSheet("工单导出");
            writeRow(sheet, 0, List.of("工单ID", "标题", "类型", "优先级", "状态", "创建人", "处理人", "公司", "部门", "团队", "SLA状态", "创建时间"));
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper helper = workbook.getCreationHelper();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            int rowIndex = 1;
            int page = 1;
            while (rowIndex <= EXPORT_MAX_ROWS) {
                PagedWorkOrderResponse response = workOrderService.listVisible(
                        new WorkOrderListQuery(query.keyword(), query.status(), query.priority(), query.creatorId(), query.handlerId(),
                                query.createdFrom(), query.createdTo(), query.sort(), page, EXPORT_PAGE_SIZE),
                        actor);
                for (WorkOrderResponse item : response.items()) {
                    Row row = sheet.createRow(rowIndex++);
                    writeWorkOrderRow(row, item, dateStyle);
                    if (rowIndex > EXPORT_MAX_ROWS) {
                        break;
                    }
                }
                if (response.items().isEmpty() || page >= response.totalPages()) {
                    break;
                }
                page++;
            }
            autosize(sheet, 12);
            workbook.write(out);
            return rowIndex - 1;
        }
    }

    private void writeWorkOrderRow(Row row, WorkOrderResponse item, CellStyle dateStyle) {
        set(row, 0, item.id());
        set(row, 1, item.title());
        set(row, 2, item.type());
        set(row, 3, item.priority());
        set(row, 4, item.status());
        set(row, 5, item.creatorUsername());
        set(row, 6, item.handlerUsername());
        set(row, 7, item.companyName());
        set(row, 8, item.departmentName());
        set(row, 9, item.teamName());
        set(row, 10, item.slaStatus());
        Cell cell = row.createCell(11);
        cell.setCellValue(LocalDateTime.ofInstant(item.createdAt(), EXPORT_ZONE));
        cell.setCellStyle(dateStyle);
    }

    private String importUserRow(Row row, Set<String> seenUsernames) {
        String username = text(row, 0).toLowerCase(Locale.ROOT);
        String nickname = text(row, 1);
        String email = text(row, 2).toLowerCase(Locale.ROOT);
        String role = text(row, 3).toUpperCase(Locale.ROOT);
        String companyName = text(row, 4);
        String departmentName = text(row, 5);
        String teamName = text(row, 6);
        boolean enabled = !"否".equals(text(row, 7));
        if (username.length() < 4 || username.length() > 30) return "用户名长度必须为 4 到 30 个字符";
        if (!seenUsernames.add(username)) return "Excel 内用户名重复";
        if (exists("SELECT COUNT(*) FROM users WHERE username = ?", username)) return "用户名已存在";
        if (nickname.isBlank()) return "昵称不能为空";
        if (role.isBlank()) role = "USER";
        if (!Set.of("USER", "CUSTOMER_SERVICE", "DEPARTMENT_ADMIN", "ADMIN", "AUDITOR").contains(role)) return "角色不存在";
        Long companyId = companyName.isBlank() ? null : findOrgId("companies", companyName, null, null);
        if (!companyName.isBlank() && companyId == null) return "公司不存在";
        Long departmentId = departmentName.isBlank() ? null : findOrgId("departments", departmentName, "company_id", companyId);
        if (!departmentName.isBlank() && departmentId == null) return "部门不存在";
        Long teamId = teamName.isBlank() ? null : findOrgId("teams", teamName, "department_id", departmentId);
        if (!teamName.isBlank() && teamId == null) return "团队不存在";
        jdbcTemplate.update(
                """
                INSERT INTO users (username, nickname, email, password_hash, role, enabled, company_id, department_id, team_id, org_confirmed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                username,
                nickname,
                email.isBlank() ? null : email,
                passwordEncoder.encode(randomPassword()),
                role,
                enabled,
                companyId,
                departmentId,
                teamId,
                departmentId != null);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        try {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", userId, role);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private Path writeErrorReport(Sheet sourceSheet, List<ImportError> errors, Long jobId) throws IOException {
        Files.createDirectories(storageRoot.resolve("imports"));
        Path path = storageRoot.resolve("imports").resolve("user-import-errors-" + jobId + ".xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(path)) {
            Sheet sheet = workbook.createSheet("错误报告");
            writeRow(sheet, 0, List.of("原始行号", "错误原因"));
            int rowIndex = 1;
            for (ImportError error : errors) {
                writeRow(sheet, rowIndex++, List.of(String.valueOf(error.rowNumber()), error.message()));
            }
            autosize(sheet, 2);
            workbook.write(out);
        }
        return path;
    }

    private Long createJob(String type, Long createdBy, String originalFilename, String filterJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO file_jobs (type, status, created_by, original_filename, filter_json)
                    VALUES (?, 'PENDING', ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, type);
            ps.setLong(2, createdBy);
            ps.setString(3, originalFilename);
            ps.setString(4, filterJson);
            return ps;
        }, keyHolder);
        Number key = generatedKey(keyHolder);
        if (key == null) {
            throw new ExcelException("创建文件任务失败");
        }
        return key.longValue();
    }

    private Number generatedKey(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().isEmpty()) {
            return null;
        }
        Map<String, Object> keys = keyHolder.getKeyList().getFirst();
        Object value = keys.getOrDefault("GENERATED_KEY", keys.get("ID"));
        return value instanceof Number number ? number : null;
    }

    private void markRunning(Long jobId) {
        jdbcTemplate.update("UPDATE file_jobs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP WHERE id = ?", jobId);
    }

    private void markSuccess(Long jobId, Path resultPath, Path errorReportPath, int total, int success, int failed) {
        jdbcTemplate.update(
                """
                UPDATE file_jobs
                SET status = 'SUCCESS', result_file_path = ?, error_report_path = ?,
                    total_count = ?, success_count = ?, failed_count = ?, finished_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                resultPath == null ? null : resultPath.toString(),
                errorReportPath == null ? null : errorReportPath.toString(),
                total,
                success,
                failed,
                jobId);
    }

    private void markFailed(Long jobId, String message) {
        jdbcTemplate.update(
                "UPDATE file_jobs SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP WHERE id = ?",
                message == null ? "文件任务失败" : message.substring(0, Math.min(500, message.length())),
                jobId);
    }

    private FileJobExecution claimExportJob(Long jobId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE file_jobs
                SET status = 'RUNNING', started_at = COALESCE(started_at, CURRENT_TIMESTAMP), error_message = NULL
                WHERE id = ? AND type = 'WORK_ORDER_EXPORT' AND status = 'PENDING'
                """,
                jobId);
        if (updated != 1) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT id, created_by, filter_json FROM file_jobs WHERE id = ?",
                (rs, rowNum) -> new FileJobExecution(rs.getLong("id"), rs.getLong("created_by"), rs.getString("filter_json")),
                jobId);
    }

    private void publishFileExport(Long jobId) {
        if (asyncTaskPublisher != null) {
            asyncTaskPublisher.publishFileExport(jobId);
        }
    }

    private WorkOrderListQuery queryFromFilterJson(String filterJson) {
        try {
            Map<String, String> filters = objectMapper.readValue(
                    filterJson == null || filterJson.isBlank() ? "{}" : filterJson,
                    new TypeReference<>() {
                    });
            return new WorkOrderListQuery(
                    blankToNull(filters.get("keyword")),
                    blankToNull(filters.get("status")),
                    blankToNull(filters.get("priority")),
                    parseLong(filters.get("creatorId")),
                    parseLong(filters.get("handlerId")),
                    blankToNull(filters.get("createdFrom")),
                    blankToNull(filters.get("createdTo")),
                    blankToNull(filters.get("sort")),
                    1,
                    EXPORT_PAGE_SIZE);
        } catch (IOException ex) {
            throw new ExcelException("导出筛选条件无效");
        }
    }

    private CurrentUser actorById(Long userId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT u.id, u.username, u.nickname, u.role,
                       u.company_id, c.name AS company_name,
                       u.department_id, d.name AS department_name,
                       u.team_id, t.name AS team_name,
                       u.org_confirmed
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                LEFT JOIN departments d ON d.id = u.department_id
                LEFT JOIN teams t ON t.id = u.team_id
                WHERE u.id = ?
                """,
                (rs, rowNum) -> new CurrentUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nickname"),
                        rs.getString("role"),
                        (Long) rs.getObject("company_id"),
                        rs.getString("company_name"),
                        (Long) rs.getObject("department_id"),
                        rs.getString("department_name"),
                        (Long) rs.getObject("team_id"),
                        rs.getString("team_name"),
                        rs.getBoolean("org_confirmed")),
                userId);
    }

    private Path filePath(Long jobId, Long actorId, String column) {
        String path = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM file_jobs WHERE id = ? AND created_by = ?",
                String.class,
                jobId,
                actorId);
        return path == null ? null : Path.of(path);
    }

    private Long findOrgId(String table, String name, String parentColumn, Long parentId) {
        List<Long> ids = parentColumn == null
                ? jdbcTemplate.queryForList("SELECT id FROM " + table + " WHERE name = ? AND enabled = TRUE", Long.class, name)
                : jdbcTemplate.queryForList("SELECT id FROM " + table + " WHERE name = ? AND " + parentColumn + " = ? AND enabled = TRUE", Long.class, name, parentId);
        return ids.size() == 1 ? ids.getFirst() : null;
    }

    private boolean exists(String sql, String value) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count != null && count > 0;
    }

    private String filterJson(WorkOrderListQuery query) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "keyword", query.keyword() == null ? "" : query.keyword(),
                    "status", query.status() == null ? "" : query.status(),
                    "priority", query.priority() == null ? "" : query.priority(),
                    "creatorId", query.creatorId() == null ? "" : String.valueOf(query.creatorId()),
                    "handlerId", query.handlerId() == null ? "" : String.valueOf(query.handlerId()),
                    "createdFrom", query.createdFrom() == null ? "" : query.createdFrom(),
                    "createdTo", query.createdTo() == null ? "" : query.createdTo(),
                    "sort", query.sort() == null ? "" : query.sort()));
        } catch (IOException ex) {
            throw new ExcelException("导出筛选条件无效");
        }
    }

    private Long parseLong(String value) {
        String text = blankToNull(value);
        return text == null ? null : Long.valueOf(text);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBlankRow(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 8; i++) {
            if (!text(row, i).isBlank()) return false;
        }
        return true;
    }

    private String text(Row row, int index) {
        if (row == null) return "";
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
    }

    private void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }

    private void set(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : value.toString());
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i), 2800), 9000));
        }
    }

    private record ImportError(int rowNumber, String message) {
    }

    private record FileJobExecution(Long id, Long createdBy, String filterJson) {
    }
}
