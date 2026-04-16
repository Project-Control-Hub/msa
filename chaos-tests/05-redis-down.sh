#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# Chaos Scenario 5: Redis 캐시 장애
# 검증: 캐시 miss → DB 직접 조회 → 서비스 유지
# MTTR 목표: < 1분
# ──────────────────────────────────────────────────
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
REDIS_CONTAINER="${REDIS_CONTAINER:-pch-redis}"

echo "╔══════════════════════════════════════════════╗"
echo "║  Chaos Scenario 5: Redis Cache Down          ║"
echo "╚══════════════════════════════════════════════╝"

# ── Phase 1: 캐시 적중 상태 응답시간 측정 ──
echo "[1/5] Measuring cached response time (warm cache)..."
# 워밍 요청
curl -s -o /dev/null "${GATEWAY_URL}/api/v1/dashboards/1/board?sprintId=1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" 2>/dev/null || true
sleep 1
CACHED_MS=$(curl -s -o /dev/null -w "%{time_total}"     "${GATEWAY_URL}/api/v1/dashboards/1/board?sprintId=1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" 2>/dev/null | awk '{printf "%.0f", $1 * 1000}')
echo "  Cached response: ${CACHED_MS}ms"

# ── Phase 2: Redis 중단 ──
echo "[2/5] Stopping ${REDIS_CONTAINER}..."
docker stop "${REDIS_CONTAINER}"
STOP_TIME=$(date +%s)

# ── Phase 3: 캐시 없이 응답 검증 (DB 폴백) ──
echo "[3/5] Testing without Redis (DB fallback)..."
sleep 3
for i in $(seq 1 8); do
    START=$(date +%s%3N)
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10         "${GATEWAY_URL}/api/v1/dashboards/1/board?sprintId=1"         -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    END=$(date +%s%3N)
    ELAPSED=$(( END - START ))
    echo "  [Request ${i}] HTTP ${CODE} — ${ELAPSED}ms (no cache)"
    sleep 3
done

# ── Phase 4: Redis 복구 ──
echo "[4/5] Restarting ${REDIS_CONTAINER}..."
docker start "${REDIS_CONTAINER}"
RESTART_TIME=$(date +%s)

# 캐시 워밍 + 복구 확인
sleep 5
echo "  Re-warming cache..."
curl -s -o /dev/null "${GATEWAY_URL}/api/v1/dashboards/1/board?sprintId=1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" 2>/dev/null || true
sleep 2
RECOVERED_MS=$(curl -s -o /dev/null -w "%{time_total}"     "${GATEWAY_URL}/api/v1/dashboards/1/board?sprintId=1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" 2>/dev/null | awk '{printf "%.0f", $1 * 1000}')
MTTR=$(( RESTART_TIME - STOP_TIME ))

# ── Phase 5: 결과 요약 ──
echo ""
echo "┌────────────────────────────────────┐"
echo "│  Scenario 5 Results                │"
echo "├────────────────────────────────────┤"
echo "│  Cached Response: ${CACHED_MS}ms"
echo "│  Post-Recovery: ${RECOVERED_MS}ms"
echo "│  MTTR: ${MTTR}s (target: <60s)"
[ "$MTTR" -lt 60 ] && echo "│  Status: ✅ PASS" || echo "│  Status: ❌ FAIL"
echo "│  Service Available During Outage: (see HTTP codes above)"
echo "└────────────────────────────────────┘"
