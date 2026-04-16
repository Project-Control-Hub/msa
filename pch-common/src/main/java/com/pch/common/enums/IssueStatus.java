package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IssueStatus {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    CODE_REVIEW("Code Review"),
    IN_TEST("In Test"),
    DONE("Done"),
    CLOSED("Closed");

    private final String displayName;
}
