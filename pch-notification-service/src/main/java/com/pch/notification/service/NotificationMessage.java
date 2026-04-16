package com.pch.notification.service;

import com.pch.notification.domain.NotificationType;

public record NotificationMessage(
        Long recipientId,
        NotificationType type,
        String title,
        String content,
        String linkUrl,
        String eventId
) {
    public static NotificationMessage of(Long recipientId, NotificationType type,
                                         String title, String content, String linkUrl, String eventId) {
        return new NotificationMessage(recipientId, type, title, content, linkUrl, eventId);
    }
}
