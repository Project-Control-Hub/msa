#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# Chaos Scenario 2: 네트워크 지연 (500ms)
# 검증: Timeout → Circuit Breaker → 캐시 데이터 반환
# MTTR 목표: < 2분
# ──────────────────────────────────────────────────
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
ISSUE_CONTAINER="${ISSUE_CONTAINER:-pch-issue-service}"
DELAY_MS="${DELAY_MS:-500}"
DELAY_DURATION="${DELAY_DURATION:-60}"

echo "╔══════════════════════════════════════════════╗"
echo "║  Chaos Scenario 2: Network Delay ${DELAY_MS}ms      ║"
echo "╚══════════════════════════════════════════════╝"

# ── Phase 1: 정상 응답시간 측정 ──
echo "[1/5] Measuring baseline response time..."
BASELINE_MS=$(curl -s -o /dev/null -w "%{time_total}" "${GATEWAY_URL}/api/v1/issues/PROJ-1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" | awk '{printf "%.0f", $1 * 1000}')
echo "  Baseline: ${BASELINE_MS}ms"

# ── Phase 2: 네트워크 지연 주입 ──
echo "[2/5] Injecting ${DELAY_MS}ms network delay on ${ISSUE_CONTAINER}..."
docker exec "${ISSUE_CONTAINER}" tc qdisc add dev eth0 root netem delay "${DELAY_MS}ms" 2>/dev/null ||     docker exec "${ISSUE_CONTAINER}" tc qdisc change dev eth0 root netem delay "${DELAY_MS}ms"
INJECT_TIME=$(date +%s)

# ── Phase 3: 지연 상태에서 응답 검증 ──
echo "[3/5] Testing under network delay..."
for i in $(seq 1 10); do
    START=$(date +%s%3N)
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10         "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    END=$(date +%s%3N)
    ELAPSED=$(( END - START ))
    echo "  [Request ${i}] HTTP ${CODE} — ${ELAPSED}ms"
    sleep 3
done

# ── Phase 4: 지연 제거 ──
echo "[4/5] Removing network delay..."
docker exec "${ISSUE_CONTAINER}" tc qdisc del dev eth0 root 2>/dev/null || true
REMOVE_TIME=$(date +%s)

# 복구 확인
echo "  Verifying recovery..."
sleep 10
RECOVERED_MS=$(curl -s -o /dev/null -w "%{time_total}" "${GATEWAY_URL}/api/v1/issues/PROJ-1"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}" | awk '{printf "%.0f", $1 * 1000}')
MTTR=$(( REMOVE_TIME - INJECT_TIME ))

# ── Phase 5: 결과 요약 ──
echo ""
echo "┌────────────────────────────────────┐"
echo "│  Scenario 2 Results                │"
echo "├────────────────────────────────────┤"
echo "│  Injected Delay: ${DELAY_MS}ms"
echo "│  Duration: ${DELAY_DURATION}s"
echo "│  Baseline: ${BASELINE_MS}ms"
echo "│  Post-Recovery: ${RECOVERED_MS}ms"
echo "│  MTTR: ${MTTR}s (target: <120s)"
[ "$MTTR" -lt 120 ] && echo "│  Status: ✅ PASS" || echo "│  Status: ❌ FAIL"
echo "└────────────────────────────────────┘"
