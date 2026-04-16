package com.pch.boardreport.repository;

import com.pch.boardreport.domain.DashboardGadget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardGadgetRepository extends JpaRepository<DashboardGadget, Long> {

    List<DashboardGadget> findByProjectIdAndUserIdOrderByPositionAsc(Long projectId, Long userId);
}
