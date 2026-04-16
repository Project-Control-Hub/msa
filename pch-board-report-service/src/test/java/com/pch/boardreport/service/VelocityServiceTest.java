package com.pch.boardreport.service;

import com.pch.boardreport.domain.SprintVelocity;
import com.pch.boardreport.repository.SprintVelocityRepository;
import com.pch.common.enums.SprintIncompleteIssueDisposition;
import com.pch.common.event.SprintCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityServiceTest {

    @Mock private SprintVelocityRepository velocityRepository;
    @InjectMocks private VelocityService velocityService;

    @Test
    void getVelocity_limitsToSprintCount() {
        List<SprintVelocity> velocities = List.of(
            SprintVelocity.builder().sprintId(1L).projectId(1L).endDate(LocalDate.of(2025, 3, 1)).build(),
            SprintVelocity.builder().sprintId(2L).projectId(1L).endDate(LocalDate.of(2025, 2, 1)).build(),
            SprintVelocity.builder().sprintId(3L).projectId(1L).endDate(LocalDate.of(2025, 1, 1)).build()
        );
        when(velocityRepository.findTop10ByProjectIdOrderByEndDateDesc(1L)).thenReturn(velocities);

        List<SprintVelocity> result = velocityService.getVelocity(1L, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void recordVelocity_savesOnSprintCompleted() {
        SprintCompletedEvent event = new SprintCompletedEvent(
            1L, 1L, SprintIncompleteIssueDisposition.MOVE_TO_BACKLOG, "project-service");
        when(velocityRepository.findBySprintId(1L)).thenReturn(Optional.empty());
        when(velocityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        velocityService.recordVelocity(event);

        verify(velocityRepository).save(any(SprintVelocity.class));
    }
}
