package com.pch.issue.dto;

import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIssueRequest(
        @NotNull Long projectId,
        @NotBlank String projectKey,
        @NotBlank String summary,
        String description,
        IssueType type,
        Priority priority,
        Long assigneeId,
        Long sprintId,
        Long parentIssueId
) {}
