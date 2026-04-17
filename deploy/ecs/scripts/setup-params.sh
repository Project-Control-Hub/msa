#!/usr/bin/env bash
# =============================================================================
#  PCH MSA — SSM Parameter Store Setup Script
#
#  Creates all required SSM parameters for the PCH MSA ECS deployment.
#  Parameters are stored under: /pch-msa/{environment}/...
#
#  Usage:
#    ./setup-params.sh [OPTIONS]
#
#  Options:
#    -e, --env       Environment (dev|staging|prod)   [default: dev]
#    -r, --region    AWS region                        [default: ap-northeast-2]
#        --overwrite Overwrite existing parameters     [default: false]
#        --dry-run   Print parameters without creating them
#    -h, --help      Show this help message
#
#  IMPORTANT:
#    Before running, set the following environment variables or edit the
#    DEFAULT VALUES section below:
#
#      PCH_DB_PASSWORD         - RDS master password
#      PCH_JWT_SECRET          - JWT signing secret (min 32 chars)
#      PCH_OPENSEARCH_PASSWORD - OpenSearch admin password
#
#    After managed-infra.yml is deployed, run this script again with the
#    --overwrite flag to update endpoints from CloudFormation outputs.
# =============================================================================
set -euo pipefail

# ──────────────────────────────────────────────
#  Defaults
# ──────────────────────────────────────────────
ENVIRONMENT="dev"
AWS_REGION="ap-northeast-2"
OVERWRITE=false
DRY_RUN=false

# ──────────────────────────────────────────────
#  Colours
# ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()    { echo -e "\n${CYAN}══════════════════════════════════════════${NC}"; echo -e "${CYAN}  $*${NC}"; echo -e "${CYAN}══════════════════════════════════════════${NC}"; }

# ──────────────────────────────────────────────
#  Parse arguments
# ──────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    -e|--env)       ENVIRONMENT="$2"; shift 2 ;;
    -r|--region)    AWS_REGION="$2"; shift 2 ;;
    --overwrite)    OVERWRITE=true; shift ;;
    --dry-run)      DRY_RUN=true; shift ;;
    -h|--help)
      grep '^#' "$0" | grep -v '#!/' | sed 's/^#[[:space:]]*//'
      exit 0 ;;
    *) log_error "Unknown option: $1"; exit 1 ;;
  esac
done

# ──────────────────────────────────────────────
#  Validate environment
# ──────────────────────────────────────────────
case "$ENVIRONMENT" in
  dev|staging|prod) ;;
  *) log_error "Invalid environment: $ENVIRONMENT. Must be dev|staging|prod"; exit 1 ;;
esac

# ──────────────────────────────────────────────
#  SSM parameter helper
# ──────────────────────────────────────────────
SSM_BASE="/pch-msa/${ENVIRONMENT}"
PARAM_COUNT=0
PARAM_SKIPPED=0

put_param() {
  local name="$1"
  local value="$2"
  local type="${3:-SecureString}"       # String | SecureString | StringList
  local description="${4:-PCH MSA ${ENVIRONMENT} parameter}"
  local full_name="${SSM_BASE}/${name}"

  if [[ "$DRY_RUN" == true ]]; then
    local masked_value="$value"
    if [[ "$type" == "SecureString" ]]; then
      masked_value="***MASKED***"
    fi
    echo "  [DRY-RUN] PUT  ${full_name}  (${type})  = ${masked_value}"
    return
  fi

  local extra_args=()
  if [[ "$OVERWRITE" == true ]]; then
    extra_args+=(--overwrite)
  fi

  if aws ssm put-parameter \
    --region "$AWS_REGION" \
    --name "$full_name" \
    --value "$value" \
    --type "$type" \
    --description "$description" \
    "${extra_args[@]}" \
    2>/dev/null; then
    log_success "PUT  ${full_name}"
    PARAM_COUNT=$((PARAM_COUNT + 1))
  else
    log_warn "SKIP ${full_name} (already exists; use --overwrite to force)"
    PARAM_SKIPPED=$((PARAM_SKIPPED + 1))
  fi
}

# ──────────────────────────────────────────────
#  Resolve CloudFormation outputs (if stacks exist)
# ──────────────────────────────────────────────
get_cfn_output() {
  local stack_name="$1"
  local output_key="$2"
  aws cloudformation describe-stacks \
    --region "$AWS_REGION" \
    --stack-name "$stack_name" \
    --query "Stacks[0].Outputs[?OutputKey=='${output_key}'].OutputValue" \
    --output text 2>/dev/null || echo ""
}

resolve_infrastructure_values() {
  local infra_stack="pch-msa-${ENVIRONMENT}-infra"
  log_info "Resolving values from CloudFormation stack: ${infra_stack}"

  RDS_ENDPOINT=$(get_cfn_output "$infra_stack" "RdsEndpoint")
  REDIS_ENDPOINT=$(get_cfn_output "$infra_stack" "RedisEndpoint")
  REDIS_PORT=$(get_cfn_output "$infra_stack" "RedisPort")
  OPENSEARCH_ENDPOINT=$(get_cfn_output "$infra_stack" "OpenSearchEndpoint")
  S3_BUCKET_NAME=$(get_cfn_output "$infra_stack" "FileServiceBucketName")

  # MSK bootstrap servers (retrieved separately via MSK API)
  local msk_cluster_arn
  msk_cluster_arn=$(get_cfn_output "$infra_stack" "MskClusterArn")
  if [[ -n "$msk_cluster_arn" ]]; then
    MSK_BOOTSTRAP_SERVERS=$(aws kafka get-bootstrap-brokers \
      --cluster-arn "$msk_cluster_arn" \
      --region "$AWS_REGION" \
      --query 'BootstrapBrokerStringTls' \
      --output text 2>/dev/null || echo "")
  fi
}

# ──────────────────────────────────────────────
#  Configuration — Edit these before running!
# ──────────────────────────────────────────────
load_config() {
  # ── Secrets from environment variables (preferred) ──
  DB_MASTER_PASSWORD="${PCH_DB_PASSWORD:-CHANGE_ME_$(openssl rand -hex 12)}"
  JWT_SECRET="${PCH_JWT_SECRET:-CHANGE_ME_$(openssl rand -hex 32)}"
  OPENSEARCH_PASSWORD="${PCH_OPENSEARCH_PASSWORD:-CHANGE_ME_$(openssl rand -hex 16)}"

  # ── Infrastructure endpoints ──
  # These are auto-resolved from CloudFormation outputs if the stack exists.
  # Override by setting environment variables before running.
  RDS_ENDPOINT="${PCH_RDS_ENDPOINT:-pch-msa-${ENVIRONMENT}-mysql.rds.${AWS_REGION}.amazonaws.com}"
  RDS_PORT="${PCH_RDS_PORT:-3306}"
  RDS_MASTER_USERNAME="${PCH_RDS_MASTER_USERNAME:-pch_admin}"

  REDIS_ENDPOINT="${PCH_REDIS_ENDPOINT:-pch-msa-${ENVIRONMENT}-redis.cache.amazonaws.com}"
  REDIS_PORT="${PCH_REDIS_PORT:-6379}"

  MSK_BOOTSTRAP_SERVERS="${PCH_MSK_BOOTSTRAP_SERVERS:-b-1.pch-msa-${ENVIRONMENT}-kafka.xxx.kafka.${AWS_REGION}.amazonaws.com:9094}"

  OPENSEARCH_ENDPOINT="${PCH_OPENSEARCH_ENDPOINT:-https://vpc-pch-msa-${ENVIRONMENT}.${AWS_REGION}.es.amazonaws.com}"
  OPENSEARCH_USERNAME="${PCH_OPENSEARCH_USERNAME:-pch_os_admin}"

  S3_BUCKET_NAME="${PCH_S3_BUCKET:-pch-msa-${ENVIRONMENT}-files-ACCOUNT_ID}"

  # ── JWT ──
  JWT_EXPIRATION="${PCH_JWT_EXPIRATION:-3600000}"  # 1 hour in ms
  JWT_REFRESH_EXPIRATION="${PCH_JWT_REFRESH_EXPIRATION:-604800000}"  # 7 days in ms

  # ── Per-database passwords (default = same as master) ──
  DB_AUTH_PASSWORD="${PCH_DB_AUTH_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_PROJECT_PASSWORD="${PCH_DB_PROJECT_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_ISSUE_PASSWORD="${PCH_DB_ISSUE_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_BOARD_REPORT_PASSWORD="${PCH_DB_BOARD_REPORT_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_NOTIFICATION_PASSWORD="${PCH_DB_NOTIFICATION_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_FILE_PASSWORD="${PCH_DB_FILE_PASSWORD:-${DB_MASTER_PASSWORD}}"
  DB_INTEGRATION_PASSWORD="${PCH_DB_INTEGRATION_PASSWORD:-${DB_MASTER_PASSWORD}}"
}

# ──────────────────────────────────────────────
#  Create parameters
# ──────────────────────────────────────────────
create_redis_params() {
  log_step "Redis / ElastiCache"
  put_param "redis/host"          "$REDIS_ENDPOINT"  "String"       "ElastiCache Redis primary endpoint"
  put_param "redis/port"          "$REDIS_PORT"      "String"       "ElastiCache Redis port"
}

create_kafka_params() {
  log_step "Kafka / MSK"
  put_param "kafka/bootstrap-servers"  "$MSK_BOOTSTRAP_SERVERS"  "String"  "MSK Kafka bootstrap servers (TLS)"
}

create_opensearch_params() {
  log_step "OpenSearch"
  put_param "opensearch/endpoint"   "${OPENSEARCH_ENDPOINT}"   "String"       "OpenSearch domain endpoint"
  put_param "opensearch/username"   "${OPENSEARCH_USERNAME}"   "String"       "OpenSearch admin username"
  put_param "opensearch/password"   "${OPENSEARCH_PASSWORD}"   "SecureString" "OpenSearch admin password"
}

create_s3_params() {
  log_step "S3"
  put_param "s3/bucket-name"  "$S3_BUCKET_NAME"  "String"  "S3 bucket name for file-service"
}

create_jwt_params() {
  log_step "JWT"
  put_param "jwt/secret"              "$JWT_SECRET"              "SecureString"  "JWT signing secret"
  put_param "jwt/expiration"          "$JWT_EXPIRATION"          "String"        "JWT access token expiration (ms)"
  put_param "jwt/refresh-expiration"  "$JWT_REFRESH_EXPIRATION"  "String"        "JWT refresh token expiration (ms)"
}

create_db_params() {
  log_step "Database — pch_auth"
  put_param "db/pch_auth/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_auth?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_auth DB JDBC URL"
  put_param "db/pch_auth/username"  "$RDS_MASTER_USERNAME"   "String"        "pch_auth DB username"
  put_param "db/pch_auth/password"  "$DB_AUTH_PASSWORD"      "SecureString"  "pch_auth DB password"

  log_step "Database — pch_project"
  put_param "db/pch_project/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_project?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_project DB JDBC URL"
  put_param "db/pch_project/username"  "$RDS_MASTER_USERNAME"    "String"        "pch_project DB username"
  put_param "db/pch_project/password"  "$DB_PROJECT_PASSWORD"    "SecureString"  "pch_project DB password"

  log_step "Database — pch_issue"
  put_param "db/pch_issue/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_issue?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_issue DB JDBC URL"
  put_param "db/pch_issue/username"  "$RDS_MASTER_USERNAME"   "String"        "pch_issue DB username"
  put_param "db/pch_issue/password"  "$DB_ISSUE_PASSWORD"     "SecureString"  "pch_issue DB password"

  log_step "Database — pch_board_report"
  put_param "db/pch_board_report/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_board_report?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_board_report DB JDBC URL"
  put_param "db/pch_board_report/username"  "$RDS_MASTER_USERNAME"       "String"        "pch_board_report DB username"
  put_param "db/pch_board_report/password"  "$DB_BOARD_REPORT_PASSWORD"  "SecureString"  "pch_board_report DB password"

  log_step "Database — pch_notification"
  put_param "db/pch_notification/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_notification?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_notification DB JDBC URL"
  put_param "db/pch_notification/username"  "$RDS_MASTER_USERNAME"       "String"        "pch_notification DB username"
  put_param "db/pch_notification/password"  "$DB_NOTIFICATION_PASSWORD"  "SecureString"  "pch_notification DB password"

  log_step "Database — pch_integration"
  put_param "db/pch_integration/url"       "jdbc:mysql://${RDS_ENDPOINT}:${RDS_PORT}/pch_integration?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"  "String"        "pch_integration DB JDBC URL"
  put_param "db/pch_integration/username"  "$RDS_MASTER_USERNAME"      "String"        "pch_integration DB username"
  put_param "db/pch_integration/password"  "$DB_INTEGRATION_PASSWORD"  "SecureString"  "pch_integration DB password"
}

# ──────────────────────────────────────────────
#  Print summary of all parameters
# ──────────────────────────────────────────────
list_params() {
  log_step "Existing SSM Parameters under ${SSM_BASE}"
  aws ssm describe-parameters \
    --region "$AWS_REGION" \
    --parameter-filters "Key=Name,Option=BeginsWith,Values=${SSM_BASE}" \
    --query 'Parameters[*].{Name:Name,Type:Type,LastModified:LastModifiedDate}' \
    --output table 2>/dev/null || log_warn "No parameters found (or insufficient permissions)"
}

# ──────────────────────────────────────────────
#  Main
# ──────────────────────────────────────────────
main() {
  log_step "PCH MSA — SSM Parameter Store Setup"
  log_info "Environment : ${ENVIRONMENT}"
  log_info "Region      : ${AWS_REGION}"
  log_info "Overwrite   : ${OVERWRITE}"
  log_info "Dry Run     : ${DRY_RUN}"

  # Check AWS credentials
  if ! aws sts get-caller-identity --region "$AWS_REGION" &>/dev/null; then
    log_error "AWS credentials not configured. Run 'aws configure' or set env vars."
    exit 1
  fi

  load_config

  # Try to resolve actual endpoints from CFN outputs
  resolve_infrastructure_values 2>/dev/null || true

  # Warn if placeholder values are still set
  if [[ "$DB_MASTER_PASSWORD" == CHANGE_ME* ]] && [[ "$DRY_RUN" == false ]]; then
    log_warn "Using auto-generated DB password. Set PCH_DB_PASSWORD to use a specific value."
  fi
  if [[ "$JWT_SECRET" == CHANGE_ME* ]] && [[ "$DRY_RUN" == false ]]; then
    log_warn "Using auto-generated JWT secret. Set PCH_JWT_SECRET to use a specific value."
  fi

  # Create all parameters
  create_redis_params
  create_kafka_params
  create_opensearch_params
  create_s3_params
  create_jwt_params
  create_db_params

  echo ""
  if [[ "$DRY_RUN" == false ]]; then
    log_success "Done! Created: ${PARAM_COUNT} | Skipped: ${PARAM_SKIPPED}"
    list_params
  else
    log_success "Dry-run complete. No parameters were created."
  fi

  echo ""
  log_info "Next steps:"
  log_info "  1. Deploy managed-infra.yml CloudFormation stack"
  log_info "  2. Re-run this script with --overwrite to update endpoints"
  log_info "  3. Run deploy.sh to push images and deploy ECS services"
}

main "$@"
