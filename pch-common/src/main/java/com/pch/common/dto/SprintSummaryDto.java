package com.pch.common.dto;

import com.pch.common.enums.SprintStatus;

public record SprintSummaryDto(
    Long id,
    String name,
    SprintStatus status,
    Long projectId
) {
}
