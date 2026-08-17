package com.example.workorder.workorder;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "app.attachments", name = "storage-provider", havingValue = "minio")
public class MinioAttachmentStorageService implements AttachmentStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioAttachmentStorageService(WorkOrderAttachmentProperties properties) {
        WorkOrderAttachmentProperties.Minio minio = properties.minio();
        this.bucket = minio.bucket();
        this.minioClient = MinioClient.builder()
                .endpoint(minio.endpoint())
                .credentials(minio.accessKey(), minio.secretKey())
                .region(minio.region())
                .build();
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file, String contentType, long size) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(file.getInputStream(), size, -1)
                    .build());
            return new StoredObject(provider(), bucket, objectKey);
        } catch (Exception ex) {
            throw new WorkOrderException("保存附件失败");
        }
    }

    @Override
    public Resource load(StoredObject object) {
        try {
            return new InputStreamResource(minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName(object))
                    .object(object.objectKey())
                    .build()));
        } catch (Exception ex) {
            throw new WorkOrderException("附件文件不存在");
        }
    }

    @Override
    public void deleteQuietly(StoredObject object) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName(object))
                    .object(object.objectKey())
                    .build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public String provider() {
        return "minio";
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String bucketName(StoredObject object) {
        return object.bucket() == null || object.bucket().isBlank() ? bucket : object.bucket();
    }
}
