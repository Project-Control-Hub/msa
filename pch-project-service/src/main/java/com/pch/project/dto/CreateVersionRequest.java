package com.pch.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVersionRequest(@NotBlank String name, String description) {}
