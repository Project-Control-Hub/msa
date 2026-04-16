package com.pch.project.repository;

import com.pch.project.domain.Version;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionRepository extends JpaRepository<Version, Long> {
    List<Version> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
