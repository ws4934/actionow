#!/usr/bin/env bash
# =====================================================
# Actionow Full-Stack Management Script
#
# One-click entrypoint that orchestrates both the backend
# (Spring Cloud microservices) and the frontend (Next.js).
#
# Thin wrapper around docker/scripts/deploy.sh, with extra
# commands to build and run the web frontend.
# =====================================================

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$REPO_ROOT/docker"
BACKEND_DIR="$REPO_ROOT/backend"
WEB_DIR="$REPO_ROOT/web"
DEPLOY_SH="$DOCKER_DIR/scripts/deploy.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "${CYAN}[STEP]${NC} $*"; }

# Default compose mode for backend delegation
export COMPOSE_MODE="${COMPOSE_MODE:-prod}"

# Env file (shared with docker/scripts/deploy.sh resolution)
resolve_env_file() {
    case "$COMPOSE_MODE" in
        prod)
            if   [ -f "$DOCKER_DIR/.env.prod" ]; then echo "$DOCKER_DIR/.env.prod"
            elif [ -f "$DOCKER_DIR/.env" ];      then echo "$DOCKER_DIR/.env"
            else echo "$DOCKER_DIR/.env.prod"; fi ;;
        dev)
            if   [ -f "$DOCKER_DIR/.env" ];      then echo "$DOCKER_DIR/.env"
            elif [ -f "$DOCKER_DIR/.env.prod" ]; then echo "$DOCKER_DIR/.env.prod"
            else echo "$DOCKER_DIR/.env"; fi ;;
        apps) echo "$DOCKER_DIR/.env.apps" ;;
        *) echo "$DOCKER_DIR/.env" ;;
    esac
}

compose_files() {
    case "$COMPOSE_MODE" in
        prod) echo "-f $DOCKER_DIR/docker-compose.prod.yml -f $DOCKER_DIR/docker-compose.web.yml" ;;
        dev)  echo "-f $DOCKER_DIR/docker-compose.dev.yml  -f $DOCKER_DIR/docker-compose.web.yml" ;;
        apps) echo "-f $DOCKER_DIR/docker-compose.apps.yml -f $DOCKER_DIR/docker-compose.web.yml" ;;
        *)    echo "-f $DOCKER_DIR/docker-compose.prod.yml -f $DOCKER_DIR/docker-compose.web.yml" ;;
    esac
}

compose_cmd() {
    local env_file
    env_file="$(resolve_env_file)"
    # shellcheck disable=SC2046
    docker compose $(compose_files) --env-file "$env_file" "$@"
}

show_banner() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════╗"
    echo "║          Actionow Full-Stack (Backend + Web)         ║"
    echo "╚══════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

show_help() {
    echo -e "$(cat << EOF
${CYAN}Actionow Full-Stack Management${NC}

${YELLOW}Usage:${NC} $0 <command> [options]

${YELLOW}Full-stack commands:${NC}
  ${GREEN}init${NC}            Interactive backend setup wizard (delegates to docker/scripts/deploy.sh)
  ${GREEN}up${NC}              Bring up infrastructure + all backend services + web (profile: full)
  ${GREEN}down${NC}            Stop everything
  ${GREEN}restart${NC}         Restart everything
  ${GREEN}status${NC}          Show container status
  ${GREEN}logs${NC} [svc] [-f] Tail logs
  ${GREEN}health${NC}          Backend health check (delegates)
  ${GREEN}clean${NC}           Remove all containers + volumes (DESTRUCTIVE)

${YELLOW}Backend-only shortcuts:${NC}
  ${GREEN}backend${NC} <args>  Pass-through to docker/scripts/deploy.sh (e.g. "backend rebuild agent")
  ${GREEN}infra${NC}           Start infrastructure only (PostgreSQL, Redis, RabbitMQ, MinIO)

${YELLOW}Frontend-only commands:${NC}
  ${GREEN}web build${NC}       Build the Docker image for the web frontend
  ${GREEN}web up${NC}          Start the web frontend container
  ${GREEN}web down${NC}        Stop the web frontend container
  ${GREEN}web logs${NC} [-f]   Tail web logs
  ${GREEN}web local-dev${NC}   Install deps & run 'npm run dev' on host (no Docker)

${YELLOW}Environment:${NC}
  Env file:      $(resolve_env_file)
  Compose mode:  $COMPOSE_MODE    (override with COMPOSE_MODE=dev|prod|apps)

${YELLOW}Typical quick start:${NC}
  $0 init             # edit docker/.env.prod interactively
  $0 up               # build + start backend + web

${YELLOW}Dev loop (backend in IDE, web & infra in Docker):${NC}
  COMPOSE_MODE=dev $0 infra
  $0 web local-dev

EOF
)"
}

ensure_env_file() {
    local env_file
    env_file="$(resolve_env_file)"
    if [ ! -f "$env_file" ]; then
        if [ -f "$DOCKER_DIR/.env.example" ]; then
            log_warn "Env file not found: $env_file"
            log_info  "Creating from template. Review and customize secrets before a real deployment."
            cp "$DOCKER_DIR/.env.example" "$env_file"
        else
            log_error "No env file and no .env.example to copy from."
            exit 1
        fi
    fi
}

cmd_up() {
    ensure_env_file
    log_step "Bringing up backend (infra + services) + web …"
    # 默认并行构建（13 个 Maven + Next 同时跑）会把内存打爆导致 Next 被 SIGKILL。
    # 通过 COMPOSE_PARALLEL_LIMIT 限制同时构建数；先 build 再 up -d 避免 build 和服务启动抢内存。
    # 跳过构建：COMPOSE_SKIP_BUILD=1 ./actionow.sh up
    if [ "${COMPOSE_SKIP_BUILD:-0}" != "1" ]; then
        log_step "[1/2] Building web image first (Next build is memory-heavy) …"
        # 必须带 full 或 services profile，否则 web.depends_on:gateway 校验会失败
        COMPOSE_PARALLEL_LIMIT=1 \
            compose_cmd --profile full --profile services build web
        log_step "[2/2] Building backend images …"
        COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}" \
            compose_cmd --profile services --profile full build
    fi
    compose_cmd --profile infra --profile services --profile full --profile web up -d
    log_success "Stack is up. Run '$0 status' to inspect."
}

cmd_down() {
    log_step "Stopping stack …"
    compose_cmd --profile infra --profile services --profile full down
    log_success "Stack stopped."
}

cmd_status() {
    compose_cmd ps
}

cmd_logs() {
    compose_cmd logs "$@"
}

cmd_web_build() {
    log_step "Building web image …"
    ensure_env_file
    compose_cmd build web
    log_success "Web image built."
}

cmd_web_up() {
    ensure_env_file
    log_step "Starting web container …"
    compose_cmd --profile web up -d --build web
    log_success "Web is up on port ${WEB_PORT:-3000}."
}

cmd_web_down() {
    log_step "Stopping web container …"
    compose_cmd stop web || true
    compose_cmd rm -f web || true
}

cmd_web_logs() {
    compose_cmd logs "$@" web
}

cmd_web_local_dev() {
    log_step "Starting web dev server on host (no Docker)"
    if [ ! -f "$WEB_DIR/.env.local" ]; then
        if [ -f "$WEB_DIR/.env.example" ]; then
            log_info "Creating web/.env.local from web/.env.example"
            cp "$WEB_DIR/.env.example" "$WEB_DIR/.env.local"
        fi
    fi
    cd "$WEB_DIR"
    if [ ! -d node_modules ]; then
        log_info "Installing npm dependencies …"
        npm install
    fi
    npm run dev
}

main() {
    show_banner
    if [ $# -eq 0 ]; then show_help; exit 0; fi

    local cmd="$1"; shift || true
    case "$cmd" in
        -h|--help|help) show_help ;;
        init)     bash "$DEPLOY_SH" init ;;
        up)       cmd_up ;;
        down)     cmd_down ;;
        restart)  cmd_down; cmd_up ;;
        status|ps) cmd_status ;;
        logs)     cmd_logs "$@" ;;
        health)   bash "$DEPLOY_SH" health ;;
        clean)    bash "$DEPLOY_SH" clean ;;
        infra)    bash "$DEPLOY_SH" infra ;;
        backend)  bash "$DEPLOY_SH" "$@" ;;
        web)
            local sub="${1:-}"; shift || true
            case "$sub" in
                build)     cmd_web_build ;;
                up)        cmd_web_up ;;
                down)      cmd_web_down ;;
                logs)      cmd_web_logs "$@" ;;
                local-dev) cmd_web_local_dev ;;
                ""|-h|--help) show_help ;;
                *) log_error "Unknown web subcommand: $sub"; show_help; exit 1 ;;
            esac
            ;;
        *) log_error "Unknown command: $cmd"; show_help; exit 1 ;;
    esac
}

main "$@"
