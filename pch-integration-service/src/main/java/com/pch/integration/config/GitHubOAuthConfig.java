package com.pch.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "github.oauth")
@Getter
@Setter
public class GitHubOAuthConfig {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
