package com.pch.search.dto;

import com.pch.search.domain.IssueDocument;

import java.time.Instant;

public record SearchResponse(
        String issueKey,
        Long issueId,
        Long projectId,
        String projectKey,
        String summary,
        String type,
        String status,
        String priority,
        Long assigneeId,
        Long reporterId,
        Instant createdAt
) {
    public static SearchResponse from(IssueDocument doc) {
        return new SearchResponse(
                doc.getIssueKey(), doc.getIssueId(), doc.getProjectId(),
                doc.getProjectKey(), doc.getSummary(), doc.getType(),
                doc.getStatus(), doc.getPriority(), doc.getAssigneeId(),
                doc.getReporterId(), doc.getCreatedAt()
        );
    }
}
