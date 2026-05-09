package com.kriyanshtech.bodycam.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ShutdownCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ShutdownCoordinator.class);

    private final DataSource dataSource;
    private final OkHttpClient minioHttpClient;

    public ShutdownCoordinator(DataSource dataSource, OkHttpClient minioHttpClient) {
        this.dataSource = dataSource;
        this.minioHttpClient = minioHttpClient;
    }

    @PreDestroy
    public void shutdownResources() {
        log.info("Shutting down backend resources");
        shutdownMinioHttpClient();
        shutdownDataSource();
    }

    private void shutdownMinioHttpClient() {
        minioHttpClient.dispatcher().executorService().shutdown();
        minioHttpClient.connectionPool().evictAll();
        if (minioHttpClient.cache() != null) {
            try {
                minioHttpClient.cache().close();
            } catch (Exception exception) {
                log.warn("Failed to close MinIO HTTP cache cleanly", exception);
            }
        }
    }

    private void shutdownDataSource() {
        if (dataSource instanceof HikariDataSource hikariDataSource && !hikariDataSource.isClosed()) {
            hikariDataSource.close();
            log.info("Closed datasource connection pool");
        }
    }
}
