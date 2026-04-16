package com.pch.notification.sender;

import com.pch.notification.domain.Channel;
import com.pch.notification.domain.Notification;
import com.pch.notification.repository.NotificationRepository;
import com.pch.notification.service.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppSender implements NotificationSender {

    private final NotificationRepository notificationRepository;

    @Override
    public Channel channel() {
        return Channel.IN_APP;
    }

    @Override
    public void send(NotificationMessage message) {
        Notification notification = Notification.create(
                message.recipientId(),
                message.type(),
                Channel.IN_APP,
                message.title(),
                message.content(),
                message.linkUrl(),
                message.eventId()
        );
        notificationRepository.save(notification);
        log.debug("In-app notification saved: recipientId={}, type={}", message.recipientId(), message.type());
    }
}
