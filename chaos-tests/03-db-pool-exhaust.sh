#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# Chaos Scenario 3: DB 연결 풀 고갈
# 검증: Connection timeout → 503 → HikariCP 자동 복구
# MTTR 목표: < 3분
# ──────────────────────────────────────────────────
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
K6_SCRIPT="${K6_SCRIPT:-load-tests/scripts/issue-crud.js}"
CONCURRENT_VU="${CONCURRENT_VU:-50}"
DURATION="${DURATION:-30}"

echo "╔══════════════════════════════════════════════╗"
echo "║  Chaos Scenario 3: DB Connection Pool Exhaust║"
echo "╚══════════════════════════════════════════════╝"

# ── Phase 1: 현재 HikariCP 설정 확인 ──
echo "[1/5] Checking current HikariCP metrics..."
HIKARI_METRICS=$(curl -s "${GATEWAY_URL}/issue/actuator/metrics/hikaricp.connections.active" 2>/dev/null || echo '{"error":"unavailable"}')
echo "  HikariCP Active: ${HIKARI_METRICS}"

# ── Phase 2: 동시 부하로 풀 고갈 유도 ──
echo "[2/5] Generating ${CONCURRENT_VU} concurrent connections for ${DURATION}s..."
START_TIME=$(date +%s)

# 동시 curl 요청으로 풀 고갈 시뮬레이션
for i in $(seq 1 "${CONCURRENT_VU}"); do
    curl -s -o /dev/null "${GATEWAY_URL}/api/v1/issues/PROJ-${i}"         -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" &
done
echo "  ${CONCURRENT_VU} concurrent requests fired"

# ── Phase 3: 풀 고갈 상태 모니터링 ──
echo "[3/5] Monitoring connection pool status..."
sleep 5
for i in $(seq 1 6); do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10         "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    echo "  [Check ${i}] HTTP ${CODE}"
    sleep 5
done

# ── Phase 4: 부하 해제 + 복구 대기 ──
echo "[4/5] Waiting for connection pool recovery..."
wait 2>/dev/null || true
RECOVERED=false
for i in $(seq 1 18); do
    sleep 5
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10         "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    if [ "$CODE" -eq 200 ]; then
        RECOVERY_TIME=$(date +%s)
        MTTR=$(( RECOVERY_TIME - START_TIME ))
        echo "  ✅ Pool recovered! MTTR: ${MTTR}s"
        RECOVERED=true
        break
    fi
    echo "  [Recovery ${i}] HTTP ${CODE} — pool recovering..."
done

# ── Phase 5: 결과 요약 ──
echo ""
echo "┌────────────────────────────────────┐"
echo "│  Scenario 3 Results                │"
echo "├────────────────────────────────────┤"
echo "│  Concurrent VUs: ${CONCURRENT_VU}"
if [ "$RECOVERED" = true ]; then
    echo "│  MTTR: ${MTTR}s (target: <180s)"
    [ "$MTTR" -lt 180 ] && echo "│  Status: ✅ PASS" || echo "│  Status: ❌ FAIL"
else
    echo "│  MTTR: >90s (target: <180s)"
    echo "│  Status: ❌ FAIL"
fi
echo "└────────────────────────────────────┘"
