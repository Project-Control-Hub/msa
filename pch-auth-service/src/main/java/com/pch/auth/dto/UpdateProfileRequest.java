package com.pch.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하입니다.")
        String name,

        @Size(max = 500, message = "프로필 이미지 URL은 500자 이하입니다.")
        String profileImageUrl
) {}
