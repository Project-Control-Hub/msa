package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VcsLinkKind {
    COMMIT("Commit"),
    BRANCH("Branch"),
    PULL_REQUEST("Pull Request"),
    TAG("Tag");

    private final String displayName;
}
