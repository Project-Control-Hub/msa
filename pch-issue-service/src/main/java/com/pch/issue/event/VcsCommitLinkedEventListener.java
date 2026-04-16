package com.pch.issue.event;

import com.pch.common.event.VcsCommitLinkedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * VCS 커밋 연동 이벤트 수신 → 이슈에 VCS 링크 기록.
 * Phase 2에서 IssueVcsLink 엔티티 연동으로 확장 예정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VcsCommitLinkedEventListener {

    @KafkaListener(topics = KafkaTopics.VCS_COMMIT_LINKED, groupId = "pch-issue-service")
    public void handle(String message) {
        VcsCommitLinkedEvent event = JsonUtil.fromJson(message, VcsCommitLinkedEvent.class);
        log.info("[Event Consumed] VCS_COMMIT_LINKED: issueKey={}, commitSha={}, repo={}",
                event.getIssueKey(), event.getCommitSha(), event.getRepo());

        // TODO: IssueVcsLink 엔티티 저장 (Phase 2 후반부 구현)
    }
}
