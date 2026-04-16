package com.pch.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueDeletedEvent extends DomainEvent {
    private Long issueId;
    private String issueKey;

    public IssueDeletedEvent(Long issueId, String issueKey, String source) {
        super("IssueDeleted", source);
        this.issueId = issueId;
        this.issueKey = issueKey;
    }

    public IssueDeletedEvent(Long issueId, String issueKey, String source, String correlationId) {
        super("IssueDeleted", source, correlationId);
        this.issueId = issueId;
        this.issueKey = issueKey;
    }
}
