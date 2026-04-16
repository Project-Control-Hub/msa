package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SprintIncompleteIssueDisposition {
    MOVE_TO_BACKLOG("Move to Backlog"),
    MOVE_TO_NEXT_SPRINT("Move to Next Sprint");

    private final String displayName;
}
