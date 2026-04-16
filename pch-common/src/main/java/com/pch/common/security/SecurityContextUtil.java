package com.pch.common.security;

import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Gateway가 주입한 헤더로부터 현재 인증된 사용자를 읽는 유틸.
 * 다운스트림 서비스에서 Spring Security를 쓰지 않고 헤더만으로 인증된 사용자를 식별한다.
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static CurrentUser getCurrentUser() {
        HttpServletRequest request = currentRequest();
        String userIdHeader = request.getHeader(CurrentUser.HEADER_USER_ID);
        String email = request.getHeader(CurrentUser.HEADER_USER_EMAIL);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return new CurrentUser(Long.valueOf(userIdHeader), email);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return attributes.getRequest();
    }
}
