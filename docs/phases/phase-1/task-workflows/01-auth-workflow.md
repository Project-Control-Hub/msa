# T1 — Auth Service 분리 워크플로우

> 목표: 모놀리스의 `com.pch.mng.user.*`, `com.pch.mng.auth.*` 를 **`pch-auth-service`** 로 분리하고 JWT 기반 인증을 완성한다.
>
> **브랜치**: `feature/phase-1-auth` · **베이스**: `develop` · **예상 기간**: 3~4일

---

## 🧩 Prerequisites (착수 전 체크)

- [ ] Phase 0 PR 이 `develop` 에 머지된 상태
- [ ] 로컬 `docker compose up -d mysql redis kafka` 정상 기동
- [ ] `pch-common` 의 `JwtTokenProvider`, `SecurityContextUtil` 확인
- [ ] MySQL `pch_auth` DB 가 생성되어 있음 (`docker/init-db.sql`)
- [ ] Postman / HTTPie 테스트 컬렉션 준비

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop
git pull
git checkout -b feature/phase-1-auth
```

### Step 1. 도메인/엔티티 이관 (0.5일)

- [ ] `pch-auth-service/src/main/java/com/pch/auth/domain/User.java`
- [ ] `pch-auth-service/src/main/java/com/pch/auth/domain/RefreshToken.java` (Redis TTL 또는 RDB)
- [ ] `BaseEntity` 상속, `@Table(name = "users")`, unique email
- [ ] `db/migration/V1__create_users.sql`, `V2__create_refresh_tokens.sql`
- **커밋**: `feat(auth): User/RefreshToken 엔티티 정의 + Flyway 초기 마이그레이션`

### Step 2. Repository + Service (1일)

- [ ] `UserRepository extends JpaRepository<User, Long>`
  - `Optional<User> findByEmail(String email)`
- [ ] `PasswordEncoder` 빈 등록 (`BCryptPasswordEncoder(10)`)
- [ ] `AuthService`
  - `register(SignupRequest)` : 이메일 중복 검증 → 저장 → `UserCreatedEvent` 발행
  - `login(LoginRequest)` : 자격검증 → access/refresh 발급
  - `refresh(String refreshToken)` : RefreshToken 유효성 → 신규 발급 (회전)
  - `logout(Long userId)` : Redis 에서 refresh 무효화
- **커밋**: `feat(auth): AuthService 회원가입/로그인/토큰 재발급/로그아웃 구현`

### Step 3. REST API (0.5일)

| 메서드 | 경로                    | 기능           | 인증 |
|--------|-------------------------|----------------|------|
| POST   | `/api/auth/register`    | 회원가입        | X    |
| POST   | `/api/auth/login`       | 로그인          | X    |
| POST   | `/api/auth/refresh`     | 토큰 재발급      | X (refresh token header)    |
| POST   | `/api/auth/logout`      | 로그아웃        | O    |
| GET    | `/api/v1/users/me`      | 내 프로필 조회   | O    |
| PATCH  | `/api/v1/users/me`      | 프로필 수정      | O    |

- [ ] `AuthController`, `UserController`
- [ ] `SignupRequest`, `LoginRequest`, `TokenResponse`, `UserProfileResponse`
- [ ] `jakarta.validation` 어노테이션 적용
- **커밋**: `feat(auth): Auth/User REST API + DTO 정의`

### Step 4. 이벤트 발행 (0.5일)

- [ ] `UserCreatedEvent` (`pch-common`) 를 Kafka `user.created` 토픽으로 발행
- [ ] `KafkaUserEventPublisher implements DomainEventPublisher`
- [ ] 테스트: `EmbeddedKafka` 로 발행 검증
- **커밋**: `feat(auth): UserCreatedEvent Kafka 발행 (토픽: user.created)`

### Step 5. 테스트 (1일)

- [ ] **단위 테스트**: `AuthServiceTest` (Mockito) - 정상/중복 이메일/비번 오류
- [ ] **슬라이스**: `@WebMvcTest(AuthController)` - 상태코드/스키마 검증
- [ ] **통합**: `@SpringBootTest` + `@Testcontainers(MySQL)` - 회원가입 → 로그인 → /me
- [ ] JaCoCo 보고서 커버리지 80%+
- **커밋**: `test(auth): 단위/슬라이스/통합 테스트 추가 (coverage 85%)`

### Step 6. 마무리 (0.5일)

- [ ] `application-dev.yml` / `application-prod.yml` 확인
- [ ] `pch-gateway` 라우팅 정상 동작 확인 (`/api/auth/**`, `/api/v1/users/**`)
- [ ] `docs/phases/phase-1/01-auth-service.md` 업데이트 (실제 구현과 차이 반영)
- [ ] Swagger UI 동작 확인 (`http://localhost:8081/swagger-ui.html`)
- **커밋**: `docs(auth): 구현 결과 반영 및 운영 가이드 추가`

---

## 💻 핵심 코드 스니펫

```java
// AuthService.java (핵심 로직 발췌)
@Transactional
public TokenResponse register(SignupRequest request) {
    if (userRepository.existsByEmail(request.email())) {
        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }
    User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.name());
    userRepository.save(user);
    publisher.publish(new UserCreatedEvent(user.getId(), user.getEmail(), user.getName(), LocalDateTime.now()));
    return issueTokens(user);
}
```

---

## 🧪 테스트 시나리오

| # | 시나리오                          | 예상 결과                                  |
|---|-----------------------------------|--------------------------------------------|
| 1 | 정상 회원가입 → 로그인 → /me       | 200, access/refresh, 프로필 응답           |
| 2 | 중복 이메일로 가입                | 409 Duplicate resource                      |
| 3 | 잘못된 비밀번호                   | 401 Invalid credentials                     |
| 4 | 만료된 Refresh                    | 401 Expired token                           |
| 5 | Access 토큰 변조                  | Gateway 401 (JwtAuthenticationFilter)       |
| 6 | Kafka 다운 상태에서 가입           | 회원가입 성공, 이벤트는 Outbox 테이블에 적재 |

---

## 🔀 PR 제출

**제목**: `feat(auth): Phase 1 — pch-auth-service 분리 (JWT 발급/검증, 사용자 CRUD)`

**본문**: [_templates/pr-template.md](./_templates/pr-template.md) 를 복사하여 작성, 관련 이슈 연결.

**리뷰어**: 최소 1명 (Auth 도메인 owner + 보안 담당자 권장)

---

## ✅ Definition of Done (Auth 전용 추가)

> 공통 DoD 는 [_templates/definition-of-done.md](./_templates/definition-of-done.md) 참고.

- [ ] `BCrypt` strength 10 이상
- [ ] JWT secret 32자 이상, **평문 하드코딩 금지** (env 주입)
- [ ] RefreshToken 회전 전략(토큰 재발급 시 이전 것 무효화) 구현
- [ ] 로그에 비밀번호 / 토큰 원문이 남지 않도록 `@Sensitive` 필터링
- [ ] `UserCreatedEvent` 가 Notification Service 쪽에서 소비 가능한 스키마로 고정

---

## ⚠️ 리스크 & 대응

| 리스크                           | 영향 | 대응                                               |
|----------------------------------|------|----------------------------------------------------|
| 모놀리스 사용자 테이블과 DB 동기화 | 중   | Phase 1 동안은 **모놀리스 테이블 읽기만**, 쓰기는 신규 DB |
| Kafka 전송 실패                  | 중   | Transactional Outbox 패턴(T2 에서 재처리)           |
| 비밀번호 정책 약함                | 고   | 영문+숫자+특수문자 ≥ 8자 검증, rate limit 병행       |
| Refresh 탈취                     | 고   | rotate on use + device fingerprint (Phase 2 보강)   |

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
