#!/bin/bash
# =====================================================
# Actionow Shared Library
# Common utilities sourced by all management scripts.
# Do NOT execute directly — source from other scripts.
# =====================================================

# Guard against double-sourcing
[ -n "$_ACTIONOW_LIB_LOADED" ] && return 0
_ACTIONOW_LIB_LOADED=1

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Paths — resolved from this file's location (docker/scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DOCKER_DIR")"
PROJECT_ROOT="$REPO_ROOT/backend"
WEB_ROOT="$REPO_ROOT/web"

# =====================================================
# Compose Mode & Env File Detection
# =====================================================
# Modes:
#   dev  = local JAR + volume mount + dockerized infra
#   prod = Docker build + dockerized infra (default)
#   apps = Docker build + external infra only
# Switch with: COMPOSE_MODE=apps ./deploy.sh up

COMPOSE_MODE="${COMPOSE_MODE:-prod}"
case "$COMPOSE_MODE" in
    dev)
        COMPOSE_FILE="$DOCKER_DIR/docker-compose.dev.yml"
        if [ -f "$DOCKER_DIR/.env" ]; then
            ENV_FILE="$DOCKER_DIR/.env"
        elif [ -f "$DOCKER_DIR/.env.prod" ]; then
            ENV_FILE="$DOCKER_DIR/.env.prod"
        else
            ENV_FILE="$DOCKER_DIR/.env"
        fi
        ENV_TEMPLATE="$DOCKER_DIR/.env.example"
        ;;
    prod)
        COMPOSE_FILE="$DOCKER_DIR/docker-compose.prod.yml"
        if [ -f "$DOCKER_DIR/.env.prod" ]; then
            ENV_FILE="$DOCKER_DIR/.env.prod"
        elif [ -f "$DOCKER_DIR/.env" ]; then
            ENV_FILE="$DOCKER_DIR/.env"
        else
            ENV_FILE="$DOCKER_DIR/.env.prod"
        fi
        ENV_TEMPLATE="$DOCKER_DIR/.env.example"
        ;;
    apps)
        COMPOSE_FILE="$DOCKER_DIR/docker-compose.apps.yml"
        ENV_FILE="$DOCKER_DIR/.env.apps"
        ENV_TEMPLATE="$DOCKER_DIR/.env.example"
        ;;
    *)
        echo "[ERROR] Unsupported COMPOSE_MODE: $COMPOSE_MODE"
        echo "Supported modes: dev, prod, apps"
        exit 1
        ;;
esac

# Service lists
ALL_SERVICES="gateway user workspace wallet billing project task ai collab canvas system agent"
INFRA_SERVICES="postgres redis rabbitmq minio"

# =====================================================
# Logging
# =====================================================

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()    { echo -e "${CYAN}[STEP]${NC} $1"; }

print_banner() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════╗"
    echo "║           Actionow Platform Management                ║"
    echo "║              Version 1.0.0                           ║"
    echo "╚══════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# =====================================================
# Docker / Compose Helpers
# =====================================================

check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    if ! docker compose version &> /dev/null; then
        log_error "Docker Compose V2 is not available"
        exit 1
    fi
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        exit 1
    fi
}

check_env() {
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$ENV_TEMPLATE" ]; then
            log_warn "Env file not found. Creating from template: $ENV_FILE"
            cp "$ENV_TEMPLATE" "$ENV_FILE"
            log_success "Created $ENV_FILE — edit it to customize settings."
        else
            log_error "No env file found. Run '$0 init' to configure."
            exit 1
        fi
    fi
}

# Read a single variable from the env file without sourcing (safe for JVM_OPTS etc.)
_env_get() {
    local key="$1"
    local default="$2"
    local val
    val=$(grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- | sed 's/^["'\'']\(.*\)["'\'']$/\1/')
    echo "${val:-$default}"
}

# Docker Compose wrapper
# In apps mode, always activate --profile all so every service is "visible"
# to commands like ps, logs, stop.
compose() {
    if [ "$COMPOSE_MODE" = "apps" ]; then
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile all "$@"
    else
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
    fi
}

compose_all_profiles() {
    case "$COMPOSE_MODE" in
        apps)
            compose "$@"
            ;;
        *)
            compose --profile infra --profile apps "$@"
            ;;
    esac
}

mode_supports_docker_infra() {
    [ "$COMPOSE_MODE" != "apps" ]
}
