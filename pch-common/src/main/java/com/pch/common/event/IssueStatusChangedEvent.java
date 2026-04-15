package com.pch.common.event;

import com.pch.common.enums.IssueStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueStatusChangedEvent extends DomainEvent {
    private Long issueId;
    private String issueKey;
    private Long projectId;
    private Long sprintId;
    private IssueStatus fromStatus;
    private IssueStatus toStatus;
    private Long changedBy;

    public IssueStatusChangedEvent(Long issueId, String issueKey, Long projectId, Long sprintId,
                                   IssueStatus fromStatus, IssueStatus toStatus, Long changedBy, String source) {
        super("IssueStatusChanged", source);
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectId = projectId;
        this.sprintId = sprintId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
    }

    public IssueStatusChangedEvent(Long issueId, String issueKey, Long projectId, Long sprintId,
                                   IssueStatus fromStatus, IssueStatus toStatus, Long changedBy, String source, String correlationId) {
        super("IssueStatusChanged", source, correlationId);
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectId = projectId;
        this.sprintId = sprintId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
    }
}
