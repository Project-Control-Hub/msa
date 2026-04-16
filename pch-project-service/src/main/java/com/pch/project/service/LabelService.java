package com.pch.project.service;

import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.project.domain.Label;
import com.pch.project.domain.Project;
import com.pch.project.dto.CreateLabelRequest;
import com.pch.project.dto.LabelResponse;
import com.pch.project.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabelService {

    private final LabelRepository labelRepository;
    private final ProjectService projectService;

    @Transactional
    public LabelResponse create(String projectKey, CreateLabelRequest request) {
        Project project = projectService.findActiveByKey(projectKey);
        if (labelRepository.existsByProjectIdAndName(project.getId(), request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Label label = Label.create(project.getId(), request.name(), request.color());
        labelRepository.save(label);
        return LabelResponse.from(label);
    }

    public List<LabelResponse> getByProject(String projectKey) {
        Project project = projectService.findActiveByKey(projectKey);
        return labelRepository.findByProjectId(project.getId()).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        labelRepository.deleteById(id);
    }
}
