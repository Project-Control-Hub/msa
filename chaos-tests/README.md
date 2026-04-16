# Chaos Engineering Test Suite

> PCH MSA 장애 복원력 검증을 위한 5종 시나리오

## Prerequisites

- Docker Compose 전체 스택 기동
- `tc` (iproute2) 설치: 네트워크 지연 시뮬레이션
- k6 설치: 부하 생성
- curl, jq: API 호출 및 결과 파싱

## 시나리오 목록

| # | 시나리오 | 검증 대상 | MTTR 목표 |
|---|---------|----------|----------|
| 1 | 서비스 인스턴스 다운 | Circuit Breaker + Fallback | < 5분 |
| 2 | 네트워크 지연 (500ms) | Timeout + CB + 캐시 폴백 | < 2분 |
| 3 | DB 연결 풀 고갈 | HikariCP 복구 | < 3분 |
| 4 | Kafka 브로커 다운 | Producer retry + Consumer lag | < 10분 |
| 5 | Redis 캐시 장애 | Cache miss → DB 폴백 | < 1분 |

## 실행 방법

```bash
# 개별 실행
bash chaos-tests/01-service-down.sh

# 전체 순차 실행
for script in chaos-tests/0*.sh; do
  echo "=== Running: $script ==="
  bash "$script"
  echo ""
done
```
