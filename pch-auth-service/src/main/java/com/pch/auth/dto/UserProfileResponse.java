package com.pch.auth.dto;

import com.pch.auth.domain.AuthProvider;
import com.pch.auth.domain.User;
import com.pch.auth.domain.UserRole;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String profileImageUrl,
        UserRole role,
        AuthProvider authProvider,
        boolean isActive,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.getAuthProvider(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
