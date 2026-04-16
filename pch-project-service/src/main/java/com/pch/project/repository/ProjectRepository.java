package com.pch.project.repository;

import com.pch.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectKeyAndActiveTrue(String projectKey);
    boolean existsByProjectKey(String projectKey);
}
