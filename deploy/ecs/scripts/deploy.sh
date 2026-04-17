#!/usr/bin/env bash
# =============================================================================
#  PCH MSA — ECS Fargate Deployment Script
#
#  Usage:
#    ./deploy.sh [OPTIONS]
#
#  Options:
#    -e, --env         Environment (dev|staging|prod)    [default: dev]
#    -r, --region      AWS region                        [default: ap-northeast-2]
#    -t, --tag         Image tag to build/push/deploy    [default: git SHA]
#    -s, --services    Comma-separated service list      [default: all]
#        --skip-build  Skip Docker build + ECR push step
#        --skip-deploy Skip ECS task/service update step
#    -h, --help        Show this help message
#
#  Examples:
#    # Full deploy of all services to dev
#    ./deploy.sh --env dev
#
#    # Deploy only gateway and auth-service to staging
#    ./deploy.sh --env staging --services pch-gateway,pch-auth-service
#
#    # Re-deploy with existing image (skip build)
#    ./deploy.sh --env prod --tag v1.2.3 --skip-build
# =============================================================================
set -euo pipefail

# ──────────────────────────────────────────────
#  Defaults
# ──────────────────────────────────────────────
ENVIRONMENT="dev"
AWS_REGION="ap-northeast-2"
IMAGE_TAG=""
SELECTED_SERVICES=""
SKIP_BUILD=false
SKIP_DEPLOY=false

# Service definitions: name:port (in dependency order)
ALL_SERVICES=(
  "pch-discovery:8761"
  "pch-gateway:8000"
  "pch-auth-service:8081"
  "pch-project-service:8082"
  "pch-issue-service:8083"
  "pch-board-report-service:8084"
  "pch-search-service:8085"
  "pch-notification-service:8086"
  "pch-file-service:8087"
  "pch-integration-service:8088"
)

# ──────────────────────────────────────────────
#  Colours
# ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Colour

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()    { echo -e "\n${CYAN}══════════════════════════════════════════${NC}"; echo -e "${CYAN}  $*${NC}"; echo -e "${CYAN}══════════════════════════════════════════${NC}"; }

# ──────────────────────────────────────────────
#  Usage
# ──────────────────────────────────────────────
usage() {
  grep '^#' "$0" | grep -v '#!/' | sed 's/^#[[:space:]]*//'
  exit 0
}

# ──────────────────────────────────────────────
#  Parse arguments
# ──────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    -e|--env)       ENVIRONMENT="$2"; shift 2 ;;
    -r|--region)    AWS_REGION="$2"; shift 2 ;;
    -t|--tag)       IMAGE_TAG="$2"; shift 2 ;;
    -s|--services)  SELECTED_SERVICES="$2"; shift 2 ;;
    --skip-build)   SKIP_BUILD=true; shift ;;
    --skip-deploy)  SKIP_DEPLOY=true; shift ;;
    -h|--help)      usage ;;
    *) log_error "Unknown option: $1"; usage ;;
  esac
done

# ──────────────────────────────────────────────
#  Derived variables
# ──────────────────────────────────────────────
STACK_PREFIX="pch-msa-${ENVIRONMENT}"
NETWORK_STACK="${STACK_PREFIX}-network"
CLUSTER_STACK="${STACK_PREFIX}-cluster"
SERVICES_STACK="${STACK_PREFIX}-services"

# Default tag = short git SHA
if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
fi

# Resolve which services to deploy
if [[ -n "$SELECTED_SERVICES" ]]; then
  IFS=',' read -ra DEPLOY_SERVICES <<< "$SELECTED_SERVICES"
else
  DEPLOY_SERVICES=()
  for svc_entry in "${ALL_SERVICES[@]}"; do
    DEPLOY_SERVICES+=("${svc_entry%%:*}")
  done
fi

# ──────────────────────────────────────────────
#  Prerequisite checks
# ──────────────────────────────────────────────
check_prerequisites() {
  log_step "Checking prerequisites"
  local missing=()
  for cmd in aws docker git jq; do
    if ! command -v "$cmd" &>/dev/null; then
      missing+=("$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    log_error "Missing required tools: ${missing[*]}"
    exit 1
  fi

  # Verify AWS credentials
  if ! aws sts get-caller-identity --region "$AWS_REGION" &>/dev/null; then
    log_error "AWS credentials not configured or invalid. Run 'aws configure' or set env vars."
    exit 1
  fi

  AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "$AWS_REGION")
  ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  log_success "AWS Account: ${AWS_ACCOUNT_ID} | Region: ${AWS_REGION}"
  log_success "Environment: ${ENVIRONMENT} | Tag: ${IMAGE_TAG}"
}

# ──────────────────────────────────────────────
#  ECR Login
# ──────────────────────────────────────────────
ecr_login() {
  log_step "Logging in to Amazon ECR"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "${ECR_REGISTRY}"
  log_success "ECR login successful"
}

# ──────────────────────────────────────────────
#  Build & Push one service
# ──────────────────────────────────────────────
build_and_push() {
  local service="$1"
  local repo="pch-msa/${ENVIRONMENT}/${service}"
  local image_uri="${ECR_REGISTRY}/${repo}:${IMAGE_TAG}"
  local image_uri_latest="${ECR_REGISTRY}/${repo}:latest"
  local project_root
  project_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

  log_info "Building ${service} → ${image_uri}"

  docker build \
    --build-arg SERVICE="${service}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    --cache-from "${image_uri_latest}" \
    --tag "${image_uri}" \
    --tag "${image_uri_latest}" \
    --file "${project_root}/Dockerfile" \
    "${project_root}"

  log_info "Pushing ${image_uri}"
  docker push "${image_uri}"
  docker push "${image_uri_latest}"

  log_success "Pushed ${service}:${IMAGE_TAG}"
  echo "${image_uri}"
}

# ──────────────────────────────────────────────
#  Build Phase (parallel builds per service)
# ──────────────────────────────────────────────
run_builds() {
  log_step "Building & pushing Docker images (parallel)"

  declare -A BUILD_PIDS
  declare -A BUILD_LOGS
  declare -A IMAGE_URIS

  for svc in "${DEPLOY_SERVICES[@]}"; do
    local log_file
    log_file=$(mktemp /tmp/pch-build-XXXXXX.log)
    BUILD_LOGS["$svc"]="$log_file"
    (
      image_uri=$(build_and_push "$svc" 2>&1 | tee "$log_file" | tail -1)
      echo "$image_uri" >> "$log_file.uri"
    ) &
    BUILD_PIDS["$svc"]=$!
    log_info "Started build for ${svc} (PID: ${BUILD_PIDS[$svc]})"
  done

  local failed=()
  for svc in "${DEPLOY_SERVICES[@]}"; do
    if wait "${BUILD_PIDS[$svc]}"; then
      local uri_file="${BUILD_LOGS[$svc]}.uri"
      if [[ -f "$uri_file" ]]; then
        IMAGE_URIS["$svc"]=$(cat "$uri_file")
      fi
      log_success "Build completed: ${svc}"
    else
      failed+=("$svc")
      log_error "Build failed: ${svc}"
      log_error "--- Build log for ${svc} ---"
      cat "${BUILD_LOGS[$svc]}" >&2
    fi
    rm -f "${BUILD_LOGS[$svc]}" "${BUILD_LOGS[$svc]}.uri"
  done

  if [[ ${#failed[@]} -gt 0 ]]; then
    log_error "Build failed for: ${failed[*]}"
    exit 1
  fi

  # Export for use in deploy phase
  for svc in "${!IMAGE_URIS[@]}"; do
    local var_name
    var_name="IMAGE_URI_$(echo "$svc" | tr '[:lower:]-' '[:upper:]_')"
    export "$var_name"="${IMAGE_URIS[$svc]}"
  done
}

# ──────────────────────────────────────────────
#  Register new task definition revision
# ──────────────────────────────────────────────
update_task_definition() {
  local service="$1"
  local image_uri="$2"
  local family="pch-msa-${ENVIRONMENT}-${service}"

  log_info "Updating task definition for ${service}"

  # Get current task definition
  local current_td
  current_td=$(aws ecs describe-task-definition \
    --task-definition "$family" \
    --region "$AWS_REGION" \
    --query 'taskDefinition' \
    --output json 2>/dev/null || echo "null")

  if [[ "$current_td" == "null" ]]; then
    log_warn "No existing task definition for ${service}; skipping task-def update (deploy via CFN services stack)"
    return 0
  fi

  # Build new task definition JSON with updated image
  local new_td
  new_td=$(echo "$current_td" | jq \
    --arg img "$image_uri" \
    --arg svc "$service" \
    '.containerDefinitions[0].image = $img
     | del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
           .placementConstraints, .compatibilities, .registeredAt, .registeredBy)')

  # Register new revision
  local new_revision
  new_revision=$(aws ecs register-task-definition \
    --region "$AWS_REGION" \
    --cli-input-json "$new_td" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

  log_success "Registered: ${new_revision}"
  echo "$new_revision"
}

# ──────────────────────────────────────────────
#  Force new ECS service deployment
# ──────────────────────────────────────────────
deploy_service() {
  local service="$1"
  local task_def_arn="$2"
  local ecs_service_name="pch-msa-${ENVIRONMENT}-${service}"
  local cluster_name="pch-msa-${ENVIRONMENT}"

  log_info "Deploying ${service} → cluster ${cluster_name}"

  local args=(
    --region "$AWS_REGION"
    --cluster "$cluster_name"
    --service "$ecs_service_name"
    --force-new-deployment
  )

  if [[ -n "$task_def_arn" ]]; then
    args+=(--task-definition "$task_def_arn")
  fi

  aws ecs update-service "${args[@]}" \
    --query 'service.{Status:status,DesiredCount:desiredCount,RunningCount:runningCount}' \
    --output table

  log_success "Deployment triggered for ${service}"
}

# ──────────────────────────────────────────────
#  Wait for service stability
# ──────────────────────────────────────────────
wait_for_stability() {
  local service="$1"
  local cluster_name="pch-msa-${ENVIRONMENT}"
  local ecs_service_name="pch-msa-${ENVIRONMENT}-${service}"
  local timeout=600  # 10 minutes

  log_info "Waiting for ${service} to stabilize (timeout: ${timeout}s) ..."

  if aws ecs wait services-stable \
    --region "$AWS_REGION" \
    --cluster "$cluster_name" \
    --services "$ecs_service_name" 2>/dev/null; then
    log_success "${service} is stable"
    return 0
  else
    log_error "${service} did not stabilize within ${timeout}s"
    # Show recent events
    aws ecs describe-services \
      --region "$AWS_REGION" \
      --cluster "$cluster_name" \
      --services "$ecs_service_name" \
      --query 'services[0].events[0:5]' \
      --output table
    return 1
  fi
}

# ──────────────────────────────────────────────
#  Rollback a service
# ──────────────────────────────────────────────
rollback_service() {
  local service="$1"
  local cluster_name="pch-msa-${ENVIRONMENT}"
  local ecs_service_name="pch-msa-${ENVIRONMENT}-${service}"
  local family="pch-msa-${ENVIRONMENT}-${service}"

  log_warn "Rolling back ${service} to previous task definition revision..."

  # Get the revision before current
  local previous_revision
  previous_revision=$(aws ecs describe-task-definition \
    --task-definition "$family" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.revision' \
    --output text 2>/dev/null || echo "1")

  if [[ "$previous_revision" -le 1 ]]; then
    log_warn "No previous revision to roll back to for ${service}"
    return
  fi

  local prev_arn="${family}:$((previous_revision - 1))"

  aws ecs update-service \
    --region "$AWS_REGION" \
    --cluster "$cluster_name" \
    --service "$ecs_service_name" \
    --task-definition "$prev_arn" \
    --force-new-deployment \
    --output table > /dev/null

  log_warn "Rollback triggered for ${service} → ${prev_arn}"
}

# ──────────────────────────────────────────────
#  Show deployment status summary
# ──────────────────────────────────────────────
show_status() {
  log_step "Deployment Status Summary"
  local cluster_name="pch-msa-${ENVIRONMENT}"

  local service_names=()
  for svc in "${DEPLOY_SERVICES[@]}"; do
    service_names+=("pch-msa-${ENVIRONMENT}-${svc}")
  done

  aws ecs describe-services \
    --region "$AWS_REGION" \
    --cluster "$cluster_name" \
    --services "${service_names[@]}" \
    --query 'services[*].{Service:serviceName,Status:status,Desired:desiredCount,Running:runningCount,Pending:pendingCount}' \
    --output table 2>/dev/null || log_warn "Could not retrieve service status (services may not exist yet)"
}

# ──────────────────────────────────────────────
#  Deploy Phase (sequential in dependency order)
# ──────────────────────────────────────────────
run_deploy() {
  log_step "Deploying services in dependency order"

  local failed_services=()

  for svc in "${DEPLOY_SERVICES[@]}"; do
    log_info "─── Processing ${svc} ───"

    # Resolve image URI (from build phase or construct from ECR)
    local var_name
    var_name="IMAGE_URI_$(echo "$svc" | tr '[:lower:]-' '[:upper:]_')"
    local image_uri="${!var_name:-}"

    if [[ -z "$image_uri" ]]; then
      AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "$AWS_REGION")
      ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
      image_uri="${ECR_REGISTRY}/pch-msa/${ENVIRONMENT}/${svc}:${IMAGE_TAG}"
    fi

    # Update task definition with new image
    local task_def_arn=""
    task_def_arn=$(update_task_definition "$svc" "$image_uri") || true

    # Trigger deployment
    if ! deploy_service "$svc" "$task_def_arn"; then
      log_error "Failed to deploy ${svc}"
      failed_services+=("$svc")
      continue
    fi

    # Wait for stability (discovery and gateway block others)
    if [[ "$svc" == "pch-discovery" || "$svc" == "pch-gateway" ]]; then
      if ! wait_for_stability "$svc"; then
        log_error "${svc} failed to stabilize — rolling back"
        rollback_service "$svc"
        failed_services+=("$svc")
        log_error "Aborting further deployments due to critical service failure: ${svc}"
        break
      fi
    fi
  done

  # Wait for remaining services
  log_step "Waiting for all services to stabilize"
  for svc in "${DEPLOY_SERVICES[@]}"; do
    if [[ "$svc" != "pch-discovery" && "$svc" != "pch-gateway" ]]; then
      if ! wait_for_stability "$svc"; then
        log_warn "Rolling back ${svc}"
        rollback_service "$svc"
        failed_services+=("$svc")
      fi
    fi
  done

  if [[ ${#failed_services[@]} -gt 0 ]]; then
    log_error "Deployment failed for: ${failed_services[*]}"
    show_status
    exit 1
  fi
}

# ──────────────────────────────────────────────
#  Main
# ──────────────────────────────────────────────
main() {
  log_step "PCH MSA — ECS Deployment"
  log_info "Environment : ${ENVIRONMENT}"
  log_info "Region      : ${AWS_REGION}"
  log_info "Tag         : ${IMAGE_TAG}"
  log_info "Services    : ${DEPLOY_SERVICES[*]}"

  check_prerequisites

  if [[ "$SKIP_BUILD" == false ]]; then
    ecr_login
    run_builds
  else
    log_warn "Skipping Docker build (--skip-build)"
    # Still need account ID for image URIs
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "$AWS_REGION")
    ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  fi

  if [[ "$SKIP_DEPLOY" == false ]]; then
    run_deploy
  else
    log_warn "Skipping ECS deployment (--skip-deploy)"
  fi

  show_status
  log_success "Deployment complete!"
}

main "$@"
