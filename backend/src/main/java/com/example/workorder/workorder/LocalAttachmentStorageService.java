package com.example.workorder.workorder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "app.attachments", name = "storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalAttachmentStorageService implements AttachmentStorageService {

    private final Path uploadDir;

    public LocalAttachmentStorageService(WorkOrderAttachmentProperties properties) {
        this.uploadDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file, String contentType, long size) {
        ensureUploadDirectory();
        Path target = uploadDir.resolve(objectKey).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new WorkOrderException("附件保存路径不正确");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredObject(provider(), null, objectKey);
        } catch (IOException ex) {
            throw new WorkOrderException("保存附件失败");
        }
    }

    @Override
    public Resource load(StoredObject object) {
        Path path = uploadDir.resolve(object.objectKey()).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path)) {
            throw new WorkOrderException("附件文件不存在");
        }
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException ex) {
            throw new WorkOrderException("附件文件不存在");
        }
    }

    @Override
    public void deleteQuietly(StoredObject object) {
        try {
            Path path = uploadDir.resolve(object.objectKey()).normalize();
            if (path.startsWith(uploadDir)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public String provider() {
        return "local";
    }

    private void ensureUploadDirectory() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ex) {
            throw new WorkOrderException("附件目录不可用");
        }
    }
}
