package com.pch.boardreport.dto;

import com.pch.boardreport.domain.BoardCard;
import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;

public record BoardCardResponse(
    Long id,
    Long issueId,
    String issueKey,
    String summary,
    IssueStatus status,
    Priority priority,
    IssueType type,
    Long assigneeId,
    Integer cardOrder
) {
    public static BoardCardResponse from(BoardCard card) {
        return new BoardCardResponse(
            card.getId(), card.getIssueId(), card.getIssueKey(), card.getSummary(),
            card.getStatus(), card.getPriority(), card.getType(),
            card.getAssigneeId(), card.getCardOrder()
        );
    }
}
