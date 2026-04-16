package com.pch.search.dto;

import com.pch.search.domain.SavedFilter;

import java.time.LocalDateTime;

public record FilterResponse(
        Long id,
        Long userId,
        String name,
        String jqlExpression,
        boolean isDefault,
        LocalDateTime createdAt
) {
    public static FilterResponse from(SavedFilter f) {
        return new FilterResponse(
                f.getId(), f.getUserId(), f.getName(),
                f.getJqlExpression(), f.isDefault(), f.getCreatedAt()
        );
    }
}
