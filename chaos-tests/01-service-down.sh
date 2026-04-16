#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# Chaos Scenario 1: 서비스 인스턴스 다운
# 검증: Circuit Breaker OPEN → Fallback 503 → 자동 복구
# MTTR 목표: < 5분
# ──────────────────────────────────────────────────
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
ISSUE_CONTAINER="${ISSUE_CONTAINER:-pch-issue-service}"
DOWN_DURATION="${DOWN_DURATION:-60}"  # seconds

echo "╔══════════════════════════════════════════════╗"
echo "║  Chaos Scenario 1: Service Instance Down     ║"
echo "╚══════════════════════════════════════════════╝"

# ── Phase 1: 정상 상태 확인 ──
echo "[1/5] Verifying baseline health..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
echo "  Issue API status: ${HEALTH}"

# ── Phase 2: 서비스 중단 ──
echo "[2/5] Stopping ${ISSUE_CONTAINER} for ${DOWN_DURATION}s..."
docker stop "${ISSUE_CONTAINER}"
STOP_TIME=$(date +%s)

# ── Phase 3: Circuit Breaker 검증 ──
echo "[3/5] Verifying Circuit Breaker activation..."
sleep 5
for i in $(seq 1 12); do
    RESPONSE=$(curl -s "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    STATUS=$(echo "$RESPONSE" | jq -r '.success // empty' 2>/dev/null || echo "parse_error")
    MSG=$(echo "$RESPONSE" | jq -r '.message // empty' 2>/dev/null || echo "")
    echo "  [Attempt ${i}] success=${STATUS} message='${MSG}'"
    sleep 5
done

# ── Phase 4: 서비스 복구 ──
echo "[4/5] Restarting ${ISSUE_CONTAINER}..."
docker start "${ISSUE_CONTAINER}"
RESTART_TIME=$(date +%s)

# 복구 대기 + MTTR 측정
echo "  Waiting for service recovery..."
RECOVERED=false
for i in $(seq 1 30); do
    sleep 5
    CODE=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/api/v1/issues/PROJ-1" -H "Authorization: Bearer ${TEST_TOKEN:-dummy}")
    if [ "$CODE" -ne 503 ] && [ "$CODE" -ne 504 ]; then
        RECOVERY_TIME=$(date +%s)
        MTTR=$(( RECOVERY_TIME - STOP_TIME ))
        echo "  ✅ Service recovered! MTTR: ${MTTR}s"
        RECOVERED=true
        break
    fi
    echo "  [Recovery check ${i}] HTTP ${CODE} — still recovering..."
done

if [ "$RECOVERED" = false ]; then
    echo "  ❌ Service did NOT recover within 150s!"
fi

# ── Phase 5: 결과 요약 ──
echo ""
echo "┌────────────────────────────────────┐"
echo "│  Scenario 1 Results                │"
echo "├────────────────────────────────────┤"
echo "│  Target: ${ISSUE_CONTAINER}"
echo "│  Downtime: ${DOWN_DURATION}s"
if [ "$RECOVERED" = true ]; then
    echo "│  MTTR: ${MTTR}s (target: <300s)"
    [ "$MTTR" -lt 300 ] && echo "│  Status: ✅ PASS" || echo "│  Status: ❌ FAIL"
else
    echo "│  MTTR: >150s (target: <300s)"
    echo "│  Status: ❌ FAIL"
fi
echo "└────────────────────────────────────┘"
