package com.pch.boardreport.repository;

import com.pch.boardreport.domain.SprintBurndown;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SprintBurndownRepository extends JpaRepository<SprintBurndown, Long> {

    List<SprintBurndown> findBySprintIdOrderByRecordDateAsc(Long sprintId);

    Optional<SprintBurndown> findBySprintIdAndRecordDate(Long sprintId, LocalDate recordDate);
}
