# Phase 3 — 검색/보드 분리 태스크 워크플로우

> **목적**: Phase 3 에서 수행할 서비스 분리 태스크(T1~T3)의 작업 순서, 커밋 전략, PR 체크리스트를 정의한다.
>
> **관련 문서**: [Phase 3 개요](../00-phase-3-overview.md) · [CQRS 데이터 전략](../../architecture/data-strategy.md) · [이벤트 카탈로그](../../architecture/event-catalog.md)

---

## 전체 태스크 맵

| 태스크 | 서비스 | 핵심 기술 | 예상 기간 | 브랜치 |
|--------|--------|----------|----------|--------|
| **T1** | Search Service | Elasticsearch + JQL 파서 | 5~7일 | `feature/phase-3-search` |
| **T2** | Board & Report Service | CQRS Read Model + Redis 캐시 | 5~7일 | `feature/phase-3-board-report` |
| **T3** | 통합 검증 | 이벤트 동기화 + Read Model 일관성 | 2~3일 | `feature/phase-3-integration-test` |

## 의존 관계

```
Phase 2 (Issue Service) ──필수──► T1 (Search)  ──병렬가능──► T3 (통합 검증)
                         ──필수──► T2 (Board)   ──병렬가능──►
```

T1과 T2는 **병렬 수행 가능**하나 둘 다 Phase 2 Issue Service의 이벤트 발행에 의존한다.

## 커밋 전략

Phase 1과 동일한 5단계 atomic commit:

| Step | 범위 | 커밋 프리픽스 |
|------|------|-------------|
| 1 | 도메인 엔티티/문서 + Repository + Flyway/Index 설정 | `feat(search):` / `feat(board):` |
| 2 | Service 레이어 + 핵심 비즈니스 로직 | `feat(search):` / `feat(board):` |
| 3 | REST API Controller + DTO | `feat(search):` / `feat(board):` |
| 4 | Kafka Consumer (이벤트 동기화) | `feat(search):` / `feat(board):` |
| 5 | Tests + config 업데이트 | `test(search):` / `test(board):` |

## PR 체크리스트 (공통)

```markdown
## Summary
- 서비스명, 변경 파일 수, 핵심 변경 사항

## Changes
| Step | 내용 | 커밋 SHA |

## Test plan
- [ ] 단위 테스트 통과
- [ ] 이벤트 동기화 검증
- [ ] Read Model 일관성 검증
```

## Definition of Done

- [ ] 모든 단위 테스트 통과
- [ ] Kafka Consumer가 이벤트 → Read Model 동기화 완료
- [ ] API 응답 시간 p95 < 200ms
- [ ] 공통 응답 래퍼 `ApiResponse<T>` 사용
- [ ] Conventional Commits 준수
- [ ] PROGRESS.md 업데이트
