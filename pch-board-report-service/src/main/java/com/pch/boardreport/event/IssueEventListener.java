package com.pch.boardreport.event;

import com.pch.boardreport.service.BoardService;
import com.pch.boardreport.service.BurndownService;
import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.event.IssueDeletedEvent;
import com.pch.common.event.IssueStatusChangedEvent;
import com.pch.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueEventListener {

    private final BoardService boardService;
    private final BurndownService burndownService;

    @KafkaListener(topics = KafkaTopics.ISSUE_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onIssueCreated(IssueCreatedEvent event) {
        log.info("[Kafka] issue.created: {}", event.getIssueKey());
        boardService.syncBoardCard(event);
    }

    @KafkaListener(topics = KafkaTopics.ISSUE_STATUS_CHANGED, groupId = "${spring.kafka.consumer.group-id}")
    public void onIssueStatusChanged(IssueStatusChangedEvent event) {
        log.info("[Kafka] issue.status-changed: {} → {}", event.getIssueKey(), event.getToStatus());
        boardService.syncBoardCard(event);
        if (event.getSprintId() != null) {
            burndownService.recalculate(event.getSprintId());
        }
    }

    @KafkaListener(topics = KafkaTopics.ISSUE_DELETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onIssueDeleted(IssueDeletedEvent event) {
        log.info("[Kafka] issue.deleted: {}", event.getIssueKey());
        boardService.removeBoardCard(event);
    }
}
