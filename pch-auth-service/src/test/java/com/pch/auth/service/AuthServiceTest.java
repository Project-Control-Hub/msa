package com.pch.auth.service;

import com.pch.auth.domain.User;
import com.pch.auth.dto.LoginRequest;
import com.pch.auth.dto.SignupRequest;
import com.pch.auth.dto.TokenResponse;
import com.pch.auth.repository.LoginAttemptRepository;
import com.pch.auth.repository.UserRepository;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.common.kafka.DomainEventPublisher;
import com.pch.common.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private DomainEventPublisher eventPublisher;

    @Test
    @DisplayName("회원가입 성공 시 토큰을 반환한다")
    void register_success() {
        // given
        SignupRequest request = new SignupRequest("test@test.com", "Password1!", "홍길동");
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(passwordEncoder.encode("Password1!")).willReturn("$2a$10$encoded");
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            // reflection set id
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, 1L);
            return u;
        });
        given(jwtTokenProvider.createAccessToken(1L, "test@test.com")).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(1L, "test@test.com")).willReturn("refresh-token");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        TokenResponse response = authService.register(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.email()).isEqualTo("test@test.com");
        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("중복 이메일로 회원가입 시 DUPLICATE_RESOURCE 예외")
    void register_duplicateEmail() {
        // given
        SignupRequest request = new SignupRequest("dup@test.com", "Password1!", "홍길동");
        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 UNAUTHORIZED 예외")
    void login_invalidPassword() {
        // given
        LoginRequest request = new LoginRequest("test@test.com", "wrongpass");
        User user = User.create("test@test.com", "$2a$10$encoded", "홍길동");
        given(loginAttemptRepository.countByEmailAndIsSuccessFalseAndAttemptedAtAfter(anyString(), any())).willReturn(0L);
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpass", "$2a$10$encoded")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("로그인 성공 시 토큰을 반환한다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("test@test.com", "Password1!");
        User user = User.create("test@test.com", "$2a$10$encoded", "홍길동");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, 1L);
        } catch (Exception ignored) {}

        given(loginAttemptRepository.countByEmailAndIsSuccessFalseAndAttemptedAtAfter(anyString(), any())).willReturn(0L);
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password1!", "$2a$10$encoded")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, "test@test.com")).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(1L, "test@test.com")).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        TokenResponse response = authService.login(request, "127.0.0.1");

        // then
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.userId()).isEqualTo(1L);
    }
}
