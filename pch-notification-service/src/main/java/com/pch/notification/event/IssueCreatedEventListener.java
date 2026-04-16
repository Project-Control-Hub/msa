package com.pch.notification.event;

import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
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
public class IssueCreatedEventListener {

    private final NotificationDispatcher dispatcher;
    private final EventDeduplicator dedup;

    @KafkaListener(topics = KafkaTopics.ISSUE_CREATED, groupId = "pch-notification-service")
    public void on(String payload) {
        IssueCreatedEvent event = JsonUtil.fromJson(payload, IssueCreatedEvent.class);
        if (!dedup.firstSeen(event.getEventId())) return;

        log.info("Handling IssueCreatedEvent: issueId={}, assigneeId={}", event.getIssueId(), event.getAssigneeId());
        if (event.getAssigneeId() != null) {
            dispatcher.dispatch(NotificationMessage.of(
                    event.getAssigneeId(),
                    NotificationType.ISSUE_ASSIGNED,
                    "새 이슈가 배정되었습니다: " + event.getTitle(),
                    "이슈 #" + event.getIssueId() + "이(가) 당신에게 배정되었습니다.",
                    "/issues/" + event.getIssueId(),
                    event.getEventId()
            ));
        }
    }
}
