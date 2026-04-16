package com.pch.search.repository;

import com.pch.search.domain.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {

    List<SavedFilter> findByUserIdOrderByCreatedAtDesc(Long userId);
}
