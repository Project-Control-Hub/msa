package com.pch.issue.dto;

import com.pch.common.enums.Priority;

public record UpdateIssueRequest(
        String summary,
        String description,
        Priority priority,
        Integer storyPoints
) {}
