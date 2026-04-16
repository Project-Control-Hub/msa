package com.pch.integration.repository;

import com.pch.integration.domain.VcsConnection;
import com.pch.integration.domain.VcsProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VcsConnectionRepository extends JpaRepository<VcsConnection, Long> {
    Optional<VcsConnection> findByUserIdAndProvider(Long userId, VcsProvider provider);
    void deleteByUserIdAndProvider(Long userId, VcsProvider provider);
}
