package com.kriyanshtech.bodycam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.kriyanshtech.bodycam.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BodyCamApplication {

    public static void main(String[] args) {
        SpringApplication.run(BodyCamApplication.class, args);
    }
}
