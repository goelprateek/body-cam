package com.kriyanshtech.bodycam.recording.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.kriyanshtech.bodycam.config.AppProperties;

import java.io.InputStream;

@Service
public class ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

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
            log.info(
                    "Uploading recording object to bucket={} key={} sizeBytes={} contentType={}",
                    appProperties.storage().bucket(),
                    objectKey,
                    size,
                    contentType
            );
            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.storage().bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info(
                    "Uploaded recording object to bucket={} key={}",
                    appProperties.storage().bucket(),
                    objectKey
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to upload recording object to bucket={} key={}",
                    appProperties.storage().bucket(),
                    objectKey,
                    exception
            );
            throw new IllegalStateException("Failed to upload recording segment", exception);
        }
    }

    public String presignedPlaybackUrl(String objectKey, int expirySeconds) {
        try {
            String playbackUrl = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(appProperties.storage().bucket())
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
            log.info(
                    "Generated playback URL for recording key={} expirySeconds={}",
                    objectKey,
                    expirySeconds
            );
            return playbackUrl;
        } catch (Exception exception) {
            log.error("Failed to generate playback URL for recording key={}", objectKey, exception);
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
            log.info("Recording bucket {} not found. Creating it now.", appProperties.storage().bucket());
            internalMinioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(appProperties.storage().bucket())
                            .build()
            );
            log.info("Created recording bucket {}", appProperties.storage().bucket());
        }
    }
}
