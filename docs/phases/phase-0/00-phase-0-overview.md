# Phase 0: 기반 인프라 구축 (2주)

## 개요

MSA 전환을 위한 인프라 기반을 마련하는 단계입니다. 모든 마이크로서비스가 공존할 수 있는 기술 스택과 운영 환경을 구축합니다.

## 목표

- 멀티모듈 Gradle 프로젝트 구조 확립
- 공유 라이브러리(pch-common) 분리
- API Gateway 및 Service Discovery 구축
- 이벤트 버스(Kafka) 통합
- 로컬 개발 환경 자동화 (Docker Compose)
- CI/CD 파이프라인 구축
- 모니터링 및 로깅 기반 준비

## 주요 산출물

| 항목 | 설명 | 담당자 |
|------|------|--------|
| `settings.gradle` | 멀티모듈 프로젝트 정의 | 빌드 담당자 |
| `pch-common` | 공유 라이브러리 모듈 | 아키텍처팀 |
| `pch-gateway` | Spring Cloud Gateway | 인프라팀 |
| `pch-discovery` | Eureka Server | 인프라팀 |
| `docker-compose.yml` | 개발 환경 정의 | DevOps |
| `.github/workflows/` | CI/CD 파이프라인 | DevOps |
| Monitoring Stack | Prometheus + Grafana | 모니터링팀 |

## 작업 항목 체크리스트

| ID | 작업명 | 설명 | 예상 소요시간 |
|----|--------|------|---------------|
| 0-1 | 멀티모듈 구조 설계 | settings.gradle 작성, 모듈 간 의존성 정의 | 2일 |
| 0-2 | pch-common 추출 | Enum, DTO, Event, Exception, Response 분리 | 3일 |
| 0-3 | API Gateway 구축 | Spring Cloud Gateway 설정, JWT 검증, 라우팅 규칙 정의 | 3일 |
| 0-4 | 이벤트 버스 구성 | Kafka 토픽 설계, Event Envelope 스키마 정의 | 2일 |
| 0-5 | Service Discovery | Eureka Server 구축, 클라이언트 등록 메커니즘 | 2일 |
| 0-6 | CI/CD 파이프라인 | GitHub Actions 워크플로우, 빌드/배포 자동화 | 3일 |
| 0-7 | Docker Compose | 로컬 개발 환경 스택 구성 | 2일 |
| 0-8 | 모니터링 기반 | Prometheus, Grafana, ELK Stack 초기 구성 | 3일 |

## 기술 스택

- **Framework**: Spring Boot 4.0.3
- **Language**: Java 21
- **Build**: Gradle 9.4
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Message Broker**: Apache Kafka (KRaft mode)
- **Database**: MySQL 8.0
- **Cache**: Redis
- **Container**: Docker & Docker Compose
- **Monitoring**: Prometheus, Grafana
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **CI/CD**: GitHub Actions

## 완료 기준 (Definition of Done)

### 기술적 완료 기준

- [ ] 모든 마이크로서비스가 Eureka에 정상 등록됨
  - Eureka 대시보드에서 8개 서비스 모두 UP 상태 확인
  - Heartbeat 정상 작동 (30초 주기)

- [ ] API Gateway 라우팅 정상 작동
  - `curl http://localhost:8000/api/v1/*` → 각 서비스로 정상 라우팅
  - JWT 검증 필터 동작 확인

- [ ] Kafka 토픽 생성 완료
  - 10개 토픽 모두 생성됨 (`kafka-topics.sh --list` 확인)
  - 각 토픽 replication factor 3 이상

- [ ] Docker Compose 로컬 환경 실행
  - `docker compose up -d` 후 모든 컨테이너 healthy 상태
  - `http://localhost:8761` Eureka 대시보드 접근 가능
  - `http://localhost:9090` Kafka UI 접근 가능

- [ ] CI/CD 파이프라인 검증
  - GitHub Actions 워크플로우 트리거 확인
  - 각 서비스의 Docker 이미지 빌드 및 레지스트리 푸시 성공

### 조직적 완료 기준

- [ ] 팀 전체가 로컬 개발 환경 구성 완료
  - 모든 개발자가 `docker compose up` 명령어로 환경 실행 가능

- [ ] 문서화 완료
  - 각 인프라 컴포넌트별 설치/구성/운영 가이드
  - 트러블슈팅 가이드

- [ ] 모니터링 대시보드 준비
  - Prometheus에 메트릭 수집 확인
  - Grafana 대시보드 템플릿 로드

## Phase 0 → Phase 1 전환 조건

Phase 0이 완료되어야 Phase 1(주변 서비스 분리)를 시작할 수 있습니다.

### 필수 조건

1. **인프라 안정성**: 위의 완료 기준을 모두 만족해야 함
2. **팀 준비**: 모든 개발자가 MSA 개발 환경에 익숙해져야 함
3. **문서 준비**: 모든 기술 문서 작성 및 팀 리뷰 완료

### 교육 및 훈련

Phase 1 시작 전 팀 전체를 대상으로 다음 교육 실시:
- Eureka를 이용한 서비스 등록 및 디스커버리
- API Gateway를 통한 라우팅 및 인증
- Kafka 이벤트 기반 통신 패턴
- Docker Compose를 이용한 로컬 환경 운영

## 다음 문서

1. [01-multi-module-setup.md](01-multi-module-setup.md) - Gradle 멀티모듈 구조 설계
2. [02-common-library.md](02-common-library.md) - pch-common 공유 라이브러리
3. [03-gateway-setup.md](03-gateway-setup.md) - API Gateway 구축
4. [04-discovery-setup.md](04-discovery-setup.md) - Service Discovery 구성
5. [05-kafka-setup.md](05-kafka-setup.md) - 이벤트 버스 구성
6. [06-docker-compose.md](06-docker-compose.md) - 로컬 개발 환경
7. [07-cicd-pipeline.md](07-cicd-pipeline.md) - CI/CD 파이프라인

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Status**: Approved
