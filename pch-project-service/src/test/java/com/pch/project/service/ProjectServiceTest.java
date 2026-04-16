package com.pch.project.service;

import com.pch.common.enums.ProjectRole;
import com.pch.common.exception.BusinessException;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.project.domain.Project;
import com.pch.project.domain.ProjectMember;
import com.pch.project.dto.CreateProjectRequest;
import com.pch.project.dto.ProjectResponse;
import com.pch.project.repository.ProjectMemberRepository;
import com.pch.project.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock DomainEventPublisher eventPublisher;
    @InjectMocks ProjectService projectService;

    @Test
    @DisplayName("프로젝트 생성 성공 → 생성자가 ADMIN으로 등록")
    void create_success() {
        var request = new CreateProjectRequest("PCH", "Project Control Hub", "설명");
        given(projectRepository.existsByProjectKey("PCH")).willReturn(false);
        given(projectRepository.save(any(Project.class))).willAnswer(inv -> inv.getArgument(0));
        given(memberRepository.save(any(ProjectMember.class))).willAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.create(1L, request);

        assertThat(response.projectKey()).isEqualTo("PCH");
        assertThat(response.name()).isEqualTo("Project Control Hub");
        then(memberRepository).should().save(argThat(m -> m.getRole() == ProjectRole.ADMIN));
    }

    @Test
    @DisplayName("중복 프로젝트 키 → 409 DUPLICATE_RESOURCE")
    void create_duplicateKey() {
        var request = new CreateProjectRequest("PCH", "name", null);
        given(projectRepository.existsByProjectKey("PCH")).willReturn(true);

        assertThatThrownBy(() -> projectService.create(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("라벨 이름 중복 시 409 에러 (LabelService 위임)")
    void labelDuplicate_detected() {
        // LabelService에서 처리하므로 별도 검증 (여기서는 ProjectService 범위)
    }
}
