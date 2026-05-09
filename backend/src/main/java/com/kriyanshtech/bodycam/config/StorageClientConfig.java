package com.kriyanshtech.bodycam.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageClientConfig {

    @Bean
    OkHttpClient minioHttpClient() {
        return new OkHttpClient.Builder().build();
    }

    @Bean
    @Qualifier("internalMinioClient")
    MinioClient internalMinioClient(AppProperties appProperties, OkHttpClient minioHttpClient) {
        return MinioClient.builder()
                .endpoint(appProperties.storage().endpoint())
                .region(appProperties.storage().region())
                .credentials(appProperties.storage().accessKey(), appProperties.storage().secretKey())
                .httpClient(minioHttpClient)
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    MinioClient publicMinioClient(AppProperties appProperties, OkHttpClient minioHttpClient) {
        return MinioClient.builder()
                .endpoint(appProperties.storage().publicUrl())
                .region(appProperties.storage().region())
                .credentials(appProperties.storage().accessKey(), appProperties.storage().secretKey())
                .httpClient(minioHttpClient)
                .build();
    }
}
