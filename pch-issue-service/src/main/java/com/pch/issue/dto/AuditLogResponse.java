package com.pch.issue.dto;

import com.pch.issue.domain.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long issueId,
        String issueKey,
        String action,
        String changedFields,
        Long actorId,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getIssueId(), log.getIssueKey(),
                log.getAction(), log.getChangedFields(),
                log.getActorId(), log.getCreatedAt()
        );
    }
}
