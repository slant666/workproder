package com.example.workorder.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.ForbiddenException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

class WorkOrderAttachmentServiceTests {

    private static final String PENDING = "\u5f85\u5904\u7406";

    private Path uploadDir;
    private JdbcTemplate jdbcTemplate;
    private WorkOrderService workOrderService;
    private WorkOrderAttachmentService attachmentService;

    @BeforeEach
    void setUp() throws Exception {
        uploadDir = Path.of("target", "test-uploads", UUID.randomUUID().toString()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:attachments;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_attachments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_operation_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_comments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_status_transitions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_order_assignments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS work_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(30) NOT NULL UNIQUE,
                    nickname VARCHAR(60) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_orders (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(120) NOT NULL,
                    description TEXT NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    priority VARCHAR(10) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT '\u5f85\u5904\u7406',
                    creator_id BIGINT NOT NULL,
                    handler_id BIGINT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_order_operation_logs (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    work_order_id BIGINT NOT NULL,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(60) NOT NULL,
                    field_name VARCHAR(60) NULL,
                    old_value TEXT NULL,
                    new_value TEXT NULL,
                    details_json TEXT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE work_order_attachments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    work_order_id BIGINT NOT NULL,
                    uploader_id BIGINT NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    stored_filename VARCHAR(120) NOT NULL UNIQUE,
                    content_type VARCHAR(120) NOT NULL,
                    file_size BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role) VALUES (?, ?, ?, ?)",
                "demo", "Demo", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role) VALUES (?, ?, ?, ?)",
                "other", "Other", "hash", "USER");
        jdbcTemplate.update("INSERT INTO users (username, nickname, password_hash, role) VALUES (?, ?, ?, ?)",
                "admin", "Admin", "hash", "ADMIN");
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "Own order", "Own description", "Device", "\u4e2d", PENDING, 1L);
        jdbcTemplate.update("INSERT INTO work_orders (title, description, type, priority, status, creator_id) VALUES (?, ?, ?, ?, ?, ?)",
                "Other order", "Sensitive", "Account", "\u9ad8", PENDING, 2L);
        workOrderService = new WorkOrderService(jdbcTemplate);
        attachmentService = new WorkOrderAttachmentService(
                jdbcTemplate,
                workOrderService,
                new WorkOrderAttachmentProperties(uploadDir.toString(), 10));
    }

    @Test
    void uploadsAllowedAttachmentWithRandomStoredFilenameAndOperationLog() throws Exception {
        CurrentUser owner = new CurrentUser(1L, "demo", "Demo", "USER");

        WorkOrderAttachmentResponse first = attachmentService.upload(1L, png("report.png", new byte[] {1, 2, 3}), owner);
        WorkOrderAttachmentResponse second = attachmentService.upload(1L, png("report.png", new byte[] {4, 5}), owner);

        assertThat(first.originalFilename()).isEqualTo("report.png");
        assertThat(first.fileSize()).isEqualTo(3L);
        assertThat(first.uploaderUsername()).isEqualTo("demo");
        assertThat(attachmentService.listVisibleAttachments(1L, owner))
                .extracting(WorkOrderAttachmentResponse::originalFilename)
                .containsExactly("report.png", "report.png");

        List<String> storedFilenames = jdbcTemplate.queryForList(
                "SELECT stored_filename FROM work_order_attachments ORDER BY id",
                String.class);
        assertThat(storedFilenames).hasSize(2);
        assertThat(storedFilenames.get(0)).isNotEqualTo("report.png");
        assertThat(storedFilenames.get(1)).isNotEqualTo("report.png");
        assertThat(storedFilenames.get(0)).isNotEqualTo(storedFilenames.get(1));
        assertThat(Files.exists(uploadDir.resolve(storedFilenames.get(0)))).isTrue();
        assertThat(Files.exists(uploadDir.resolve(storedFilenames.get(1)))).isTrue();
        assertThat(workOrderService.listVisibleOperationLogs(1L, owner))
                .extracting(WorkOrderOperationLogResponse::action)
                .containsExactly("attachment_add", "attachment_add");
        assertThat(second.createdAt()).isNotNull();
    }

    @Test
    void downloadsOnlyAfterCheckingWorkOrderPermissionAndAttachmentOwnership() throws Exception {
        CurrentUser owner = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser other = new CurrentUser(2L, "other", "Other", "USER");
        CurrentUser admin = new CurrentUser(3L, "admin", "Admin", "ADMIN");
        WorkOrderAttachmentResponse attachment = attachmentService.upload(1L, pdf("manual.pdf", new byte[] {9, 8, 7}), owner);

        WorkOrderAttachmentDownload download = attachmentService.download(1L, attachment.id(), owner);

        assertThat(download.attachment().originalFilename()).isEqualTo("manual.pdf");
        assertThat(download.resource().getContentAsByteArray()).containsExactly(9, 8, 7);
        assertThat(attachmentService.download(1L, attachment.id(), admin).attachment().id()).isEqualTo(attachment.id());
        assertThatThrownBy(() -> attachmentService.download(1L, attachment.id(), other))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attachmentService.download(2L, attachment.id(), admin))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("附件不存在");
    }

    @Test
    void rejectsDangerousTypesOversizedFilesAndUnauthorizedUploads() {
        CurrentUser owner = new CurrentUser(1L, "demo", "Demo", "USER");
        CurrentUser other = new CurrentUser(2L, "other", "Other", "USER");

        assertThatThrownBy(() -> attachmentService.upload(1L, png("big.png", new byte[11]), owner))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("附件大小不能超过 10 bytes");
        assertThatThrownBy(() -> attachmentService.upload(1L, file("run.exe", "application/octet-stream", new byte[] {1}), owner))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("附件类型不允许");
        assertThatThrownBy(() -> attachmentService.upload(1L, file("fake.png", "application/x-msdownload", new byte[] {1}), owner))
                .isInstanceOf(WorkOrderException.class)
                .hasMessage("附件类型不允许");
        assertThatThrownBy(() -> attachmentService.upload(1L, png("nope.png", new byte[] {1}), other))
                .isInstanceOf(ForbiddenException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_attachments", Long.class)).isZero();
    }

    private MockMultipartFile png(String filename, byte[] content) {
        return file(filename, "image/png", content);
    }

    private MockMultipartFile pdf(String filename, byte[] content) {
        return file(filename, "application/pdf", content);
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }
}
