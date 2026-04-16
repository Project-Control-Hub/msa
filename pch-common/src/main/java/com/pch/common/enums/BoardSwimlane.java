package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BoardSwimlane {
    NONE("None"),
    ASSIGNEE("Assignee"),
    PRIORITY("Priority"),
    ISSUE_TYPE("Issue Type");

    private final String displayName;
}
