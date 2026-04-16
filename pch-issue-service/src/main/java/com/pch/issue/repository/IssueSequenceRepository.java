package com.pch.issue.repository;

import com.pch.issue.domain.IssueSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface IssueSequenceRepository extends JpaRepository<IssueSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssueSequence> findByProjectKey(String projectKey);
}
