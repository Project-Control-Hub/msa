package com.pch.project.dto;

import com.pch.common.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotNull Long userId,
        @NotNull ProjectRole role
) {}
