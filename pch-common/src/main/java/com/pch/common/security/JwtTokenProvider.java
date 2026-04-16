package com.pch.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 발급/검증 공용 유틸.
 * <p>
 * - pch-auth-service: access/refresh 토큰 발급에 사용
 * - pch-gateway: JwtAuthenticationFilter에서 검증에 사용
 * <p>
 * 시크릿은 각 서비스의 application.yml (jwt.secret) 에서 주입한다.
 */
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(String secret, long accessTokenValiditySeconds, long refreshTokenValiditySeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, TOKEN_TYPE_ACCESS, accessTokenValiditySeconds);
    }

    public String createRefreshToken(Long userId, String email) {
        return createToken(userId, email, TOKEN_TYPE_REFRESH, refreshTokenValiditySeconds);
    }

    private String createToken(Long userId, String email, String type, long validitySeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + validitySeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    public Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getPayload().getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).getPayload().get(CLAIM_EMAIL, String.class);
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(parse(token).getPayload().get(CLAIM_TOKEN_TYPE, String.class));
    }
}
