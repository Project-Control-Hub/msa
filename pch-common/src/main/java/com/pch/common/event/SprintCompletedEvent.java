package com.pch.common.event;

import com.pch.common.enums.SprintIncompleteIssueDisposition;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SprintCompletedEvent extends DomainEvent {
    private Long sprintId;
    private Long projectId;
    private SprintIncompleteIssueDisposition disposition;

    public SprintCompletedEvent(Long sprintId, Long projectId, SprintIncompleteIssueDisposition disposition, String source) {
        super("SprintCompleted", source);
        this.sprintId = sprintId;
        this.projectId = projectId;
        this.disposition = disposition;
    }

    public SprintCompletedEvent(Long sprintId, Long projectId, SprintIncompleteIssueDisposition disposition, String source, String correlationId) {
        super("SprintCompleted", source, correlationId);
        this.sprintId = sprintId;
        this.projectId = projectId;
        this.disposition = disposition;
    }
}
