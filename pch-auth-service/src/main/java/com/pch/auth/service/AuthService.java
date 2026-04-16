package com.pch.auth.service;

import com.pch.auth.domain.LoginAttempt;
import com.pch.auth.domain.User;
import com.pch.auth.dto.LoginRequest;
import com.pch.auth.dto.SignupRequest;
import com.pch.auth.dto.TokenResponse;
import com.pch.auth.repository.LoginAttemptRepository;
import com.pch.auth.repository.UserRepository;
import com.pch.common.event.UserCreatedEvent;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.kafka.KafkaTopics;
import com.pch.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 5;
    private static final String REFRESH_KEY_PREFIX = "refresh_token:";

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public TokenResponse register(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 사용 중인 이메일입니다.");
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name()
        );
        userRepository.save(user);

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        // 도메인 이벤트 발행
        eventPublisher.publish(
                KafkaTopics.USER_CREATED,
                new UserCreatedEvent(user.getId(), user.getEmail(), user.getName(), "pch-auth-service")
        );

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ipAddress) {
        // 로그인 시도 제한 검사
        checkLoginAttemptLimit(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    recordLoginAttempt(request.email(), ipAddress, false, "User not found");
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            recordLoginAttempt(request.email(), ipAddress, false, "Invalid password");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다.");
        }

        recordLoginAttempt(request.email(), ipAddress, true, null);
        user.recordLogin();

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        try {
            if (!isRefreshTokenValid(refreshToken)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
            }

            Long userId = jwtTokenProvider.extractUserId(refreshToken);
            String email = jwtTokenProvider.extractEmail(refreshToken);

            // 기존 리프레시 토큰 무효화 (회전 전략)
            invalidateRefreshToken(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

            log.info("Token refreshed for user: id={}", userId);
            return issueTokens(user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "토큰 갱신에 실패했습니다.");
        }
    }

    public void logout(Long userId) {
        invalidateRefreshToken(userId);
        log.info("User logged out: id={}", userId);
    }

    // ── Private helpers ──

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());

        // Redis에 리프레시 토큰 저장 (7일 TTL)
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                Duration.ofDays(7)
        );

        return new TokenResponse(accessToken, refreshToken, user.getId(), user.getEmail(), user.getName());
    }

    private boolean isRefreshTokenValid(String refreshToken) {
        try {
            Long userId = jwtTokenProvider.extractUserId(refreshToken);
            String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
            return refreshToken.equals(stored);
        } catch (Exception e) {
            return false;
        }
    }

    private void invalidateRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }

    private void checkLoginAttemptLimit(String email) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(LOCKOUT_MINUTES);
        long failCount = loginAttemptRepository.countByEmailAndIsSuccessFalseAndAttemptedAtAfter(email, since);
        if (failCount >= MAX_LOGIN_ATTEMPTS) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    String.format("%d분 내 %d회 로그인 실패로 계정이 일시 잠금되었습니다.", LOCKOUT_MINUTES, MAX_LOGIN_ATTEMPTS));
        }
    }

    private void recordLoginAttempt(String email, String ipAddress, boolean success, String reason) {
        LoginAttempt attempt = success
                ? LoginAttempt.success(email, ipAddress)
                : LoginAttempt.failure(email, ipAddress, reason);
        loginAttemptRepository.save(attempt);
    }
}
