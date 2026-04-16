package com.pch.boardreport.service;

import com.pch.boardreport.domain.BoardCard;
import com.pch.boardreport.dto.CfdDataPoint;
import com.pch.boardreport.repository.BoardCardRepository;
import com.pch.common.enums.IssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cumulative Flow Diagram — 일별 상태별 이슈 수 집계.
 * 현재는 간이 구현(현재 상태 스냅샷 기반). 이력 기반 CFD는 Phase 4에서 고도화.
 */
@Service
@RequiredArgsConstructor
public class CfdService {

    private final BoardCardRepository boardCardRepository;

    @Cacheable(value = "cfd", key = "#projectId + ':' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public List<CfdDataPoint> getCfd(Long projectId, LocalDate from, LocalDate to) {
        // Simplified: returns current snapshot per status
        List<CfdDataPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (IssueStatus status : IssueStatus.values()) {
            List<BoardCard> cards = boardCardRepository.findByProjectIdAndStatus(projectId, status);
            points.add(new CfdDataPoint(today, status, cards.size()));
        }
        return points;
    }
}
