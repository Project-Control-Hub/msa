package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VersionStatus {
    UNRELEASED("Unreleased"),
    RELEASED("Released"),
    ARCHIVED("Archived");

    private final String displayName;
}
