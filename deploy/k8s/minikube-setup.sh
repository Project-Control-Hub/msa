#!/usr/bin/env bash
# ============================================================
#  PCH MSA — Minikube Local Setup Script
#  Starts Minikube, builds images inside Minikube, and
#  applies all Kubernetes manifests via Kustomize.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ── Configuration ─────────────────────────────────────────────────────────────
MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-8192}"   # MB
MINIKUBE_DISK="${MINIKUBE_DISK:-40g}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-docker}"
IMAGE_TAG="${IMAGE_TAG:-local}"

SERVICES=(
  pch-discovery
  pch-gateway
  pch-auth-service
  pch-project-service
  pch-issue-service
  pch-board-report-service
  pch-search-service
  pch-notification-service
  pch-file-service
  pch-integration-service
)

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# ── Prerequisites check ───────────────────────────────────────────────────────
check_prerequisites() {
  info "Checking prerequisites..."
  for cmd in minikube kubectl docker; do
    if ! command -v "$cmd" &>/dev/null; then
      error "'$cmd' is not installed or not in PATH."
    fi
  done
  success "All prerequisites found."
}

# ── Start Minikube ────────────────────────────────────────────────────────────
start_minikube() {
  if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
    warn "Minikube is already running. Skipping start."
  else
    info "Starting Minikube (driver=${MINIKUBE_DRIVER}, cpus=${MINIKUBE_CPUS}, memory=${MINIKUBE_MEMORY}MB)..."
    minikube start \
      --driver="${MINIKUBE_DRIVER}" \
      --cpus="${MINIKUBE_CPUS}" \
      --memory="${MINIKUBE_MEMORY}" \
      --disk-size="${MINIKUBE_DISK}" \
      --kubernetes-version=stable
    success "Minikube started."
  fi
}

# ── Enable addons ─────────────────────────────────────────────────────────────
enable_addons() {
  info "Enabling ingress addon..."
  minikube addons enable ingress
  minikube addons enable metrics-server
  success "Addons enabled."
}

# ── Build images inside Minikube ──────────────────────────────────────────────
build_images() {
  info "Pointing Docker CLI to Minikube's Docker daemon..."
  eval "$(minikube docker-env)"

  info "Building Docker images inside Minikube..."
  for SERVICE in "${SERVICES[@]}"; do
    info "  Building ${SERVICE}:${IMAGE_TAG} ..."
    docker build \
      --build-arg SERVICE="${SERVICE}" \
      -t "pch-msa/${SERVICE}:${IMAGE_TAG}" \
      "${PROJECT_ROOT}"
    success "  ${SERVICE} built."
  done

  # Restore local Docker env
  eval "$(minikube docker-env --unset)"
}

# ── Patch image references for local builds ───────────────────────────────────
patch_images_local() {
  info "Patching Kustomization for local image tags..."
  # Use kubectl set image after deployment, or build a kustomization overlay.
  # For simplicity we patch each deployment after apply using imagePullPolicy: Never.
  for SERVICE in "${SERVICES[@]}"; do
    kubectl set image deployment/"${SERVICE}" \
      "${SERVICE}"="pch-msa/${SERVICE}:${IMAGE_TAG}" \
      -n pch 2>/dev/null || true
    kubectl patch deployment "${SERVICE}" \
      -n pch \
      --type=json \
      -p='[{"op":"replace","path":"/spec/template/spec/containers/0/imagePullPolicy","value":"Never"}]' \
      2>/dev/null || true
  done
}

# ── Apply manifests ───────────────────────────────────────────────────────────
apply_manifests() {
  info "Applying Kubernetes manifests via Kustomize..."
  kubectl apply -k "${SCRIPT_DIR}"
  success "Manifests applied."
}

# ── Wait for infra ────────────────────────────────────────────────────────────
wait_for_infra() {
  info "Waiting for infrastructure StatefulSets to be ready (timeout 5m)..."
  kubectl rollout status statefulset/pch-mysql          -n pch --timeout=300s || warn "MySQL not ready yet."
  kubectl rollout status statefulset/pch-redis          -n pch --timeout=300s || warn "Redis not ready yet."
  kubectl rollout status statefulset/pch-kafka          -n pch --timeout=300s || warn "Kafka not ready yet."
  kubectl rollout status statefulset/pch-elasticsearch  -n pch --timeout=300s || warn "Elasticsearch not ready yet."
  success "Infrastructure check complete."
}

# ── Wait for services ─────────────────────────────────────────────────────────
wait_for_services() {
  info "Waiting for application Deployments to be ready (timeout 5m)..."
  for SERVICE in "${SERVICES[@]}"; do
    kubectl rollout status deployment/"${SERVICE}" -n pch --timeout=300s || warn "${SERVICE} not ready yet."
  done
  success "Service check complete."
}

# ── Print access info ─────────────────────────────────────────────────────────
print_access_info() {
  MINIKUBE_IP="$(minikube ip)"
  echo ""
  echo "============================================================"
  echo "  PCH MSA is running on Minikube!"
  echo "============================================================"
  echo ""
  echo "  Minikube IP : ${MINIKUBE_IP}"
  echo ""
  echo "  To access the API Gateway, add the following to /etc/hosts:"
  echo "    ${MINIKUBE_IP}  pch.example.com"
  echo ""
  echo "  Then open: http://pch.example.com"
  echo ""
  echo "  Eureka Dashboard:"
  echo "    kubectl port-forward svc/pch-discovery 8761:8761 -n pch"
  echo "    http://localhost:8761"
  echo ""
  echo "  Direct Gateway access (no host header):"
  echo "    kubectl port-forward svc/pch-gateway 8000:8000 -n pch"
  echo "    http://localhost:8000"
  echo ""
  echo "  Pod status:"
  echo "    kubectl get pods -n pch"
  echo ""
  echo "  Logs:"
  echo "    kubectl logs -f deployment/<service-name> -n pch"
  echo "============================================================"
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
  echo "============================================================"
  echo "  PCH MSA — Minikube Local Setup"
  echo "============================================================"
  echo ""

  check_prerequisites
  start_minikube
  enable_addons
  build_images
  apply_manifests
  patch_images_local
  wait_for_infra
  wait_for_services
  print_access_info
}

main "$@"
