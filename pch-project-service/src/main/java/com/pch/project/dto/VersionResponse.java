package com.pch.project.dto;

import com.pch.common.enums.VersionStatus;
import com.pch.project.domain.Version;

public record VersionResponse(Long id, Long projectId, String name, VersionStatus status, String description) {
    public static VersionResponse from(Version v) {
        return new VersionResponse(v.getId(), v.getProjectId(), v.getName(), v.getStatus(), v.getDescription());
    }
}
