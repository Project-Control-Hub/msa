package com.pch.notification.dto;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.NotificationPreference;

public record PreferenceResponse(
        Channel channel,
        boolean enabled
) {
    public static PreferenceResponse from(NotificationPreference p) {
        return new PreferenceResponse(p.getChannel(), p.isEnabled());
    }
}
