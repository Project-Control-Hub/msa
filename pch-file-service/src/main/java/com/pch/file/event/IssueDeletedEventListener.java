package com.pch.file.event;

import com.pch.common.event.IssueDeletedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.util.JsonUtil;
import com.pch.file.domain.OwnerType;
import com.pch.file.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IssueDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(IssueDeletedEventListener.class);

    private final AttachmentService attachmentService;

    @KafkaListener(topics = KafkaTopics.ISSUE_DELETED, groupId = "pch-file-service")
    public void handle(String message) {
        try {
            IssueDeletedEvent event = JsonUtil.fromJson(message, IssueDeletedEvent.class);
            log.info("IssueDeleted 이벤트 수신: issueId={}, issueKey={}", event.getIssueId(), event.getIssueKey());
            int count = attachmentService.softDeleteByOwner(OwnerType.ISSUE, event.getIssueId());
            log.info("이슈 첨부파일 soft-delete 완료: issueId={}, count={}", event.getIssueId(), count);
        } catch (Exception e) {
            log.error("IssueDeleted 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }
}
