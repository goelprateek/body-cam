package com.kriyanshtech.bodycam.recording.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import com.kriyanshtech.bodycam.config.AppProperties;

import java.io.InputStream;

@Service
public class ObjectStorageService {

    private final AppProperties appProperties;
    private final MinioClient minioClient;

    public ObjectStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(appProperties.storage().endpoint())
                .credentials(appProperties.storage().accessKey(), appProperties.storage().secretKey())
                .build();
    }

    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.storage().bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload recording segment", exception);
        }
    }

    public String playbackUrl(String objectKey) {
        String baseUrl = appProperties.storage().publicUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/" + appProperties.storage().bucket() + "/" + objectKey;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(appProperties.storage().bucket())
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(appProperties.storage().bucket())
                            .build()
            );
        }
    }
}
