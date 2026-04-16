package com.pch.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(min = 2, max = 10) @Pattern(regexp = "^[A-Z0-9]+$", message = "대문자+숫자만 허용")
        String projectKey,
        @NotBlank @Size(max = 200)
        String name,
        @Size(max = 2000)
        String description
) {}
