package com.pch.project.service;

import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.project.domain.Project;
import com.pch.project.domain.Version;
import com.pch.project.dto.CreateVersionRequest;
import com.pch.project.dto.VersionResponse;
import com.pch.project.repository.VersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionService {

    private final VersionRepository versionRepository;
    private final ProjectService projectService;

    @Transactional
    public VersionResponse create(String projectKey, CreateVersionRequest request) {
        Project project = projectService.findActiveByKey(projectKey);
        Version version = Version.create(project.getId(), request.name(), request.description());
        versionRepository.save(version);
        return VersionResponse.from(version);
    }

    public List<VersionResponse> getByProject(String projectKey) {
        Project project = projectService.findActiveByKey(projectKey);
        return versionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .map(VersionResponse::from)
                .toList();
    }

    @Transactional
    public VersionResponse release(Long id) {
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        version.release();
        return VersionResponse.from(version);
    }
}
