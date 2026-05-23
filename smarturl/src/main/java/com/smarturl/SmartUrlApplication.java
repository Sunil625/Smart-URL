package com.smarturl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartUrlApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartUrlApplication.class, args);
    }
}
