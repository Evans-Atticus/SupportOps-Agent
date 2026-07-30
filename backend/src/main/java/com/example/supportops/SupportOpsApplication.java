package com.example.supportops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportOpsApplication.class, args);
    }
}
