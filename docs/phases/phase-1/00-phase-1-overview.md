# Phase 1: 주변 서비스 분리 (4주)

## 개요

Phase 0에서 구축한 인프라를 기반으로 결합도가 낮은 마이크로서비스부터 단계별로 분리합니다. 각 주마다 독립적인 서비스를 완성하여 점진적으로 MSA 운영 역량을 확보합니다.

## 목표

- 결합도가 낮은 서비스 우선 분리로 MSA 전환 리스크 최소화
- 각 서비스 분리 시마다 통합 테스트 실행으로 품질 보증
- 팀 전체가 MSA 개발 패턴 습득
- 비즈니스 서비스 간 통신 패턴 확립 (Event-Driven)

## 타임라인

| 주차 | 서비스 | 작업 범위 | 도전도 |
|------|--------|---------|-------|
| 1주 | Auth Service | 사용자 인증, JWT, 계정 관리 | ★☆☆☆☆ |
| 2주 | Notification + File Service | 알림, 첨부파일 처리 | ★☆☆☆☆ |
| 3주 | Integration Service + Project Service | VCS 연동, 프로젝트 관리 | ★★☆☆☆ |
| 4주 | 통합 검증 | E2E 테스트, 성능 기준선, 장애 시나리오 | ★★☆☆☆ |

## 분리 순서 근거

### 낮은 결합도 우선 (Low Coupling First)

1. **Auth Service** (독립적)
   - 다른 서비스의 의존성 최소
   - 순수 인증/인가 로직
   - 실패 영향범위 제한

2. **Notification Service** (비즈니스 로직 없음)
   - Event Consumer 역할만 수행
   - 다른 서비스와 독립적
   - 실패해도 핵심 서비스에 영향 없음

3. **File Service** (이슈에만 의존)
   - 단순 CRUD 작업
   - 비즈니스 로직 최소
   - 이벤트 기반 정리만 필요

4. **Integration Service** (VCS 연동)
   - 외부 시스템 연동
   - Issue 서비스와 협력
   - Webhook 기반 이벤트

5. **Project Service** (복잡도 증가)
   - Sprint, Release, Member, Label 관리
   - 여러 이벤트 구독/발행
   - Issue 서비스와 상호작용
   - 기술적 도전 증가

## 완료 기준 (Definition of Done)

### 각 서비스별 완료 기준

- [ ] 모놀리스에서 코드 분리 완료
- [ ] 서비스별 DB 분리 완료
- [ ] 내부 API 구현 (다른 서비스의 조회 필요시)
- [ ] Event 발행/구독 설정
- [ ] Gateway 라우팅 규칙 추가
- [ ] Docker 이미지 빌드 성공
- [ ] E2E 테스트 성공 (문제 시나리오 포함)
- [ ] 문서화 완료

### Phase 1 전체 완료 기준

- [ ] 5개 서비스(Auth, Notification, File, Integration, Project) 모두 분리 완료
- [ ] Eureka에서 8개 서비스 모두 UP 상태 유지
- [ ] Kafka를 통한 비동기 통신 정상 작동
- [ ] API Gateway를 통한 모든 라우팅 정상 작동
- [ ] 분리 전후 API 응답 시간 비교 (성능 저하 없음)
- [ ] 장애 시나리오 테스트 통과
  - Auth Service 다운 시: Gateway 에러 반환
  - Notification Service 다운 시: 이벤트 DLQ 저장 후 재시도
  - 기타 서비스 다운 시: Circuit Breaker 동작
- [ ] 모놀리스에서 분리된 기능 완전 제거 확인

## 각 서비스 난이도 평가

| 서비스 | 난이도 | 이유 | 소요시간 |
|--------|--------|------|---------|
| Auth | ★☆☆☆☆ | 순수 인증, 의존성 최소 | 3-4일 |
| Notification | ★☆☆☆☆ | Event Consumer만 구현 | 2-3일 |
| File | ★☆☆☆☆ | 단순 CRUD, 스토리지 추상화 | 2-3일 |
| Integration | ★☆☆☆☆ | 외부 API 연동, 웹훅 처리 | 3-4일 |
| Project | ★★☆☆☆ | 여러 도메인, 복잡한 도메인 로직 | 5-7일 |

## 팀 구성 및 역할

### Auth + Notification 팀 (1-2주)
- 팀장: 시니어 개발자 1명
- 팀원: 주니어 개발자 2명
- 목표: Auth 기본 분리, Notification 완성

### File + Integration 팀 (1-2주)
- 팀장: 시니어 개발자 1명
- 팀원: 주니어 개발자 2명
- 목표: File, Integration 완성

### Project 팀 (2주)
- 팀장: 아키텍트 1명 + 시니어 개발자 1명
- 팀원: 개발자 2-3명
- 목표: Project 완성, 복잡도 관리

### QA 팀 (4주 전체)
- QA 엔지니어 2명
- 목표: 각 단계별 E2E 테스트, 통합 검증

## 위험 요소 및 대응 방안

| 위험 | 영향도 | 대응 방안 |
|------|--------|---------|
| 데이터 일관성 | 높음 | 트랜잭션 경계 재설계, Saga 패턴 검토 |
| 네트워크 지연 | 중간 | Retry, Circuit Breaker, Timeout 설정 |
| 모놀리스 코드 의존성 | 높음 | 코드 분석 도구로 사전 검증 |
| DB 마이그레이션 | 높음 | Blue/Green 전략, 롤백 계획 |
| 팀 학습 곡선 | 중간 | 사전 교육, 페어 프로그래밍 |

## Phase 1 → Phase 2 전환 기준

Phase 1 완료 후 Issue Service와 Report Service 분리를 진행합니다.

### 필수 조건

1. **모든 서비스 안정성**
   - 5개 서비스 모두 72시간 이상 무중단 운영
   - 에러율 1% 이하

2. **자동화 검증**
   - 모든 E2E 테스트 통과
   - 성능 저하 없음 (99th percentile 동일 수준)

3. **문서화 완료**
   - API 문서 (모든 엔드포인트)
   - 배포 가이드 (각 서비스)
   - 운영 가이드 (모니터링, 장애 대응)

## 문서 구조

```
Phase 1 Documentation
├── 00-phase-1-overview.md          (이 문서)
├── 01-auth-service.md              (1주차)
├── 02-notification-service.md      (2주차)
├── 03-file-service.md              (2주차)
├── 04-integration-service.md       (3주차)
├── 05-project-service.md           (3주차)
└── 06-integration-testing.md       (4주차)
```

## 다음 단계

1. [01-auth-service.md](01-auth-service.md) - Auth Service 분리
2. [02-notification-service.md](02-notification-service.md) - Notification Service 분리
3. [03-file-service.md](03-file-service.md) - File Service 분리
4. [04-integration-service.md](04-integration-service.md) - Integration Service 분리
5. [05-project-service.md](05-project-service.md) - Project Service 분리
6. [06-integration-testing.md](06-integration-testing.md) - Phase 1 통합 검증

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [../phase-0/00-phase-0-overview.md](../phase-0/00-phase-0-overview.md)
- [../phase-0/02-common-library.md](../phase-0/02-common-library.md)
- [../phase-0/05-kafka-setup.md](../phase-0/05-kafka-setup.md)
