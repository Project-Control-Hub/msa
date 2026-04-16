package com.pch.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class PchDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PchDiscoveryApplication.class, args);
    }
}
