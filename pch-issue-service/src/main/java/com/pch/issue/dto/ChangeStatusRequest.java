package com.pch.issue.dto;

import com.pch.common.enums.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull IssueStatus status
) {}
