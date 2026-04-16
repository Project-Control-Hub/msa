package com.pch.project.service;

import com.pch.common.enums.SprintIncompleteIssueDisposition;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.project.domain.Sprint;
import com.pch.project.domain.SprintStatus;
import com.pch.project.dto.SprintResponse;
import com.pch.project.repository.SprintRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock ProjectService projectService;
    @Mock DomainEventPublisher eventPublisher;
    @InjectMocks SprintService sprintService;

    @Test
    @DisplayName("스프린트 시작 → ACTIVE 상태 변경")
    void start_success() {
        Sprint sprint = Sprint.create(1L, "Sprint 1", "목표", null, null);
        given(sprintRepository.findById(1L)).willReturn(Optional.of(sprint));

        SprintResponse response = sprintService.start(1L);

        assertThat(response.status()).isEqualTo(SprintStatus.ACTIVE);
    }

    @Test
    @DisplayName("스프린트 완료 → SprintCompletedEvent 발행")
    void complete_success() {
        Sprint sprint = Sprint.create(1L, "Sprint 1", "목표", null, null);
        sprint.start();
        given(sprintRepository.findById(1L)).willReturn(Optional.of(sprint));

        SprintResponse response = sprintService.complete(1L, SprintIncompleteIssueDisposition.MOVE_TO_BACKLOG);

        assertThat(response.status()).isEqualTo(SprintStatus.COMPLETED);
        then(eventPublisher).should().publish(anyString(), any());
    }

    @Test
    @DisplayName("CREATED 상태에서 complete → IllegalStateException")
    void complete_invalidState() {
        Sprint sprint = Sprint.create(1L, "Sprint 1", "목표", null, null);
        given(sprintRepository.findById(1L)).willReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.complete(1L, SprintIncompleteIssueDisposition.MOVE_TO_BACKLOG))
                .isInstanceOf(IllegalStateException.class);
    }
}
