package com.example.workorder.workorder;

import com.example.workorder.auth.CurrentUser;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkOrderAttachmentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv");
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "ps1", "js", "jar", "war", "php", "jsp", "html", "htm", "svg", "msi", "com", "scr");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword", "application/octet-stream")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/octet-stream")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/octet-stream")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/octet-stream")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", "application/octet-stream")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/zip", "application/octet-stream")),
            Map.entry("txt", Set.of("text/plain", "application/octet-stream")),
            Map.entry("csv", Set.of("text/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream")));

    private final JdbcTemplate jdbcTemplate;
    private final WorkOrderService workOrderService;
    private final long maxSizeBytes;
    private final AttachmentStorageService storageService;
    private Boolean storageColumnsAvailable;

    @Autowired
    public WorkOrderAttachmentService(
            JdbcTemplate jdbcTemplate,
            WorkOrderService workOrderService,
            WorkOrderAttachmentProperties properties,
            AttachmentStorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.workOrderService = workOrderService;
        this.maxSizeBytes = properties.maxSizeBytes();
        this.storageService = storageService;
    }

    public WorkOrderAttachmentService(
            JdbcTemplate jdbcTemplate,
            WorkOrderService workOrderService,
            WorkOrderAttachmentProperties properties) {
        this(jdbcTemplate, workOrderService, properties, new LocalAttachmentStorageService(properties));
    }

    public List<WorkOrderAttachmentResponse> listVisibleAttachments(Long workOrderId, CurrentUser currentUser) {
        workOrderService.requireVisibleWorkOrder(workOrderId, currentUser);
        return jdbcTemplate.query(
                """
                SELECT a.id, a.work_order_id, a.uploader_id, u.username AS uploader_username,
                       u.nickname AS uploader_nickname, a.original_filename, a.content_type, a.file_size, a.created_at
                FROM work_order_attachments a
                JOIN users u ON u.id = a.uploader_id
                WHERE a.work_order_id = ?
                ORDER BY a.created_at ASC, a.id ASC
                """,
                this::mapAttachment,
                workOrderId);
    }

    @Transactional
    public WorkOrderAttachmentResponse upload(Long workOrderId, MultipartFile file, CurrentUser currentUser) {
        workOrderService.requireVisibleWorkOrder(workOrderId, currentUser);
        ValidatedFile validated = validate(file);
        String objectKey = "work-orders/" + workOrderId + "/" + UUID.randomUUID() + "." + validated.extension();
        AttachmentStorageService.StoredObject storedObject = storageService.store(
                objectKey,
                file,
                validated.contentType(),
                validated.size());

        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                boolean includeStorage = hasStorageColumns();
                PreparedStatement ps = connection.prepareStatement(
                        includeStorage
                                ? """
                                  INSERT INTO work_order_attachments
                                      (work_order_id, uploader_id, original_filename, stored_filename, content_type, file_size,
                                       storage_provider, bucket_name, object_key)
                                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                  """
                                : """
                                  INSERT INTO work_order_attachments
                                      (work_order_id, uploader_id, original_filename, stored_filename, content_type, file_size)
                                  VALUES (?, ?, ?, ?, ?, ?)
                                  """,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, workOrderId);
                ps.setLong(2, currentUser.id());
                ps.setString(3, validated.originalFilename());
                ps.setString(4, objectKey);
                ps.setString(5, validated.contentType());
                ps.setLong(6, validated.size());
                if (includeStorage) {
                    ps.setString(7, storedObject.provider());
                    ps.setString(8, storedObject.bucket());
                    ps.setString(9, storedObject.objectKey());
                }
                return ps;
            }, keyHolder);
            Number key = generatedId(keyHolder);
            if (key == null) {
                throw new WorkOrderException("上传附件失败");
            }
            workOrderService.recordAttachmentOperation(workOrderId, currentUser, "attachment_add", validated.originalFilename());
            return findVisibleAttachment(workOrderId, key.longValue());
        } catch (RuntimeException ex) {
            storageService.deleteQuietly(storedObject);
            throw ex;
        }
    }

    public WorkOrderAttachmentDownload download(Long workOrderId, Long attachmentId, CurrentUser currentUser) {
        workOrderService.requireVisibleWorkOrder(workOrderId, currentUser);
        StoredAttachment stored = findStoredAttachment(workOrderId, attachmentId);
        Resource resource = storageService.load(new AttachmentStorageService.StoredObject(
                stored.storageProvider(),
                stored.bucketName(),
                stored.objectKey()));
        return new WorkOrderAttachmentDownload(stored.response(), resource);
    }

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkOrderException("附件不能为空");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new WorkOrderException("附件大小不能超过 " + formatMaxSize());
        }
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (extension == null || DANGEROUS_EXTENSIONS.contains(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new WorkOrderException("附件类型不允许");
        }
        String contentType = normalizeContentType(file.getContentType());
        Set<String> allowedTypes = ALLOWED_CONTENT_TYPES.get(extension);
        if (allowedTypes == null || !allowedTypes.contains(contentType)) {
            throw new WorkOrderException("附件类型不允许");
        }
        return new ValidatedFile(originalFilename, extension, contentType, file.getSize());
    }

    private String sanitizeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new WorkOrderException("附件文件名不能为空");
        }
        String normalized = filename.replace("\\", "/");
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name) || name.contains("\u0000")) {
            throw new WorkOrderException("附件文件名不能为空");
        }
        if (name.length() > 255) {
            throw new WorkOrderException("附件文件名不能超过 255 个字符");
        }
        return name;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private WorkOrderAttachmentResponse findVisibleAttachment(Long workOrderId, Long attachmentId) {
        return findStoredAttachment(workOrderId, attachmentId).response();
    }

    private StoredAttachment findStoredAttachment(Long workOrderId, Long attachmentId) {
        try {
            boolean includeStorage = hasStorageColumns();
            String storageColumns = includeStorage
                    ? "a.storage_provider, a.bucket_name, a.object_key,"
                    : "'local' AS storage_provider, NULL AS bucket_name, a.stored_filename AS object_key,";
            return jdbcTemplate.queryForObject(
                    """
                    SELECT a.id, a.work_order_id, a.uploader_id, u.username AS uploader_username,
                           u.nickname AS uploader_nickname, a.original_filename, a.stored_filename,
                           """ + storageColumns + """
                           a.content_type, a.file_size, a.created_at
                    FROM work_order_attachments a
                    JOIN users u ON u.id = a.uploader_id
                    WHERE a.work_order_id = ? AND a.id = ?
                    """,
                    (rs, rowNum) -> new StoredAttachment(
                            new WorkOrderAttachmentResponse(
                                    rs.getLong("id"),
                                    rs.getLong("work_order_id"),
                                    rs.getLong("uploader_id"),
                                    rs.getString("uploader_username"),
                                    rs.getString("uploader_nickname"),
                                    rs.getString("original_filename"),
                                    rs.getString("content_type"),
                                    rs.getLong("file_size"),
                                    rs.getTimestamp("created_at").toInstant()),
                            rs.getString("stored_filename"),
                            rs.getString("storage_provider"),
                            rs.getString("bucket_name"),
                            rs.getString("object_key")),
                    workOrderId,
                    attachmentId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WorkOrderException("附件不存在");
        }
    }

    private WorkOrderAttachmentResponse mapAttachment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkOrderAttachmentResponse(
                rs.getLong("id"),
                rs.getLong("work_order_id"),
                rs.getLong("uploader_id"),
                rs.getString("uploader_username"),
                rs.getString("uploader_nickname"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Number generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().size() == 1 && keyHolder.getKeyList().getFirst().size() == 1) {
            return keyHolder.getKey();
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null) {
            return null;
        }
        Object id = keys.getOrDefault("id", keys.get("ID"));
        return id instanceof Number number ? number : null;
    }

    private String formatMaxSize() {
        if (maxSizeBytes % (1024 * 1024) == 0) {
            return (maxSizeBytes / (1024 * 1024)) + "MB";
        }
        return maxSizeBytes + " bytes";
    }

    private boolean hasStorageColumns() {
        if (storageColumnsAvailable != null) {
            return storageColumnsAvailable;
        }
        try {
            jdbcTemplate.queryForList("SELECT storage_provider, bucket_name, object_key FROM work_order_attachments WHERE 1 = 0");
            storageColumnsAvailable = true;
        } catch (RuntimeException ex) {
            storageColumnsAvailable = false;
        }
        return storageColumnsAvailable;
    }

    private record ValidatedFile(String originalFilename, String extension, String contentType, long size) {
    }

    private record StoredAttachment(
            WorkOrderAttachmentResponse response,
            String storedFilename,
            String storageProvider,
            String bucketName,
            String objectKey) {
    }
}
