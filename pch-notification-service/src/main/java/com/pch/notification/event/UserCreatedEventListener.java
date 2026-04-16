package com.pch.notification.event;

import com.pch.common.event.UserCreatedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import com.pch.notification.domain.Channel;
import com.pch.notification.domain.NotificationType;
import com.pch.notification.service.NotificationDispatcher;
import com.pch.notification.service.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventListener {

    private final NotificationDispatcher dispatcher;
    private final EventDeduplicator dedup;

    @KafkaListener(topics = KafkaTopics.USER_CREATED, groupId = "pch-notification-service")
    public void on(String payload) {
        UserCreatedEvent event = JsonUtil.fromJson(payload, UserCreatedEvent.class);
        if (!dedup.firstSeen(event.getEventId())) {
            log.debug("Duplicate event skipped: {}", event.getEventId());
            return;
        }

        log.info("Handling UserCreatedEvent: userId={}", event.getUserId());
        dispatcher.dispatch(NotificationMessage.of(
                event.getUserId(),
                NotificationType.WELCOME,
                "환영합니다, " + event.getName() + "님!",
                "PCH 프로젝트 관리 서비스에 가입해 주셔서 감사합니다.",
                null,
                event.getEventId()
        ));
    }
}
