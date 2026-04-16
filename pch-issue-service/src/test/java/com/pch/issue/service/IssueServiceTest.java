package com.pch.issue.service;

import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import com.pch.issue.domain.Issue;
import com.pch.issue.domain.IssueSequence;
import com.pch.issue.dto.CreateIssueRequest;
import com.pch.issue.dto.IssueResponse;
import com.pch.issue.event.DomainEventPublisherImpl;
import com.pch.issue.repository.AuditLogRepository;
import com.pch.issue.repository.IssueRepository;
import com.pch.issue.repository.IssueSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @InjectMocks private IssueService issueService;
    @Mock private IssueRepository issueRepository;
    @Mock private IssueSequenceRepository sequenceRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private DomainEventPublisherImpl eventPublisher;

    @Test
    @DisplayName("이슈 생성 시 이슈키 자동 채번 + IssueCreatedEvent 발행")
    void createIssue() {
        // given
        IssueSequence seq = IssueSequence.init("PCH");
        when(sequenceRepository.findByProjectKey("PCH")).thenReturn(Optional.of(seq));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateIssueRequest req = new CreateIssueRequest(
                1L, "PCH", "Test Issue", "desc",
                IssueType.TASK, Priority.MEDIUM, null, null, null);

        // when
        IssueResponse response = issueService.create(req, 10L);

        // then
        assertEquals("PCH-1", response.issueKey());
        assertEquals(IssueStatus.OPEN, response.status());
        assertEquals(10L, response.reporterId());
        verify(eventPublisher).publish(eq("issue.created"), any());
    }

    @Test
    @DisplayName("이슈 상태 변경 시 IssueStatusChangedEvent 발행")
    void changeStatus() {
        // given
        Issue issue = Issue.create("PCH-1", "PCH", 1L, "test", null,
                IssueType.TASK, Priority.MEDIUM, 10L);
        when(issueRepository.findByIssueKeyAndDeletedFalse("PCH-1"))
                .thenReturn(Optional.of(issue));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        IssueResponse response = issueService.changeStatus("PCH-1", IssueStatus.IN_PROGRESS, 10L);

        // then
        assertEquals(IssueStatus.IN_PROGRESS, response.status());
        verify(eventPublisher).publish(eq("issue.status-changed"), any());
    }

    @Test
    @DisplayName("이슈 삭제(soft-delete) 시 IssueDeletedEvent 발행")
    void deleteIssue() {
        // given
        Issue issue = Issue.create("PCH-2", "PCH", 1L, "test", null,
                IssueType.BUG, Priority.HIGH, 10L);
        when(issueRepository.findByIssueKeyAndDeletedFalse("PCH-2"))
                .thenReturn(Optional.of(issue));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        issueService.delete("PCH-2", 10L);

        // then
        assertTrue(issue.isDeleted());
        verify(eventPublisher).publish(eq("issue.deleted"), any());
    }

    @Test
    @DisplayName("스프린트 완료 시 미완료 이슈 백로그 이동")
    void moveIncompleteIssuesToBacklog() {
        // given
        Issue openIssue = Issue.create("PCH-3", "PCH", 1L, "open issue", null,
                IssueType.TASK, Priority.MEDIUM, 10L);
        openIssue.moveToSprint(5L);
        when(issueRepository.findBySprintIdAndStatusNotAndDeletedFalse(5L, IssueStatus.DONE))
                .thenReturn(java.util.List.of(openIssue));

        // when
        issueService.moveIncompleteIssuesToBacklog(5L);

        // then
        assertNull(openIssue.getSprintId());
    }
}
