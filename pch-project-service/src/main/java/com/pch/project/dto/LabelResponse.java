package com.pch.project.dto;

import com.pch.project.domain.Label;

public record LabelResponse(Long id, Long projectId, String name, String color) {
    public static LabelResponse from(Label l) {
        return new LabelResponse(l.getId(), l.getProjectId(), l.getName(), l.getColor());
    }
}
