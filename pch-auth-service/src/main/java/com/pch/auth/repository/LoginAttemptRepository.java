package com.pch.auth.repository;

import com.pch.auth.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByEmailAndIsSuccessFalseAndAttemptedAtAfter(String email, LocalDateTime after);
}
