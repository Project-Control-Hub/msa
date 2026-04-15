package com.pch.issue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class PchIssueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PchIssueServiceApplication.class, args);
    }
}
