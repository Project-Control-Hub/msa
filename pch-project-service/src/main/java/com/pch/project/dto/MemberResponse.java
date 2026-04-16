package com.pch.project.dto;

import com.pch.common.enums.ProjectRole;
import com.pch.project.domain.ProjectMember;

public record MemberResponse(Long id, Long projectId, Long userId, ProjectRole role) {
    public static MemberResponse from(ProjectMember m) {
        return new MemberResponse(m.getId(), m.getProjectId(), m.getUserId(), m.getRole());
    }
}
