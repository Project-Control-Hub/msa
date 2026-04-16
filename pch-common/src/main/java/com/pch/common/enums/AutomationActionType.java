package com.pch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AutomationActionType {
    CHANGE_STATUS("Change Status"),
    ASSIGN_USER("Assign User"),
    ADD_LABEL("Add Label"),
    SEND_NOTIFICATION("Send Notification");

    private final String displayName;
}
