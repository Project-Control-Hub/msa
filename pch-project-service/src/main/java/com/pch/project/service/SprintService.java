package com.pch.project.service;

import com.pch.common.enums.SprintIncompleteIssueDisposition;
import com.pch.common.event.SprintCompletedEvent;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.kafka.KafkaTopics;
import com.pch.project.domain.Project;
import com.pch.project.domain.Sprint;
import com.pch.project.dto.CreateSprintRequest;
import com.pch.project.dto.SprintResponse;
import com.pch.project.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SprintService {

    private static final Logger log = LoggerFactory.getLogger(SprintService.class);

    private final SprintRepository sprintRepository;
    private final ProjectService projectService;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public SprintResponse create(String projectKey, CreateSprintRequest request) {
        Project project = projectService.findActiveByKey(projectKey);
        Sprint sprint = Sprint.create(project.getId(), request.name(), request.goal(),
                request.startDate(), request.endDate());
        sprintRepository.save(sprint);
        log.info("스프린트 생성: projectKey={}, sprintName={}", projectKey, request.name());
        return SprintResponse.from(sprint);
    }

    public List<SprintResponse> getByProject(String projectKey) {
        Project project = projectService.findActiveByKey(projectKey);
        return sprintRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .map(SprintResponse::from)
                .toList();
    }

    @Transactional
    public SprintResponse start(Long sprintId) {
        Sprint sprint = findById(sprintId);
        sprint.start();
        log.info("스프린트 시작: id={}", sprintId);
        return SprintResponse.from(sprint);
    }

    @Transactional
    public SprintResponse complete(Long sprintId, SprintIncompleteIssueDisposition disposition) {
        Sprint sprint = findById(sprintId);
        sprint.complete();

        eventPublisher.publish(KafkaTopics.SPRINT_COMPLETED,
                new SprintCompletedEvent(sprint.getId(), sprint.getProjectId(), disposition, "project-service"));

        log.info("스프린트 완료: id={}, disposition={}", sprintId, disposition);
        return SprintResponse.from(sprint);
    }

    private Sprint findById(Long id) {
        return sprintRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
