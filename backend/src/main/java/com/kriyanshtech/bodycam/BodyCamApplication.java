package com.kriyanshtech.bodycam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.kriyanshtech.bodycam.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class BodyCamApplication {

    private static final Logger log = LoggerFactory.getLogger(BodyCamApplication.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BodyCamApplication.class);
        application.setRegisterShutdownHook(false);

        ConfigurableApplicationContext context = application.run(args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, closing body-cam backend");
            if (context.isActive()) {
                context.close();
            }
        }, "bodycam-backend-shutdown"));
    }
}
