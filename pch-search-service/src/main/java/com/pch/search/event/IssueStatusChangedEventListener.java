package com.pch.search.event;

import com.pch.common.event.IssueStatusChangedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import com.pch.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueStatusChangedEventListener {

    private final SearchService searchService;

    @KafkaListener(topics = KafkaTopics.ISSUE_STATUS_CHANGED, groupId = "pch-search-service")
    public void handle(String message) {
        IssueStatusChangedEvent event = JsonUtil.fromJson(message, IssueStatusChangedEvent.class);
        log.info("[Event] ISSUE_STATUS_CHANGED: issueKey={}, {} -> {}",
                event.getIssueKey(), event.getFromStatus(), event.getToStatus());

        searchService.updateIssueStatus(event.getIssueKey(),
                event.getToStatus() != null ? event.getToStatus().name() : null);
    }
}
