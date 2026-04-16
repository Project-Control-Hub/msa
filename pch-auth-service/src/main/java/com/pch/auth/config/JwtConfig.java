package com.pch.auth.config;

import com.pch.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds:1800}") long accessTokenValiditySeconds,
            @Value("${jwt.refresh-token-validity-seconds:604800}") long refreshTokenValiditySeconds) {
        return new JwtTokenProvider(secret, accessTokenValiditySeconds, refreshTokenValiditySeconds);
    }
}
