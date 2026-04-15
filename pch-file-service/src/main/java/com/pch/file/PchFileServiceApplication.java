package com.pch.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PchFileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PchFileServiceApplication.class, args);
    }
}
