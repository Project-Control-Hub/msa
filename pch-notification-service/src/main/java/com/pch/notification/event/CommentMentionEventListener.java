package com.pch.notification.event;

import com.pch.common.event.CommentMentionEvent;
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
public class CommentMentionEventListener {

    private final NotificationDispatcher dispatcher;
    private final EventDeduplicator dedup;

    @KafkaListener(topics = KafkaTopics.COMMENT_MENTIONED, groupId = "pch-notification-service")
    public void on(String payload) {
        CommentMentionEvent event = JsonUtil.fromJson(payload, CommentMentionEvent.class);
        if (!dedup.firstSeen(event.getEventId())) return;

        log.info("Handling CommentMentionEvent: mentionedUserId={}", event.getMentionedUserId());
        dispatcher.dispatch(NotificationMessage.of(
                event.getMentionedUserId(),
                NotificationType.COMMENT_MENTION,
                "댓글에서 멘션되었습니다",
                event.getCommentSnippet(),
                "/issues/" + event.getIssueId() + "#comment-" + event.getCommentId(),
                event.getEventId()
        ));
    }
}
