package com.pch.boardreport.service;

import com.pch.boardreport.domain.SprintVelocity;
import com.pch.boardreport.repository.SprintVelocityRepository;
import com.pch.common.event.SprintCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityService {

    private final SprintVelocityRepository velocityRepository;

    @Cacheable(value = "velocity", key = "#projectId")
    @Transactional(readOnly = true)
    public List<SprintVelocity> getVelocity(Long projectId, int sprintCount) {
        List<SprintVelocity> all = velocityRepository.findTop10ByProjectIdOrderByEndDateDesc(projectId);
        return all.stream().limit(sprintCount).toList();
    }

    @CacheEvict(value = "velocity", key = "#event.projectId")
    @Transactional
    public void recordVelocity(SprintCompletedEvent event) {
        SprintVelocity velocity = velocityRepository.findBySprintId(event.getSprintId())
                .orElse(SprintVelocity.builder()
                        .sprintId(event.getSprintId())
                        .projectId(event.getProjectId())
                        .build());

        // In production, query Issue Service for committed/completed points
        velocity.setCommittedPoints(0);
        velocity.setCompletedPoints(0);
        velocityRepository.save(velocity);
        log.info("SprintVelocity recorded for sprint: {}", event.getSprintId());
    }
}
