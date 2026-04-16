package com.pch.gateway.config;

import com.pch.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JwtTokenProvider 빈 등록. 시크릿/유효기간은 application.yml 의 jwt.* 에서 주입.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds:1800}") long accessValidity,
            @Value("${jwt.refresh-token-validity-seconds:1209600}") long refreshValidity) {
        return new JwtTokenProvider(secret, accessValidity, refreshValidity);
    }
}
