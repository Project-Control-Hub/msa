package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AutomationTriggerType {
    ISSUE_CREATED("Issue Created"),
    ISSUE_UPDATED("Issue Updated"),
    STATUS_CHANGED("Status Changed"),
    COMMENT_ADDED("Comment Added");

    private final String displayName;
}
