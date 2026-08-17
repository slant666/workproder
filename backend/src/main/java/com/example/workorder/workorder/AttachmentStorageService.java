package com.example.workorder.workorder;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorageService {

    StoredObject store(String objectKey, MultipartFile file, String contentType, long size);

    Resource load(StoredObject object);

    void deleteQuietly(StoredObject object);

    String provider();

    record StoredObject(String provider, String bucket, String objectKey) {
    }
}
