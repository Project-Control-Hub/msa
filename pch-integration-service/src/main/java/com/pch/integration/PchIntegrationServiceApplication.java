package com.pch.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PchIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PchIntegrationServiceApplication.class, args);
    }
}
