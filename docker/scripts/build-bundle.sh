#!/bin/bash
# =====================================================
# Actionow Image Bundle Builder
#
# Builds all service Docker images and packs them into
# a single tar.gz archive for offline deployment.
#
# The output bundle can be deployed on the customer's
# machine using deploy-bundle.sh.
#
# Usage:
#   ./build-bundle.sh                    # Build all services
#   ./build-bundle.sh gateway user ai    # Build specific services only
#   ./build-bundle.sh --no-cache         # Force rebuild without cache
#   ./build-bundle.sh -o /tmp/bundle.tar.gz  # Custom output path
#
# Environment:
#   IMAGE_TAG       Tag for built images (default: latest)
#   SKIP_BUILD      Set to 1 to skip Maven/Docker build, just pack existing images
# =====================================================

set -euo pipefail

# Source shared library (provides colors, logging, paths)
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

IMAGE_TAG="${IMAGE_TAG:-latest}"
SKIP_BUILD="${SKIP_BUILD:-0}"

BUNDLE_SERVICES=(
    gateway user workspace wallet billing
    project ai task collab system canvas agent
)

show_help() {
    cat <<EOF
${CYAN}Actionow Image Bundle Builder${NC}

${YELLOW}Usage:${NC} $0 [options] [services...]

${YELLOW}Options:${NC}
  -o, --output PATH   Output file path (default: actionow-images-YYYYMMDD.tar.gz)
  -t, --tag TAG        Image tag (default: latest)
  --no-cache           Build Docker images without cache
  --skip-build         Skip build, only pack existing images
  --with-compose       Include compose files and deploy script in the bundle
  -h, --help           Show this help

${YELLOW}Examples:${NC}
  $0                           # Build all, output to actionow-images-YYYYMMDD.tar.gz
  $0 gateway user ai           # Build and pack only these services
  $0 --skip-build              # Pack already-built images (no rebuild)
  $0 -o /tmp/release.tar.gz   # Custom output path
  $0 --with-compose            # Include deployment files in bundle

${YELLOW}Customer deployment:${NC}
  scp actionow-images-*.tar.gz user@customer:/opt/actionow/
  ssh user@customer 'cd /opt/actionow && ./deploy-bundle.sh up actionow-images-*.tar.gz'
EOF
}

# Parse arguments
OUTPUT=""
NO_CACHE=""
WITH_COMPOSE=false
SERVICES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--output)
            OUTPUT="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        --skip-build)
            SKIP_BUILD=1
            shift
            ;;
        --with-compose)
            WITH_COMPOSE=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        -*)
            log_error "Unknown option: $1"
            show_help
            exit 1
            ;;
        *)
            SERVICES+=("$1")
            shift
            ;;
    esac
done

# Default to all services if none specified
if [ ${#SERVICES[@]} -eq 0 ]; then
    SERVICES=("${BUNDLE_SERVICES[@]}")
fi

# Default output filename
if [ -z "$OUTPUT" ]; then
    OUTPUT="${PROJECT_ROOT}/actionow-images-$(date +%Y%m%d).tar.gz"
fi

# Validate Docker
check_docker

print_banner

log_info "Services: ${SERVICES[*]}"
log_info "Image tag: $IMAGE_TAG"
log_info "Output: $OUTPUT"
echo

# =====================================================
# Step 1: Build Docker images
# =====================================================

if [ "$SKIP_BUILD" = "1" ]; then
    log_warn "Skipping build (--skip-build / SKIP_BUILD=1)"
else
    log_step "Building Docker images..."

    # Find the env file for building
    BUILD_ENV_FILE=""
    for candidate in "$DOCKER_DIR/.env.prod" "$DOCKER_DIR/.env" "$DOCKER_DIR/.env.example"; do
        if [ -f "$candidate" ]; then
            BUILD_ENV_FILE="$candidate"
            break
        fi
    done

    if [ -z "$BUILD_ENV_FILE" ]; then
        log_error "No .env file found in $DOCKER_DIR"
        exit 1
    fi

    cd "$DOCKER_DIR"

    local_services=""
    for svc in "${SERVICES[@]}"; do
        local_services="$local_services $svc"
    done

    DOCKER_BUILDKIT=1 docker compose \
        -f docker-compose.prod.yml \
        --env-file "$BUILD_ENV_FILE" \
        --profile apps \
        build $NO_CACHE $local_services

    log_success "Docker images built"
fi

# =====================================================
# Step 2: Tag images for bundle format
# =====================================================

log_step "Tagging images..."

IMAGE_LIST=()

for svc in "${SERVICES[@]}"; do
    target_image="actionow-${svc}:${IMAGE_TAG}"

    if docker image inspect "$target_image" >/dev/null 2>&1; then
        IMAGE_LIST+=("$target_image")
        log_info "  $target_image (exists)"
        continue
    fi

    found=false
    for candidate in \
        "docker-${svc}:latest" \
        "docker-${svc}:${IMAGE_TAG}" \
        "actionow/${svc}:latest" \
        "actionow/${svc}:${IMAGE_TAG}"; do
        if docker image inspect "$candidate" >/dev/null 2>&1; then
            docker tag "$candidate" "$target_image"
            IMAGE_LIST+=("$target_image")
            log_info "  $candidate -> $target_image"
            found=true
            break
        fi
    done

    if [ "$found" = false ]; then
        log_error "  Image not found for service: $svc"
        log_error "  Tried: $target_image, docker-${svc}:latest, actionow/${svc}:latest"
        exit 1
    fi
done

log_success "All ${#IMAGE_LIST[@]} images tagged"

# =====================================================
# Step 3: Export images to tar
# =====================================================

log_step "Exporting ${#IMAGE_LIST[@]} images to tar..."

TEMP_DIR="$(mktemp -d)"
TAR_FILE="${TEMP_DIR}/actionow-images.tar"

docker save "${IMAGE_LIST[@]}" -o "$TAR_FILE"

TAR_SIZE=$(du -sh "$TAR_FILE" | cut -f1)
log_info "  Raw tar size: $TAR_SIZE"

# =====================================================
# Step 4: Optionally include deployment files
# =====================================================

if [ "$WITH_COMPOSE" = true ]; then
    log_step "Including deployment files..."

    DEPLOY_DIR="${TEMP_DIR}/deploy"
    mkdir -p "$DEPLOY_DIR/init-db"

    cp "$DOCKER_DIR/docker-compose.bundle.yml" "$DEPLOY_DIR/"
    cp "$DOCKER_DIR/.env.example" "$DEPLOY_DIR/"
    cp "$DOCKER_DIR/scripts/deploy-bundle.sh" "$DEPLOY_DIR/"
    chmod +x "$DEPLOY_DIR/deploy-bundle.sh"

    if [ -d "$DOCKER_DIR/init-db" ]; then
        cp "$DOCKER_DIR/init-db/"* "$DEPLOY_DIR/init-db/" 2>/dev/null || true
    fi

    log_success "Deployment files included"
fi

# =====================================================
# Step 5: Compress
# =====================================================

log_step "Compressing bundle..."

mkdir -p "$(dirname "$OUTPUT")"

if [ "$WITH_COMPOSE" = true ]; then
    (cd "$TEMP_DIR" && tar -czf "$OUTPUT" actionow-images.tar deploy/)
else
    gzip -c "$TAR_FILE" > "$OUTPUT"
fi

rm -rf "$TEMP_DIR"

FINAL_SIZE=$(du -sh "$OUTPUT" | cut -f1)

echo
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Bundle created successfully!                        ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo
echo -e "  ${CYAN}File:${NC}     $OUTPUT"
echo -e "  ${CYAN}Size:${NC}     $FINAL_SIZE"
echo -e "  ${CYAN}Images:${NC}   ${#IMAGE_LIST[@]} services"
echo -e "  ${CYAN}Tag:${NC}      $IMAGE_TAG"
echo

echo -e "${YELLOW}Deployment steps:${NC}"
echo "  1. Transfer to customer:"
echo "     scp $OUTPUT user@customer-host:/opt/actionow/"
echo
echo "  2. On customer machine (DB/Redis/MQ already running):"
echo "     cd /opt/actionow"
if [ "$WITH_COMPOSE" = true ]; then
    echo "     tar -xzf $(basename "$OUTPUT")"
    echo "     cp .env.example .env.bundle   # Edit with customer's infra settings"
    echo "     ./deploy-bundle.sh up"
else
    echo "     ./deploy-bundle.sh up $(basename "$OUTPUT")"
fi
echo
echo "  3. Check status:"
echo "     ./deploy-bundle.sh status"
