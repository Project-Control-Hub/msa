package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BoardType {
    SCRUM("Scrum"),
    KANBAN("Kanban");

    private final String displayName;
}
