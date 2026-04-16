package com.pch.issue.service;

import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.enums.Priority;
import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.event.IssueDeletedEvent;
import com.pch.common.event.IssueStatusChangedEvent;
import com.pch.common.kafka.KafkaTopics;
import com.pch.issue.domain.AuditLog;
import com.pch.issue.domain.Issue;
import com.pch.issue.domain.IssueSequence;
import com.pch.issue.dto.*;
import com.pch.issue.event.DomainEventPublisherImpl;
import com.pch.issue.repository.AuditLogRepository;
import com.pch.issue.repository.IssueRepository;
import com.pch.issue.repository.IssueSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueSequenceRepository sequenceRepository;
    private final AuditLogRepository auditLogRepository;
    private final DomainEventPublisherImpl eventPublisher;

    @Transactional
    public IssueResponse create(CreateIssueRequest req, Long reporterId) {
        String issueKey = generateIssueKey(req.projectKey());

        Issue issue = Issue.create(
                issueKey, req.projectKey(), req.projectId(),
                req.summary(), req.description(),
                req.type() != null ? req.type() : IssueType.TASK,
                req.priority() != null ? req.priority() : Priority.MEDIUM,
                reporterId
        );

        if (req.assigneeId() != null) {
            issue.assign(req.assigneeId());
        }
        if (req.sprintId() != null) {
            issue.moveToSprint(req.sprintId());
        }
        if (req.parentIssueId() != null) {
            issue.setParent(req.parentIssueId());
        }

        Issue saved = issueRepository.save(issue);

        // 감사 로그
        auditLogRepository.save(AuditLog.create(
                saved.getId(), saved.getIssueKey(), "CREATED", null, reporterId));

        // 이벤트 발행
        eventPublisher.publish(KafkaTopics.ISSUE_CREATED,
                new IssueCreatedEvent(saved.getId(), saved.getIssueKey(),
                        saved.getProjectId(), saved.getType(), saved.getStatus(),
                        saved.getAssigneeId(), "issue-service"));

        return IssueResponse.from(saved);
    }

    public IssueResponse getByKey(String issueKey) {
        Issue issue = findByKeyOrThrow(issueKey);
        return IssueResponse.from(issue);
    }

    public Page<IssueResponse> getByProject(Long projectId, Pageable pageable) {
        return issueRepository.findByProjectIdAndDeletedFalse(projectId, pageable)
                .map(IssueResponse::from);
    }

    public List<IssueResponse> getBySprint(Long sprintId) {
        return issueRepository.findBySprintIdAndDeletedFalse(sprintId).stream()
                .map(IssueResponse::from)
                .toList();
    }

    @Transactional
    public IssueResponse update(String issueKey, UpdateIssueRequest req, Long actorId) {
        Issue issue = findByKeyOrThrow(issueKey);
        issue.update(req.summary(), req.description(), req.priority(), req.storyPoints());

        auditLogRepository.save(AuditLog.create(
                issue.getId(), issue.getIssueKey(), "UPDATED", null, actorId));

        return IssueResponse.from(issue);
    }

    @Transactional
    public IssueResponse changeStatus(String issueKey, IssueStatus newStatus, Long actorId) {
        Issue issue = findByKeyOrThrow(issueKey);
        IssueStatus oldStatus = issue.changeStatus(newStatus);

        auditLogRepository.save(AuditLog.create(
                issue.getId(), issue.getIssueKey(), "STATUS_CHANGED",
                "{\"from\":\"" + oldStatus + "\",\"to\":\"" + newStatus + "\"}",
                actorId));

        eventPublisher.publish(KafkaTopics.ISSUE_STATUS_CHANGED,
                new IssueStatusChangedEvent(issue.getId(), issue.getIssueKey(),
                        issue.getProjectId(), issue.getSprintId(),
                        oldStatus, newStatus, actorId, "issue-service"));

        return IssueResponse.from(issue);
    }

    @Transactional
    public IssueResponse assign(String issueKey, Long assigneeId, Long actorId) {
        Issue issue = findByKeyOrThrow(issueKey);
        issue.assign(assigneeId);

        auditLogRepository.save(AuditLog.create(
                issue.getId(), issue.getIssueKey(), "ASSIGNED",
                "{\"assigneeId\":" + assigneeId + "}", actorId));

        return IssueResponse.from(issue);
    }

    @Transactional
    public IssueResponse moveToSprint(String issueKey, Long sprintId, Long actorId) {
        Issue issue = findByKeyOrThrow(issueKey);
        issue.moveToSprint(sprintId);

        auditLogRepository.save(AuditLog.create(
                issue.getId(), issue.getIssueKey(), "SPRINT_MOVED",
                "{\"sprintId\":" + sprintId + "}", actorId));

        return IssueResponse.from(issue);
    }

    @Transactional
    public void delete(String issueKey, Long actorId) {
        Issue issue = findByKeyOrThrow(issueKey);
        issue.softDelete();

        auditLogRepository.save(AuditLog.create(
                issue.getId(), issue.getIssueKey(), "DELETED", null, actorId));

        eventPublisher.publish(KafkaTopics.ISSUE_DELETED,
                new IssueDeletedEvent(issue.getId(), issue.getIssueKey(), "issue-service"));
    }

    // ── 스프린트 완료 시 미완료 이슈 이동 (Saga 참여자) ──
    @Transactional
    public void moveIncompleteIssuesToBacklog(Long sprintId) {
        List<Issue> incomplete = issueRepository
                .findBySprintIdAndStatusNotAndDeletedFalse(sprintId, IssueStatus.DONE);
        for (Issue issue : incomplete) {
            issue.moveToSprint(null); // 백로그로 이동
        }
    }

    // ── Private Helpers ──
    private Issue findByKeyOrThrow(String issueKey) {
        return issueRepository.findByIssueKeyAndDeletedFalse(issueKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Issue not found: " + issueKey));
    }

    private String generateIssueKey(String projectKey) {
        IssueSequence seq = sequenceRepository.findByProjectKey(projectKey)
                .orElseGet(() -> sequenceRepository.save(IssueSequence.init(projectKey)));
        Long number = seq.nextNumber();
        return projectKey + "-" + number;
    }
}
