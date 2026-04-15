package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VcsProvider {
    GITHUB("GitHub"),
    GITLAB("GitLab"),
    BITBUCKET("Bitbucket");

    private final String displayName;
}
