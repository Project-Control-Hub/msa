package com.pch.issue.event;

import com.pch.common.event.SprintCompletedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import com.pch.issue.service.IssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 스프린트 완료 이벤트 수신 → 미완료 이슈 백로그 이동 (Saga 참여자).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SprintCompletedEventListener {

    private final IssueService issueService;

    @KafkaListener(topics = KafkaTopics.SPRINT_COMPLETED, groupId = "pch-issue-service")
    public void handle(String message) {
        SprintCompletedEvent event = JsonUtil.fromJson(message, SprintCompletedEvent.class);
        log.info("[Event Consumed] SPRINT_COMPLETED: sprintId={}, projectId={}",
                event.getSprintId(), event.getProjectId());

        issueService.moveIncompleteIssuesToBacklog(event.getSprintId());
    }
}
