package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectRole {
    ADMIN("Admin"),
    MANAGER("Manager"),
    DEVELOPER("Developer"),
    VIEWER("Viewer");

    private final String displayName;
}
