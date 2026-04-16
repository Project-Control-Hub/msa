package com.pch.notification.dto;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.Notification;
import com.pch.notification.domain.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Channel channel,
        String title,
        String content,
        String linkUrl,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getChannel(),
                n.getTitle(), n.getContent(), n.getLinkUrl(),
                n.isRead(), n.getCreatedAt()
        );
    }
}
