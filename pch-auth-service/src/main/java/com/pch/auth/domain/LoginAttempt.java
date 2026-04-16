package com.pch.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_login_attempts_email_at", columnList = "email, attemptedAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean isSuccess;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime attemptedAt = LocalDateTime.now();

    public static LoginAttempt success(String email, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.email = email;
        attempt.ipAddress = ipAddress;
        attempt.isSuccess = true;
        attempt.attemptedAt = LocalDateTime.now();
        return attempt;
    }

    public static LoginAttempt failure(String email, String ipAddress, String reason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.email = email;
        attempt.ipAddress = ipAddress;
        attempt.isSuccess = false;
        attempt.reason = reason;
        attempt.attemptedAt = LocalDateTime.now();
        return attempt;
    }
}
