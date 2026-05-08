package com.company.bodycam;

import com.company.bodycam.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BodyCamApplication {

    public static void main(String[] args) {
        SpringApplication.run(BodyCamApplication.class, args);
    }
}
