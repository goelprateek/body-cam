package com.kriyanshtech.bodycam.recording.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.kriyanshtech.bodycam.config.AppProperties;

import java.io.InputStream;

@Service
public class ObjectStorageService {

    private final AppProperties appProperties;
    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;

    public ObjectStorageService(
            AppProperties appProperties,
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient
    ) {
        this.appProperties = appProperties;
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
    }

    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucket();
            internalMinioClient.putObject(
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

    public String presignedPlaybackUrl(String objectKey, int expirySeconds) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(appProperties.storage().bucket())
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create recording playback URL", exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = internalMinioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(appProperties.storage().bucket())
                        .build()
        );
        if (!exists) {
            internalMinioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(appProperties.storage().bucket())
                            .build()
            );
        }
    }
}
