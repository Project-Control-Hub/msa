package com.pch.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectMemberEvent extends DomainEvent {
    private Long projectId;
    private String projectKey;
    private Long userId;
    private String role;
    private String action; // ADDED or REMOVED

    public ProjectMemberEvent(Long projectId, String projectKey, Long userId, String role,
                               String action, String source) {
        super("ProjectMember" + action, source);
        this.projectId = projectId;
        this.projectKey = projectKey;
        this.userId = userId;
        this.role = role;
        this.action = action;
    }
}
