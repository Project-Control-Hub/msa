package com.pch.boardreport.service;

import com.pch.boardreport.domain.SprintBurndown;
import com.pch.boardreport.repository.BoardCardRepository;
import com.pch.boardreport.repository.SprintBurndownRepository;
import com.pch.common.enums.IssueStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BurndownServiceTest {

    @Mock private SprintBurndownRepository burndownRepository;
    @Mock private BoardCardRepository boardCardRepository;
    @InjectMocks private BurndownService burndownService;

    @Test
    void getBurndown_returnsOrderedByDate() {
        SprintBurndown day1 = SprintBurndown.builder().sprintId(1L).recordDate(LocalDate.of(2025, 1, 1)).build();
        SprintBurndown day2 = SprintBurndown.builder().sprintId(1L).recordDate(LocalDate.of(2025, 1, 2)).build();
        when(burndownRepository.findBySprintIdOrderByRecordDateAsc(1L)).thenReturn(List.of(day1, day2));

        List<SprintBurndown> result = burndownService.getBurndown(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRecordDate()).isBefore(result.get(1).getRecordDate());
    }

    @Test
    void recordDailySnapshot_createsNewSnapshotIfNotExists() {
        when(boardCardRepository.countBySprintId(1L)).thenReturn(10L);
        when(boardCardRepository.countBySprintIdAndStatus(1L, IssueStatus.DONE)).thenReturn(3L);
        when(burndownRepository.findBySprintIdAndRecordDate(eq(1L), any())).thenReturn(Optional.empty());
        when(burndownRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SprintBurndown snapshot = burndownService.recordDailySnapshot(1L);

        assertThat(snapshot.getIssueCount()).isEqualTo(10);
        assertThat(snapshot.getCompletedCount()).isEqualTo(3);
        verify(burndownRepository).save(any(SprintBurndown.class));
    }
}
