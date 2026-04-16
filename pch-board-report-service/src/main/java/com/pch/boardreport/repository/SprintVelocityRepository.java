package com.pch.boardreport.repository;

import com.pch.boardreport.domain.SprintVelocity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintVelocityRepository extends JpaRepository<SprintVelocity, Long> {

    List<SprintVelocity> findTop10ByProjectIdOrderByEndDateDesc(Long projectId);

    Optional<SprintVelocity> findBySprintId(Long sprintId);
}
