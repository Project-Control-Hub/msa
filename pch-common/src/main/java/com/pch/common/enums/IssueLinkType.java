package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IssueLinkType {
    BLOCKS("Blocks"),
    IS_BLOCKED_BY("Is Blocked By"),
    DUPLICATES("Duplicates"),
    IS_DUPLICATED_BY("Is Duplicated By"),
    RELATES_TO("Relates To");

    private final String displayName;
}
