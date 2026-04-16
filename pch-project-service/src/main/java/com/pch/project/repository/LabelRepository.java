package com.pch.project.repository;

import com.pch.project.domain.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabelRepository extends JpaRepository<Label, Long> {
    List<Label> findByProjectId(Long projectId);
    boolean existsByProjectIdAndName(Long projectId, String name);
}
