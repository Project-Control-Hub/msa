package com.pch.common.security;

/**
 * Gateway가 JWT 검증 후 다운스트림 서비스에 전달하는 인증된 사용자 정보.
 * HttpHeaders: X-User-Id, X-User-Email 에서 읽는다.
 */
public record CurrentUser(Long userId, String email) {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
}
