package com.pch.search.event;

import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import com.pch.search.domain.IssueDocument;
import com.pch.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueCreatedEventListener {

    private final SearchService searchService;

    @KafkaListener(topics = KafkaTopics.ISSUE_CREATED, groupId = "pch-search-service")
    public void handle(String message) {
        IssueCreatedEvent event = JsonUtil.fromJson(message, IssueCreatedEvent.class);
        log.info("[Event] ISSUE_CREATED: issueKey={}", event.getIssueKey());

        IssueDocument doc = IssueDocument.builder()
                .issueKey(event.getIssueKey())
                .issueId(event.getIssueId())
                .projectId(event.getProjectId())
                .type(event.getIssueType() != null ? event.getIssueType().name() : null)
                .status(event.getStatus() != null ? event.getStatus().name() : null)
                .assigneeId(event.getAssigneeId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        searchService.indexIssue(doc);
    }
}
