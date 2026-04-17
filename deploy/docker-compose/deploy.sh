#!/usr/bin/env bash
# =============================================================================
# PCH MSA — Production Deployment Script
# Usage: ./deploy.sh [--pull] [--no-build] [--service <name>] [--down]
# =============================================================================

set -euo pipefail

# ---- Colours ----------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Colour

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.prod.yml"
ENV_FILE="${SCRIPT_DIR}/.env"

# ---- Parse arguments --------------------------------------------------------
PULL=false
NO_BUILD=false
TARGET_SERVICE=""
DO_DOWN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pull)       PULL=true        ; shift ;;
    --no-build)   NO_BUILD=true    ; shift ;;
    --service)    TARGET_SERVICE="$2" ; shift 2 ;;
    --down)       DO_DOWN=true     ; shift ;;
    -h|--help)
      echo "Usage: $0 [--pull] [--no-build] [--service <name>] [--down]"
      echo ""
      echo "  --pull            Pull pre-built images from registry instead of building"
      echo "  --no-build        Skip the build step (use existing local images)"
      echo "  --service <name>  Deploy / restart a single service only"
      echo "  --down            Tear down the stack (keeps volumes)"
      exit 0
      ;;
    *) echo -e "${RED}Unknown argument: $1${NC}" >&2 ; exit 1 ;;
  esac
done

# ---- Helpers ----------------------------------------------------------------
log()     { echo -e "${CYAN}[$(date '+%H:%M:%S')] $*${NC}"; }
success() { echo -e "${GREEN}[$(date '+%H:%M:%S')] $*${NC}"; }
warn()    { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARNING: $*${NC}"; }
error()   { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR: $*${NC}" >&2; exit 1; }

# ---- Validate environment ---------------------------------------------------
validate_env() {
  log "Validating environment..."

  if [[ ! -f "${ENV_FILE}" ]]; then
    error ".env file not found at ${ENV_FILE}.
Please copy .env.prod.example to .env and fill in all CHANGE_ME values:
  cp '${SCRIPT_DIR}/.env.prod.example' '${ENV_FILE}'"
  fi

  # Check for any unfilled CHANGE_ME placeholders
  if grep -q "CHANGE_ME" "${ENV_FILE}"; then
    warn "The following variables still contain 'CHANGE_ME' placeholders:"
    grep "CHANGE_ME" "${ENV_FILE}" | sed 's/=.*//' | while read -r var; do
      echo "  - ${var}"
    done
    echo ""
    read -rp "Continue anyway? [y/N] " confirm
    [[ "${confirm}" =~ ^[Yy]$ ]] || exit 1
  fi

  # Verify required secrets are non-empty after sourcing .env
  # shellcheck disable=SC1090
  set -a; source "${ENV_FILE}"; set +a

  local required_vars=(
    MYSQL_ROOT_PASSWORD
    MYSQL_PASSWORD
    REDIS_PASSWORD
    JWT_SECRET
  )
  local missing=()
  for var in "${required_vars[@]}"; do
    if [[ -z "${!var:-}" ]]; then
      missing+=("${var}")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    error "Required variables are empty: ${missing[*]}"
  fi

  success "Environment validated."
}

# ---- Docker / compose availability ------------------------------------------
check_dependencies() {
  log "Checking dependencies..."
  command -v docker >/dev/null 2>&1 || error "docker is not installed or not on PATH."

  if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    error "Neither 'docker compose' plugin nor 'docker-compose' CLI found."
  fi
  log "Using compose command: ${COMPOSE_CMD}"
}

# ---- Compose wrapper --------------------------------------------------------
compose() {
  ${COMPOSE_CMD} \
    --file "${COMPOSE_FILE}" \
    --env-file "${ENV_FILE}" \
    "$@"
}

# ---- Tear down --------------------------------------------------------------
do_down() {
  warn "Tearing down the stack. Named volumes will be PRESERVED."
  compose down --remove-orphans
  success "Stack stopped."
  exit 0
}

# ---- Build images -----------------------------------------------------------
build_images() {
  if [[ "${NO_BUILD}" == true ]]; then
    log "Skipping build (--no-build)."
    return
  fi

  if [[ "${PULL}" == true ]]; then
    log "Pulling pre-built images from registry..."
    compose pull
    return
  fi

  log "Building all application images (parallel)..."
  compose build \
    --parallel \
    --progress plain \
    pch-discovery \
    pch-gateway \
    pch-auth-service \
    pch-project-service \
    pch-issue-service \
    pch-board-report-service \
    pch-search-service \
    pch-notification-service \
    pch-file-service \
    pch-integration-service

  success "All images built."
}

# ---- Start infrastructure ---------------------------------------------------
start_infra() {
  log "Starting infrastructure services..."
  compose up -d \
    mysql \
    redis \
    kafka \
    elasticsearch \
    prometheus \
    grafana

  log "Waiting for infrastructure health checks..."
  local infra_services=(mysql redis kafka elasticsearch)
  for svc in "${infra_services[@]}"; do
    wait_healthy "${svc}"
  done
  success "Infrastructure is healthy."
}

# ---- Wait for a container to be healthy -------------------------------------
wait_healthy() {
  local service="$1"
  local container
  container=$(compose ps -q "${service}" 2>/dev/null | head -1)
  if [[ -z "${container}" ]]; then
    warn "Container for '${service}' not found — skipping health wait."
    return
  fi

  log "Waiting for '${service}' to be healthy..."
  local max_attempts=60
  local attempt=0
  while [[ ${attempt} -lt ${max_attempts} ]]; do
    local status
    status=$(docker inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo "unknown")
    if [[ "${status}" == "healthy" ]]; then
      success "'${service}' is healthy."
      return
    fi
    sleep 3
    (( attempt++ ))
  done
  error "'${service}' did not become healthy within $((max_attempts * 3)) seconds."
}

# ---- Start discovery --------------------------------------------------------
start_discovery() {
  log "Starting pch-discovery (Eureka)..."
  compose up -d pch-discovery
  wait_healthy pch-discovery
}

# ---- Start gateway ----------------------------------------------------------
start_gateway() {
  log "Starting pch-gateway..."
  compose up -d pch-gateway
  wait_healthy pch-gateway
}

# ---- Start application services ---------------------------------------------
start_app_services() {
  log "Starting all application services..."
  compose up -d \
    pch-auth-service \
    pch-project-service \
    pch-issue-service \
    pch-board-report-service \
    pch-search-service \
    pch-notification-service \
    pch-file-service \
    pch-integration-service

  success "All application services started."
}

# ---- Deploy a single service ------------------------------------------------
deploy_single_service() {
  local svc="${TARGET_SERVICE}"
  log "Deploying single service: ${svc}"

  if [[ "${NO_BUILD}" == false && "${PULL}" == false ]]; then
    log "Building image for ${svc}..."
    compose build --progress plain "${svc}"
  elif [[ "${PULL}" == true ]]; then
    compose pull "${svc}"
  fi

  compose up -d --no-deps "${svc}"
  wait_healthy "${svc}"
  success "Service '${svc}' deployed."
}

# ---- Print health status ----------------------------------------------------
print_health_status() {
  log "Current container health status:"
  echo ""
  printf "%-35s %-15s %-10s\n" "CONTAINER" "STATUS" "HEALTH"
  printf "%-35s %-15s %-10s\n" "---------" "------" "------"

  local services=(
    mysql redis kafka elasticsearch
    prometheus grafana
    pch-discovery pch-gateway
    pch-auth-service pch-project-service pch-issue-service
    pch-board-report-service pch-search-service pch-notification-service
    pch-file-service pch-integration-service
  )

  for svc in "${services[@]}"; do
    local container
    container=$(compose ps -q "${svc}" 2>/dev/null | head -1)
    if [[ -z "${container}" ]]; then
      printf "%-35s %-15s %-10s\n" "${svc}" "not found" "-"
      continue
    fi
    local running health
    running=$(docker inspect --format='{{.State.Status}}' "${container}" 2>/dev/null || echo "unknown")
    health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}N/A{{end}}' "${container}" 2>/dev/null || echo "unknown")
    printf "%-35s %-15s %-10s\n" "${svc}" "${running}" "${health}"
  done
  echo ""
}

# ---- Print access URLs ------------------------------------------------------
print_urls() {
  # shellcheck disable=SC1090
  set -a; source "${ENV_FILE}" 2>/dev/null || true; set +a
  echo -e "${CYAN}--------------------------------------------------------------------${NC}"
  echo -e "${CYAN}  Service Endpoints${NC}"
  echo -e "${CYAN}--------------------------------------------------------------------${NC}"
  printf "  %-30s %s\n" "Eureka Dashboard"     "http://localhost:${DISCOVERY_HOST_PORT:-8761}"
  printf "  %-30s %s\n" "API Gateway"          "http://localhost:${GATEWAY_HOST_PORT:-8000}"
  printf "  %-30s %s\n" "Auth Service"         "http://localhost:${AUTH_HOST_PORT:-8081}"
  printf "  %-30s %s\n" "Project Service"      "http://localhost:${PROJECT_HOST_PORT:-8082}/project"
  printf "  %-30s %s\n" "Issue Service"        "http://localhost:${ISSUE_HOST_PORT:-8083}/issue"
  printf "  %-30s %s\n" "Board/Report Service" "http://localhost:${BOARD_REPORT_HOST_PORT:-8084}/board-report"
  printf "  %-30s %s\n" "Search Service"       "http://localhost:${SEARCH_HOST_PORT:-8085}/search"
  printf "  %-30s %s\n" "Notification Service" "http://localhost:${NOTIFICATION_HOST_PORT:-8086}"
  printf "  %-30s %s\n" "File Service"         "http://localhost:${FILE_HOST_PORT:-8087}/file"
  printf "  %-30s %s\n" "Integration Service"  "http://localhost:${INTEGRATION_HOST_PORT:-8088}/integration"
  printf "  %-30s %s\n" "Prometheus"           "http://localhost:${PROMETHEUS_HOST_PORT:-9090}"
  printf "  %-30s %s\n" "Grafana"              "http://localhost:${GRAFANA_HOST_PORT:-3000}"
  echo -e "${CYAN}--------------------------------------------------------------------${NC}"
}

# ---- Main -------------------------------------------------------------------
main() {
  echo -e "${CYAN}============================================================${NC}"
  echo -e "${CYAN}  PCH MSA — Production Deployment${NC}"
  echo -e "${CYAN}  $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${CYAN}============================================================${NC}"
  echo ""

  check_dependencies
  validate_env

  if [[ "${DO_DOWN}" == true ]]; then
    do_down
  fi

  if [[ -n "${TARGET_SERVICE}" ]]; then
    deploy_single_service
    print_health_status
    print_urls
    exit 0
  fi

  build_images
  start_infra
  start_discovery
  start_gateway
  start_app_services

  # Give app services a moment to register with Eureka before printing status
  log "Waiting 15 seconds for Eureka registration..."
  sleep 15

  print_health_status
  print_urls
  success "Deployment complete."
}

main "$@"
