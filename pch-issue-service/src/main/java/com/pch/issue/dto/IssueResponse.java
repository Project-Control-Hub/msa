package com.pch.issue.dto;

import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import com.pch.issue.domain.Issue;

import java.time.LocalDateTime;

public record IssueResponse(
        Long id,
        String issueKey,
        Long projectId,
        String projectKey,
        String summary,
        String description,
        IssueType type,
        IssueStatus status,
        Priority priority,
        Long sprintId,
        Long assigneeId,
        Long reporterId,
        Long parentIssueId,
        Integer storyPoints,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IssueResponse from(Issue issue) {
        return new IssueResponse(
                issue.getId(), issue.getIssueKey(), issue.getProjectId(),
                issue.getProjectKey(), issue.getSummary(), issue.getDescription(),
                issue.getType(), issue.getStatus(), issue.getPriority(),
                issue.getSprintId(), issue.getAssigneeId(), issue.getReporterId(),
                issue.getParentIssueId(), issue.getStoryPoints(),
                issue.getCreatedAt(), issue.getUpdatedAt()
        );
    }
}
