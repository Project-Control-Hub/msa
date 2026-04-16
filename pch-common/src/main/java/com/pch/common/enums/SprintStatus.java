package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SprintStatus {
    PLANNING("Planning"),
    ACTIVE("Active"),
    COMPLETED("Completed");

    private final String displayName;
}
