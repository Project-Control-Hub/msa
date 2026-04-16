package com.pch.search.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFilterRequest(
        @NotBlank String name,
        @NotBlank String jqlExpression
) {}
