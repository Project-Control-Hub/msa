package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IssueType {
    EPIC("Epic"),
    STORY("Story"),
    TASK("Task"),
    BUG("Bug"),
    SUB_TASK("Sub-Task");

    private final String displayName;
}
