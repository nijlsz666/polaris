package com.polaris.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PolarisApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolarisApplication.class, args);
    }
}
