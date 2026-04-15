package com.pch.common.event;

import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueCreatedEvent extends DomainEvent {
    private Long issueId;
    private String issueKey;
    private Long projectId;
    private IssueType issueType;
    private IssueStatus status;
    private Long assigneeId;

    public IssueCreatedEvent(Long issueId, String issueKey, Long projectId, IssueType issueType,
                             IssueStatus status, Long assigneeId, String source) {
        super("IssueCreated", source);
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectId = projectId;
        this.issueType = issueType;
        this.status = status;
        this.assigneeId = assigneeId;
    }

    public IssueCreatedEvent(Long issueId, String issueKey, Long projectId, IssueType issueType,
                             IssueStatus status, Long assigneeId, String source, String correlationId) {
        super("IssueCreated", source, correlationId);
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectId = projectId;
        this.issueType = issueType;
        this.status = status;
        this.assigneeId = assigneeId;
    }
}
