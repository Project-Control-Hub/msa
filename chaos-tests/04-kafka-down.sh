#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# Chaos Scenario 4: Kafka 브로커 다운
# 검증: Producer retry + Consumer lag 증가 → 복구 후 메시지 처리
# MTTR 목표: < 10분
# ──────────────────────────────────────────────────
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-pch-kafka}"
DOWN_DURATION="${DOWN_DURATION:-120}"

echo "╔══════════════════════════════════════════════╗"
echo "║  Chaos Scenario 4: Kafka Broker Down         ║"
echo "╚══════════════════════════════════════════════╝"

# ── Phase 1: 정상 이벤트 흐름 확인 ──
echo "[1/6] Verifying baseline Kafka health..."
docker exec "${KAFKA_CONTAINER}" kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | head -5
echo "  Kafka topics listed successfully"

# ── Phase 2: 이슈 생성 (이벤트 발행 확인) ──
echo "[2/6] Creating test issue to verify event flow..."
CREATE_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/v1/issues"     -H "Content-Type: application/json"     -H "Authorization: Bearer ${TEST_TOKEN:-dummy}"     -d '{"title":"Chaos Test Issue","projectId":1,"issueType":"TASK","priority":"MEDIUM"}' 2>/dev/null || echo '{"error":"failed"}')
echo "  Create response: $(echo "$CREATE_RESP" | jq -r '.issueKey // .error // "unknown"' 2>/dev/null)"

# ── Phase 3: Kafka 중단 ──
echo "[3/6] Stopping ${KAFKA_CONTAINER} for ${DOWN_DURATION}s..."
docker stop "${KAFKA_CONTAINER}"
STOP_TIME=$(date +%s)

# ── Phase 4: Kafka 없이 이슈 생성 시도 (Producer retry 검증) ──
echo "[4/6] Testing issue creation without Kafka..."
for i in $(seq 1 5); do
    sleep 5
    START=$(date +%s%3N)
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15         -X POST "${GATEWAY_URL}/api/v1/issues"         -H "Content-Type: application/json"         -H "Authorization: Bearer ${TEST_TOKEN:-dummy}"         -d "{"title":"Chaos Issue ${i}","projectId":1,"issueType":"TASK","priority":"LOW"}")
    END=$(date +%s%3N)
    echo "  [Attempt ${i}] HTTP ${CODE} — $((END - START))ms"
done

# ── Phase 5: Kafka 복구 ──
echo "[5/6] Restarting ${KAFKA_CONTAINER}..."
docker start "${KAFKA_CONTAINER}"
RESTART_TIME=$(date +%s)

echo "  Waiting for Kafka recovery + consumer lag drain..."
RECOVERED=false
for i in $(seq 1 36); do
    sleep 10
    # Kafka 상태 확인
    KAFKA_OK=$(docker exec "${KAFKA_CONTAINER}" kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null && echo "yes" || echo "no")
    if [ "$KAFKA_OK" = "yes" ]; then
        RECOVERY_TIME=$(date +%s)
        MTTR=$(( RECOVERY_TIME - STOP_TIME ))
        echo "  ✅ Kafka recovered! MTTR: ${MTTR}s"
        RECOVERED=true
        break
    fi
    echo "  [Recovery check ${i}] Kafka not ready..."
done

# Consumer lag 확인 (복구 후)
if [ "$RECOVERED" = true ]; then
    echo "  Checking consumer lag after recovery..."
    sleep 15
    docker exec "${KAFKA_CONTAINER}" kafka-consumer-groups.sh         --bootstrap-server localhost:9092         --group pch-board-report-service         --describe 2>/dev/null | head -10 || echo "  (lag check unavailable)"
fi

# ── Phase 6: 결과 요약 ──
echo ""
echo "┌────────────────────────────────────┐"
echo "│  Scenario 4 Results                │"
echo "├────────────────────────────────────┤"
echo "│  Kafka Downtime: ${DOWN_DURATION}s"
if [ "$RECOVERED" = true ]; then
    echo "│  MTTR: ${MTTR}s (target: <600s)"
    [ "$MTTR" -lt 600 ] && echo "│  Status: ✅ PASS" || echo "│  Status: ❌ FAIL"
else
    echo "│  MTTR: >360s (target: <600s)"
    echo "│  Status: ❌ FAIL"
fi
echo "│  Event Loss: (check consumer lag above)"
echo "└────────────────────────────────────┘"
