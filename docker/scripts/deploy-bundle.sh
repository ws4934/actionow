#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DOCKER_DIR")"
PROJECT_ROOT="$REPO_ROOT/backend"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.bundle.yml"
ENV_TEMPLATE="$DOCKER_DIR/.env.example"
ENV_FILE="${ACTIONOW_ENV_FILE:-$DOCKER_DIR/.env.bundle}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

# Deployment profile:
#   full = start infra (PG/Redis/MQ/MinIO) + app services (default)
#   apps = app services only, connect to external infra
# Switch with: DEPLOY_PROFILE=apps ./deploy-bundle.sh up
DEPLOY_PROFILE="${DEPLOY_PROFILE:-full}"

SERVICES=(
    gateway
    user
    workspace
    wallet
    billing
    project
    ai
    task
    collab
    system
    canvas
    agent
)

log_info() {
    echo "[INFO] $1"
}

log_warn() {
    echo "[WARN] $1"
}

log_error() {
    echo "[ERROR] $1" >&2
}

show_help() {
    cat <<EOF
Usage:
  $0 up [bundle.tar|bundle.tar.gz]
  $0 load <bundle.tar|bundle.tar.gz>
  $0 down
  $0 status
  $0 logs [service] [compose-log-args...]

Environment:
  ACTIONOW_ENV_FILE   Override env file path (default: docker/.env.bundle)
  IMAGE_TAG          Image tag to use (default: latest)
  DEPLOY_PROFILE     Deployment profile: full (default) or apps (external infra)

Examples:
  # Full stack (infra + apps)
  $0 up /opt/actionow/actionow-images-20260329.tar

  # Apps only (customer already has DB/Redis/MQ running)
  DEPLOY_PROFILE=apps $0 up actionow-images-20260402.tar.gz

  $0 status
  $0 logs gateway -f
EOF
}

check_prerequisites() {
    command -v docker >/dev/null 2>&1 || {
        log_error "docker not found"
        exit 1
    }

    docker compose version >/dev/null 2>&1 || {
        log_error "docker compose v2 not available"
        exit 1
    }

    docker info >/dev/null 2>&1 || {
        log_error "docker daemon is not running"
        exit 1
    }
}

ensure_env_file() {
    if [ -f "$ENV_FILE" ]; then
        return
    fi

    if [ ! -f "$ENV_TEMPLATE" ]; then
        log_error "env template not found: $ENV_TEMPLATE"
        exit 1
    fi

    cp "$ENV_TEMPLATE" "$ENV_FILE"
    log_warn "Created $ENV_FILE from template. Defaults are for quick start only; change secrets before production use."
}

resolve_bundle() {
    local candidate="${1:-}"

    if [ -n "$candidate" ]; then
        if [ ! -f "$candidate" ]; then
            log_error "bundle file not found: $candidate"
            exit 1
        fi
        printf '%s\n' "$candidate"
        return
    fi

    local search_dirs=("$PWD" "$PROJECT_ROOT")
    local matches=()
    local dir
    local pattern
    shopt -s nullglob
    for dir in "${search_dirs[@]}"; do
        for pattern in "$dir"/actionow-images-*.tar "$dir"/actionow-images-*.tar.gz "$dir"/actionow-images-*.tgz; do
            [ -f "$pattern" ] && matches+=("$pattern")
        done
    done
    shopt -u nullglob

    if [ "${#matches[@]}" -eq 0 ]; then
        log_error "no image bundle found; pass a tar file explicitly"
        exit 1
    fi

    printf '%s\n' "${matches[@]}" | sort | tail -n 1
}

load_bundle() {
    local bundle
    bundle="$(resolve_bundle "${1:-}")"
    local temp_dir=

    log_info "Loading image bundle: $bundle"

    case "$bundle" in
        *.tar)
            docker load -i "$bundle"
            ;;
        *.tar.gz|*.tgz)
            temp_dir="$(mktemp -d)"
            tar -xzf "$bundle" -C "$temp_dir"

            shopt -s nullglob
            local tar_files=("$temp_dir"/*.tar)
            shopt -u nullglob

            if [ "${#tar_files[@]}" -eq 0 ]; then
                log_error "no .tar images found in archive: $bundle"
                rm -rf "$temp_dir"
                exit 1
            fi

            local tar_file
            for tar_file in "${tar_files[@]}"; do
                docker load -i "$tar_file"
            done
            rm -rf "$temp_dir"
            ;;
        *)
            log_error "unsupported bundle type: $bundle"
            exit 1
            ;;
    esac
}

normalize_image_tags() {
    local missing=()
    local service

    for service in "${SERVICES[@]}"; do
        local bundle_image="actionow-${service}:${IMAGE_TAG}"
        local slash_image="actionow/${service}:${IMAGE_TAG}"

        if docker image inspect "$bundle_image" >/dev/null 2>&1; then
            continue
        fi

        if docker image inspect "$slash_image" >/dev/null 2>&1; then
            log_info "Tagging $slash_image as $bundle_image"
            docker tag "$slash_image" "$bundle_image"
            continue
        fi

        missing+=("$bundle_image")
    done

    if [ "${#missing[@]}" -gt 0 ]; then
        log_error "missing required images:"
        printf '  %s\n' "${missing[@]}" >&2
        exit 1
    fi
}

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile "$DEPLOY_PROFILE" "$@"
}

up() {
    local bundle_arg="${1:-}"

    ensure_env_file

    if [ -n "$bundle_arg" ]; then
        load_bundle "$bundle_arg"
    fi

    normalize_image_tags

    log_info "Starting Actionow stack"
    compose up -d
    compose ps
}

down() {
    ensure_env_file
    compose down
}

status() {
    ensure_env_file
    compose ps
}

logs() {
    ensure_env_file
    if [ "$#" -eq 0 ]; then
        compose logs
        return
    fi

    local service="$1"
    shift
    compose logs "$@" "$service"
}

main() {
    local command="${1:-up}"

    if [ "$#" -gt 0 ]; then
        shift
    fi

    check_prerequisites

    case "$command" in
        up)
            up "${1:-}"
            ;;
        load)
            if [ "$#" -lt 1 ]; then
                log_error "load requires a bundle file"
                exit 1
            fi
            load_bundle "$1"
            normalize_image_tags
            ;;
        down)
            down
            ;;
        status|ps)
            status
            ;;
        logs)
            logs "$@"
            ;;
        help|-h|--help)
            show_help
            ;;
        *)
            if [ -f "$command" ]; then
                up "$command"
                return
            fi
            log_error "unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
