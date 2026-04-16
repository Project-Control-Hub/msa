package com.pch.boardreport.service;

import com.pch.boardreport.domain.SprintBurndown;
import com.pch.boardreport.repository.BoardCardRepository;
import com.pch.boardreport.repository.SprintBurndownRepository;
import com.pch.common.enums.IssueStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BurndownService {

    private final SprintBurndownRepository burndownRepository;
    private final BoardCardRepository boardCardRepository;

    @Cacheable(value = "burndown", key = "#sprintId")
    @Transactional(readOnly = true)
    public List<SprintBurndown> getBurndown(Long sprintId) {
        return burndownRepository.findBySprintIdOrderByRecordDateAsc(sprintId);
    }

    @CacheEvict(value = "burndown", key = "#sprintId")
    @Transactional
    public SprintBurndown recordDailySnapshot(Long sprintId) {
        LocalDate today = LocalDate.now();
        long totalCount = boardCardRepository.countBySprintId(sprintId);
        long doneCount = boardCardRepository.countBySprintIdAndStatus(sprintId, IssueStatus.DONE);

        SprintBurndown snapshot = burndownRepository.findBySprintIdAndRecordDate(sprintId, today)
                .orElse(SprintBurndown.builder()
                        .sprintId(sprintId)
                        .recordDate(today)
                        .build());

        snapshot.setIssueCount((int) totalCount);
        snapshot.setCompletedCount((int) doneCount);
        snapshot.setTotalPoints(0); // story points aggregation — requires Issue Service API
        snapshot.setCompletedPoints(0);
        snapshot.setRemainingPoints(0);

        return burndownRepository.save(snapshot);
    }

    @CacheEvict(value = "burndown", key = "#sprintId")
    @Transactional
    public void recalculate(Long sprintId) {
        log.info("Recalculating burndown for sprint: {}", sprintId);
        recordDailySnapshot(sprintId);
    }

    /**
     * 일별 번다운 스냅샷 배치 — 매일 자정 실행.
     * 활성 스프린트에 대해 스냅샷 기록.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void dailySnapshotBatch() {
        log.info("Running daily burndown snapshot batch");
        // In production: query active sprints from Project Service
        // For now, snapshot logic is triggered per-sprint via event or API
    }
}
