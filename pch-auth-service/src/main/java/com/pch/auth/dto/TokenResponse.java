package com.pch.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String email,
        String name
) {}
