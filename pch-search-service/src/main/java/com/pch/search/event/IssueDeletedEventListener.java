package com.pch.search.event;

import com.pch.common.event.IssueDeletedEvent;
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
public class IssueDeletedEventListener {

    private final SearchService searchService;

    @KafkaListener(topics = KafkaTopics.ISSUE_DELETED, groupId = "pch-search-service")
    public void handle(String message) {
        IssueDeletedEvent event = JsonUtil.fromJson(message, IssueDeletedEvent.class);
        log.info("[Event] ISSUE_DELETED: issueKey={}", event.getIssueKey());

        searchService.removeIssue(event.getIssueKey());
    }
}
