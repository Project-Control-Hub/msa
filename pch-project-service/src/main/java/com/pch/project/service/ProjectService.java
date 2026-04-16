package com.pch.project.service;

import com.pch.common.enums.ProjectRole;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.kafka.KafkaTopics;
import com.pch.project.domain.Project;
import com.pch.project.domain.ProjectMember;
import com.pch.project.dto.*;
import com.pch.project.repository.ProjectMemberRepository;
import com.pch.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public ProjectResponse create(Long userId, CreateProjectRequest request) {
        if (projectRepository.existsByProjectKey(request.projectKey().toUpperCase())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Project project = Project.create(request.projectKey(), request.name(), request.description(), userId);
        projectRepository.save(project);

        // 생성자를 ADMIN으로 자동 등록
        ProjectMember owner = ProjectMember.create(project.getId(), userId, ProjectRole.ADMIN);
        memberRepository.save(owner);

        log.info("프로젝트 생성: key={}, leadUserId={}", project.getProjectKey(), userId);
        return ProjectResponse.from(project);
    }

    public ProjectResponse getByKey(String key) {
        Project project = findActiveByKey(key);
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> getMyProjects(Long userId) {
        List<Long> projectIds = memberRepository.findByUserId(userId).stream()
                .map(ProjectMember::getProjectId)
                .toList();
        return projectRepository.findAllById(projectIds).stream()
                .filter(Project::isActive)
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public ProjectResponse update(String key, Long userId, UpdateProjectRequest request) {
        Project project = findActiveByKey(key);
        validateMemberAccess(project.getId(), userId, ProjectRole.ADMIN);
        project.update(request.name(), request.description());
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(String key, Long userId) {
        Project project = findActiveByKey(key);
        validateMemberAccess(project.getId(), userId, ProjectRole.ADMIN);
        project.deactivate();
        log.info("프로젝트 비활성화: key={}", key);
    }

    // ── 멤버 관리 ──

    @Transactional
    public MemberResponse addMember(String key, Long requesterId, AddMemberRequest request) {
        Project project = findActiveByKey(key);
        validateMemberAccess(project.getId(), requesterId, ProjectRole.ADMIN);

        if (memberRepository.existsByProjectIdAndUserId(project.getId(), request.userId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        ProjectMember member = ProjectMember.create(project.getId(), request.userId(), request.role());
        memberRepository.save(member);

        // 이벤트 발행
        var event = new com.pch.common.event.ProjectMemberEvent(
                project.getId(), project.getProjectKey(), request.userId(), request.role().name(),
                "ADDED", "project-service");
        eventPublisher.publish(KafkaTopics.PROJECT_MEMBER_ADDED, event);

        log.info("멤버 추가: projectKey={}, userId={}, role={}", key, request.userId(), request.role());
        return MemberResponse.from(member);
    }

    @Transactional
    public void removeMember(String key, Long requesterId, Long targetUserId) {
        Project project = findActiveByKey(key);
        validateMemberAccess(project.getId(), requesterId, ProjectRole.ADMIN);

        ProjectMember member = memberRepository.findByProjectIdAndUserId(project.getId(), targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // ADMIN이 최소 1명은 남아야 함
        if (member.getRole() == ProjectRole.ADMIN
                && memberRepository.countByProjectIdAndRole(project.getId(), ProjectRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        memberRepository.delete(member);

        var event = new com.pch.common.event.ProjectMemberEvent(
                project.getId(), project.getProjectKey(), targetUserId, member.getRole().name(),
                "REMOVED", "project-service");
        eventPublisher.publish(KafkaTopics.PROJECT_MEMBER_REMOVED, event);

        log.info("멤버 제거: projectKey={}, userId={}", key, targetUserId);
    }

    public List<MemberResponse> getMembers(String key) {
        Project project = findActiveByKey(key);
        return memberRepository.findByProjectId(project.getId()).stream()
                .map(MemberResponse::from)
                .toList();
    }

    // ── 내부 ──

    Project findActiveByKey(String key) {
        return projectRepository.findByProjectKeyAndActiveTrue(key.toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private void validateMemberAccess(Long projectId, Long userId, ProjectRole minRole) {
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (member.getRole().ordinal() > minRole.ordinal()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
