package com.example.workorder.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.workorder.async.AsyncTaskPublisher;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.workorder.PagedWorkOrderResponse;
import com.example.workorder.workorder.WorkOrderListQuery;
import com.example.workorder.workorder.WorkOrderResponse;
import com.example.workorder.workorder.WorkOrderService;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ExcelServiceTests {

    private JdbcTemplate jdbcTemplate;
    private WorkOrderService workOrderService;
    private AsyncTaskPublisher asyncTaskPublisher;
    private ExcelService excelService;

    Path storageDir;

    @BeforeEach
    void setUp() throws Exception {
        storageDir = Path.of("target", "excel-service-tests", String.valueOf(System.nanoTime()));
        Files.createDirectories(storageDir);
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:excel;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_roles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS file_jobs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("DROP TABLE IF EXISTS teams");
        jdbcTemplate.execute("DROP TABLE IF EXISTS departments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS companies");
        jdbcTemplate.execute("CREATE TABLE companies (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("CREATE TABLE departments (id BIGINT PRIMARY KEY AUTO_INCREMENT, company_id BIGINT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("CREATE TABLE teams (id BIGINT PRIMARY KEY AUTO_INCREMENT, company_id BIGINT, department_id BIGINT, name VARCHAR(120), enabled BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    email VARCHAR(160) NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL,
                    enabled BOOLEAN NOT NULL,
                    company_id BIGINT NULL,
                    department_id BIGINT NULL,
                    team_id BIGINT NULL,
                    org_confirmed BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE TABLE user_roles (user_id BIGINT NOT NULL, role_code VARCHAR(40) NOT NULL)");
        jdbcTemplate.execute("""
                CREATE TABLE file_jobs (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    type VARCHAR(60) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_by BIGINT NOT NULL,
                    original_filename VARCHAR(255) NULL,
                    result_file_path VARCHAR(500) NULL,
                    error_report_path VARCHAR(500) NULL,
                    total_count INT NOT NULL DEFAULT 0,
                    success_count INT NOT NULL DEFAULT 0,
                    failed_count INT NOT NULL DEFAULT 0,
                    filter_json TEXT NULL,
                    error_message VARCHAR(500) NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    started_at TIMESTAMP NULL,
                    finished_at TIMESTAMP NULL
                )
                """);
        jdbcTemplate.update("INSERT INTO companies (name, enabled) VALUES ('Default Company', TRUE)");
        jdbcTemplate.update("INSERT INTO departments (company_id, name, enabled) VALUES (1, 'Default Department', TRUE)");
        jdbcTemplate.update("INSERT INTO teams (company_id, department_id, name, enabled) VALUES (1, 1, 'Default Team', TRUE)");
        jdbcTemplate.update("""
                INSERT INTO users (username, nickname, email, password_hash, role, enabled, org_confirmed)
                VALUES ('admin', 'Admin', 'shared@example.com', 'hash', 'ADMIN', TRUE, TRUE)
                """);
        workOrderService = mock(WorkOrderService.class);
        asyncTaskPublisher = mock(AsyncTaskPublisher.class);
        excelService = new ExcelService(jdbcTemplate, workOrderService, new BCryptPasswordEncoder(), storageDir.toString(), asyncTaskPublisher, null);
    }

    @Test
    void importUsersKeepsSuccessRowsAndWritesErrorReport() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                userImportWorkbook());

        FileJobResponse job = excelService.importUsers(file, admin());

        assertThat(job.status()).isEqualTo("SUCCESS");
        assertThat(job.totalCount()).isEqualTo(3);
        assertThat(job.successCount()).isEqualTo(2);
        assertThat(job.failedCount()).isEqualTo(1);
        assertThat(job.hasErrorReport()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, "shared@example.com"))
                .isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username IN ('importok1', 'importok2')", Long.class))
                .isEqualTo(2L);

        Path errorReport = excelService.errorReportPath(job.id(), admin());
        assertThat(Files.exists(errorReport)).isTrue();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(errorReport))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("3");
            assertThat(row.getCell(1).getStringCellValue()).isNotBlank();
        }
    }

    @Test
    void exportWorkOrdersUsesCurrentFiltersAndWritesWorkbook() throws Exception {
        WorkOrderListQuery query = new WorkOrderListQuery("printer", "待处理", "高", null, null, "2026-08-01", "2026-08-11", "createdAtDesc", 1, 50);
        when(workOrderService.listVisible(any(WorkOrderListQuery.class), eq(admin()))).thenReturn(new PagedWorkOrderResponse(
                List.of(new WorkOrderResponse(10L, "Printer broken", "desc", "IT", "高", "待处理", 2L, "user1", 1L, "admin",
                        1L, "Default Company", 1L, "Default Department", 1L, "Default Team",
                        null, null, null, null, "NORMAL", Instant.parse("2026-08-11T10:00:00Z"))),
                1,
                1,
                50,
                1));

        ExcelFileResult result = excelService.exportWorkOrders(query, admin());

        verify(workOrderService).listVisible(any(WorkOrderListQuery.class), eq(admin()));
        assertThat(Files.exists(result.path())).isTrue();
        assertThat(excelService.findJob(result.jobId()).successCount()).isEqualTo(1);
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(result.path()))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(10D);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Printer broken");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("Default Department");
        }
    }

    @Test
    void asyncExportCreatesPendingJobPublishesMessageAndConsumerCompletesIt() throws Exception {
        WorkOrderListQuery query = new WorkOrderListQuery("printer", "待处理", "高", null, null, "2026-08-01", "2026-08-11", "createdAtDesc", 1, 50);
        when(workOrderService.listVisible(any(WorkOrderListQuery.class), any(CurrentUser.class))).thenReturn(new PagedWorkOrderResponse(
                List.of(new WorkOrderResponse(11L, "Async printer export", "desc", "IT", "高", "待处理", 2L, "user1", null, null,
                        1L, "Default Company", 1L, "Default Department", 1L, "Default Team",
                        null, null, null, null, "NORMAL", Instant.parse("2026-08-11T10:00:00Z"))),
                1,
                1,
                50,
                1));

        FileJobResponse created = excelService.createWorkOrderExportJob(query, admin());
        verify(asyncTaskPublisher).publishFileExport(created.id());
        assertThat(created.status()).isEqualTo("PENDING");

        excelService.processWorkOrderExportJob(created.id());

        FileJobResponse finished = excelService.findJob(created.id());
        assertThat(finished.status()).isEqualTo("SUCCESS");
        assertThat(finished.successCount()).isEqualTo(1);
        assertThat(finished.hasResultFile()).isTrue();
    }

    private byte[] userImportWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("users");
            row(sheet.createRow(0), "username", "nickname", "email", "role", "company", "department", "team", "enabled");
            row(sheet.createRow(1), "importok1", "Import One", "shared@example.com", "USER", "Default Company", "Default Department", "Default Team", "yes");
            row(sheet.createRow(2), "bad", "Too Short", "bad@example.com", "USER", "", "", "", "yes");
            row(sheet.createRow(3), "importok2", "Import Two", "shared@example.com", "CUSTOMER_SERVICE", "", "", "", "yes");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void row(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private CurrentUser admin() {
        return new CurrentUser(1L, "admin", "Admin", "ADMIN");
    }
}
