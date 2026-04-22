#!/bin/bash
# =====================================================
# Actionow Service Update Script
# Updates services from GitHub and redeploys
# =====================================================

set -e

# Source shared library (provides colors, paths, env detection, compose wrappers, logging)
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

show_help() {
    echo "Usage: $0 [options] [services...]"
    echo
    echo "Modes:"
    echo "  COMPOSE_MODE=prod   Dockerized infra + app services"
    echo "  COMPOSE_MODE=apps   App containers only, external infra"
    echo
    echo "Options:"
    echo "  -a, --all       Update all services"
    echo "  -p, --pull      Pull latest code before update"
    echo "  -b, --branch    Specify branch to pull (default: main)"
    echo "  -n, --no-cache  Build without cache"
    echo "  -h, --help      Show this help"
    echo
    echo "Examples:"
    echo "  $0 --all                    # Update all services"
    echo "  $0 gateway user             # Update specific services"
    echo "  $0 --pull --all             # Pull code and update all"
    echo "  $0 --pull -b develop --all  # Pull develop branch and update"
}

# Parse arguments
PULL_CODE=false
BRANCH="main"
NO_CACHE=""
UPDATE_ALL=false
SERVICES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -a|--all)
            UPDATE_ALL=true
            shift
            ;;
        -p|--pull)
            PULL_CODE=true
            shift
            ;;
        -b|--branch)
            BRANCH="$2"
            shift 2
            ;;
        -n|--no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            SERVICES+=("$1")
            shift
            ;;
    esac
done

# Check environment
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    log_error "Run './deploy.sh init' first"
    exit 1
fi

# Pull latest code
if [ "$PULL_CODE" = true ]; then
    log_info "Pulling latest code from branch: $BRANCH"
    cd "$PROJECT_ROOT"

    if [ -n "$(git status --porcelain)" ]; then
        log_warn "Uncommitted changes detected. Stashing..."
        git stash
    fi

    git fetch origin
    git checkout "$BRANCH"
    git pull origin "$BRANCH"

    log_info "Code updated"
fi

# Determine services to update
if [ "$UPDATE_ALL" = true ]; then
    SERVICES=()
    SERVICE_ARGS=""
else
    if [ ${#SERVICES[@]} -eq 0 ]; then
        log_error "No services specified. Use --all or specify service names."
        show_help
        exit 1
    fi
    SERVICE_ARGS="${SERVICES[*]}"
fi

# Build and restart
cd "$DOCKER_DIR"

log_info "Building services: ${SERVICE_ARGS:-all}..."
compose --profile apps build $NO_CACHE $SERVICE_ARGS

log_info "Restarting services..."
compose --profile apps up -d $SERVICE_ARGS

log_info "Update completed"

# Show status
echo
log_info "Current service status:"
compose --profile apps ps
