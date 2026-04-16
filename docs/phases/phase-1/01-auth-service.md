# Auth Service 분리 작업 명세

## 서비스 개요

Auth Service는 사용자 인증, JWT 발급/검증, 계정 관리를 담당합니다. 다른 마이크로서비스의 기반이 되므로 가장 먼저 분리합니다.

## 기본 정보

| 항목 | 값 |
|------|-----|
| **서비스명** | pch-auth |
| **포트** | 8081 |
| **DB** | pch_auth (MySQL 8.0) |
| **난이도** | ★☆☆☆☆ |
| **소요시간** | 3-4일 |
| **의존도** | 높음 (모든 서비스가 의존) |

## 서비스 책임 영역

### 보유 엔티티

| 엔티티 | 테이블명 | 설명 |
|--------|----------|------|
| User Account | user_account_tb | 사용자 계정, 인증 정보 |
| Refresh Token | refresh_token_tb | JWT 갱신 토큰 (Redis) |
| Login Attempt | login_attempt_tb | 로그인 시도 기록 |

### 현행 모놀리스 패키지 매핑

| 모놀리스 패키지 | Auth Service 패키지 |
|-----------------|-------------------|
| com.pch.domain.auth | com.pch.auth.domain |
| com.pch.api.v1.auth | com.pch.auth.api |
| com.pch.global.config.SecurityConfig | com.pch.auth.config |
| com.pch.global.security.* | com.pch.auth.security |
| com.pch.global.exception.AuthException | com.pch.auth.exception |

## API 엔드포인트

### 공개 (Public) API

| Method | Path | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| POST | `/api/v1/auth/signup` | 회원가입 | email, password, name | userId, email, name |
| POST | `/api/v1/auth/login` | 로그인 | email, password | accessToken, refreshToken |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 | refreshToken | accessToken |
| POST | `/api/v1/auth/logout` | 로그아웃 | - | success |
| GET | `/api/v1/auth/me` | 현재 사용자 정보 | (JWT 필수) | UserSummaryDto |

### 관리자 API

| Method | Path | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| PUT | `/api/v1/auth/users/{id}` | 사용자 정보 수정 | name, email | success |
| DELETE | `/api/v1/auth/users/{id}` | 사용자 삭제 | - | success |
| GET | `/api/v1/auth/users` | 사용자 목록 조회 | page, size | Page<UserSummaryDto> |

### 내부 API (Internal)

다른 마이크로서비스에서만 호출 가능 (X-Internal-Key 헤더 필수)

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| GET | `/internal/v1/users/{id}/summary` | 사용자 요약 정보 | UserSummaryDto |
| POST | `/internal/v1/users/batch` | 사용자 목록 조회 (Batch) | List<UserSummaryDto> |
| GET | `/internal/v1/users/{id}/role` | 사용자 권한 | UserRole |

## 발행 이벤트

Auth Service가 발행하는 도메인 이벤트:

```java
// 1. 사용자 생성
UserCreatedEvent {
    userId: Long
    email: String
    name: String
    authProvider: AuthProvider
}

// 2. 사용자 정보 변경
UserUpdatedEvent {
    userId: Long
    email: String
    name: String
    profileImageUrl: String
}

// 3. 사용자 삭제
UserDeletedEvent {
    userId: Long
    email: String
}
```

**토픽**: `user.created`, `user.updated`

## 데이터베이스 스키마

### user_account_tb

```sql
CREATE TABLE user_account_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    profile_image_url VARCHAR(500),
    role ENUM('ADMIN', 'USER') DEFAULT 'USER',
    auth_provider ENUM('LOCAL', 'GITHUB', 'GITLAB', 'GOOGLE') DEFAULT 'LOCAL',
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_email ON user_account_tb(email);
CREATE INDEX idx_auth_provider ON user_account_tb(auth_provider);
```

### login_attempt_tb

```sql
CREATE TABLE login_attempt_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    is_success BOOLEAN NOT NULL,
    reason VARCHAR(255),
    attempted_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_attempted_at ON login_attempt_tb(email, attempted_at);
```

### Redis (Refresh Token)

```
Key: refresh_token:{tokenId}
Value: {
    userId: Long,
    email: String,
    issuedAt: LocalDateTime,
    expiresAt: LocalDateTime
}
TTL: 7일
```

## 마이그레이션 전략

### 1단계: 코드 분리

```
pch-auth/
├── src/main/java/com/pch/auth/
│   ├── api/
│   │   ├── AuthController.java
│   │   └── InternalAuthController.java
│   ├── domain/
│   │   ├── User.java
│   │   ├── UserRepository.java
│   │   └── UserService.java
│   ├── security/
│   │   ├── JwtProvider.java
│   │   └── SecurityConfig.java
│   ├── exception/
│   │   ├── AuthException.java
│   │   └── ErrorCode.java
│   ├── event/
│   │   ├── UserCreatedEvent.java
│   │   ├── UserUpdatedEvent.java
│   │   ├── UserDeletedEvent.java
│   │   └── KafkaEventPublisher.java
│   └── AuthServiceApplication.java
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── application-prod.yml
```

### 2단계: 데이터 마이그레이션

**모놀리스에서 pch_auth로 데이터 복제**:

```sql
-- 1. pch_auth DB에 user_account_tb 생성
-- (위의 스키마 참조)

-- 2. 모놀리스에서 데이터 마이그레이션
INSERT INTO pch_auth.user_account_tb (
    id, email, password, name, profile_image_url, role, auth_provider, 
    is_active, last_login_at, created_at, updated_at
)
SELECT 
    id, email, password, name, profile_image_url, role, auth_provider,
    is_active, last_login_at, created_at, updated_at
FROM pch_main.user_account_tb;

-- 3. 마이그레이션 검증
SELECT COUNT(*) FROM pch_auth.user_account_tb;
SELECT COUNT(*) FROM pch_main.user_account_tb;
-- 두 개가 같아야 함
```

## 작업 체크리스트

### 1단계: 설계 및 준비 (0.5일)
- [ ] API 엔드포인트 정의 확정
- [ ] 데이터베이스 스키마 설계 확정
- [ ] JWT 토큰 포맷 정의
  - Header: {alg, typ}
  - Payload: {sub, email, role, iat, exp}
  - Signature: HS256
- [ ] 보안 요구사항 검토
  - 비밀번호 암호화 (BCrypt)
  - CORS 정책
  - Rate Limiting (로그인 시도)

### 2단계: 코드 구현 (1.5일)
- [ ] Entity, Repository, Service 구현
  ```java
  // User Entity
  @Entity
  public class User {
      @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;
      @Column(unique = true)
      private String email;
      private String password;  // BCrypt encrypted
      private String name;
      // ...
  }
  
  // UserService
  @Service
  public class UserService {
      public UserResponse signup(SignupRequest req) { }
      public LoginResponse login(LoginRequest req) { }
      public String refreshToken(String refreshToken) { }
      public UserSummaryDto getUserSummary(Long userId) { }
  }
  ```

- [ ] JWT 관련 클래스 구현
  ```java
  @Component
  public class JwtProvider {
      public String generateToken(User user) { }
      public Claims validateToken(String token) { }
      public boolean isTokenExpired(String token) { }
  }
  ```

- [ ] Controller 구현
  ```java
  @RestController
  @RequestMapping("/api/v1/auth")
  public class AuthController {
      @PostMapping("/signup")
      public ApiResponse<UserResponse> signup(@RequestBody SignupRequest req) { }
      
      @PostMapping("/login")
      public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) { }
      
      @PostMapping("/refresh")
      public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest req) { }
      
      @GetMapping("/me")
      public ApiResponse<UserSummaryDto> getCurrentUser() { }
  }
  ```

- [ ] 내부 API Controller 구현
  ```java
  @RestController
  @RequestMapping("/internal/v1/auth")
  public class InternalAuthController {
      @GetMapping("/users/{id}/summary")
      public UserSummaryDto getUserSummary(@PathVariable Long id) { }
      
      @PostMapping("/users/batch")
      public List<UserSummaryDto> getUsersBatch(@RequestBody List<Long> ids) { }
  }
  ```

- [ ] 이벤트 발행자 구현
  ```java
  @Service
  public class AuthEventPublisher {
      public void publishUserCreatedEvent(User user) {
          UserCreatedEvent event = new UserCreatedEvent();
          eventPublisher.publish(event, "user.created");
      }
  }
  ```

### 3단계: 테스트 (1일)
- [ ] 단위 테스트 (JUnit, Mockito)
  ```java
  @SpringBootTest
  public class UserServiceTest {
      @Test
      public void testSignup_Success() { }
      
      @Test
      public void testSignup_DuplicateEmail() { }
      
      @Test
      public void testLogin_Success() { }
      
      @Test
      public void testLogin_InvalidPassword() { }
      
      @Test
      public void testRefreshToken_Valid() { }
      
      @Test
      public void testRefreshToken_Expired() { }
  }
  ```

- [ ] 통합 테스트 (MockMvc)
  ```java
  @WebMvcTest(AuthController.class)
  public class AuthControllerTest {
      @Test
      public void testSignupEndpoint() { }
      
      @Test
      public void testLoginEndpoint() { }
      
      @Test
      public void testRefreshEndpoint() { }
  }
  ```

- [ ] Security 테스트
  - 공개 경로 접근 가능
  - 보호된 경로는 JWT 필요
  - 만료된 토큰 거부

### 4단계: 데이터 마이그레이션 (0.5일)
- [ ] pch_auth 데이터베이스 생성
- [ ] user_account_tb, login_attempt_tb 생성
- [ ] 모놀리스에서 데이터 마이그레이션
  ```sql
  -- init-db.sql에 추가
  USE pch_auth;
  -- user_account_tb, login_attempt_tb 생성
  ```
- [ ] 데이터 검증 (행 개수, 무결성)

### 5단계: Gateway 라우팅 추가 (0.5일)
- [ ] `application.yml`에 Auth 서비스 라우팅 규칙 추가
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: auth-service
            uri: lb://pch-auth
            predicates:
              - Path=/api/v1/auth/**,/internal/v1/auth/**
            filters:
              - StripPrefix=2
  ```

- [ ] Gateway에서 JWT 검증 필터 업데이트
  - 공개 경로: /api/v1/auth/login, /api/v1/auth/signup, /api/v1/auth/refresh
  - 보호된 경로: 나머지 모두

### 6단계: E2E 테스트 (0.5일)
- [ ] 회원가입 → 로그인 → 토큰 갱신 시나리오
  ```bash
  # 1. 회원가입
  curl -X POST http://localhost:8000/api/v1/auth/signup \
    -H "Content-Type: application/json" \
    -d '{"email":"user@example.com","password":"pass123","name":"User"}'
  
  # 2. 로그인
  curl -X POST http://localhost:8000/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"user@example.com","password":"pass123"}'
  
  # 3. 현재 사용자 정보 조회
  curl http://localhost:8000/api/v1/auth/me \
    -H "Authorization: Bearer <access-token>"
  ```

- [ ] 에러 시나리오 테스트
  - 잘못된 비밀번호
  - 존재하지 않는 사용자
  - 만료된 토큰
  - Rate Limiting (로그인 시도)

- [ ] 내부 API 테스트
  ```bash
  curl http://localhost:8000/internal/v1/users/1/summary \
    -H "X-Internal-Key: <internal-key>"
  ```

### 7단계: 배포 및 모니터링 (0.5일)
- [ ] Docker 이미지 빌드 및 테스트
- [ ] GitHub Actions CI/CD 확인
- [ ] Kubernetes 배포 (선택)
- [ ] Eureka에서 서비스 등록 확인
- [ ] 로깅 및 모니터링 설정

## 주의사항

### 보안

1. **비밀번호 암호화**
   ```java
   @Bean
   public PasswordEncoder passwordEncoder() {
       return new BCryptPasswordEncoder(12);  // strength 12
   }
   ```

2. **JWT 보안**
   - `SECRET_KEY` 환경변수로 관리 (최소 256비트)
   - `accessToken` 만료시간: 1시간
   - `refreshToken` 만료시간: 7일
   - HTTP-only 쿠키에 저장 (XSS 방지)

3. **로그인 시도 제한**
   ```java
   // 5분 내 5회 실패 시 계정 잠금
   if (loginAttemptsInLast5Minutes >= 5) {
       throw new AccountLockedException();
   }
   ```

4. **CORS 설정**
   ```yaml
   # 모놀리스에서 프론트엔드 도메인 제한
   cors:
     allowed-origins: https://app.example.com
   ```

### 성능

1. **인덱스 설정**
   - `user_account_tb.email` (UNIQUE)
   - `login_attempt_tb.email, attempted_at`

2. **캐싱**
   ```java
   @Cacheable(value = "user", key = "#id", cacheManager = "cacheManager")
   public User getUserById(Long id) { }
   ```

3. **배치 조회**
   ```java
   // 다른 서비스에서 여러 사용자 정보 필요시
   public List<UserSummaryDto> getUsersBatch(List<Long> ids) {
       return userRepository.findAllById(ids)
           .stream()
           .map(UserSummaryDto::from)
           .toList();
   }
   ```

## 분리 후 모놀리스 정리

Auth Service 분리 완료 후 모놀리스에서 다음을 제거합니다:

- [ ] `com.pch.domain.auth` 패키지 삭제
- [ ] `com.pch.api.v1.auth` 패키지 삭제
- [ ] `SecurityConfig` 일부 코드 제거 (Auth 관련)
- [ ] `application.yml`에서 인증 설정 제거
- [ ] 데이터베이스 마이그레이션 (user_account_tb 삭제)

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [../phase-0/02-common-library.md](../phase-0/02-common-library.md)
- [../phase-0/03-gateway-setup.md](../phase-0/03-gateway-setup.md)
