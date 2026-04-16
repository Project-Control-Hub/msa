package com.pch.notification.dto;

import com.pch.notification.domain.Channel;
import jakarta.validation.constraints.NotNull;

public record PreferenceRequest(
        @NotNull Channel channel,
        boolean enabled
) {}
