package com.pch.project.dto;

import com.pch.project.domain.Project;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id, String projectKey, String name, String description,
        Long leadUserId, boolean active, LocalDateTime createdAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getProjectKey(), p.getName(), p.getDescription(),
                p.getLeadUserId(), p.isActive(), p.getCreatedAt());
    }
}
