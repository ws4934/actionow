#!/bin/bash
# =====================================================
# Actionow Platform Management Script
# Single entry point for all deployment operations.
# =====================================================

set -e

# Source sub-modules (shared library, init wizard, database operations)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"
source "$SCRIPT_DIR/_init.sh"
source "$SCRIPT_DIR/_db.sh"

# =====================================================
# Help
# =====================================================

show_help() {
    cat << EOF
${CYAN}Actionow Platform Management Script${NC}

${YELLOW}Usage:${NC} $0 <command> [options]

${YELLOW}Commands:${NC}
  ${GREEN}init${NC}                     Interactive setup wizard (configure + start)
  ${GREEN}infra${NC}                    Start infrastructure only (PostgreSQL, Redis, RabbitMQ, MinIO)
  ${GREEN}up${NC} [services...]         Start all services or specified ones
  ${GREEN}down${NC} [services...]       Stop all services or specified ones
  ${GREEN}restart${NC} [services...]    Restart services (stop + start)
  ${GREEN}rebuild${NC} [services...]    Rebuild Docker images and restart (BuildKit, prod mode)
  ${GREEN}build${NC} [services...]      Build Maven JAR files locally (dev mode)
  ${GREEN}logs${NC} [service] [-f]      View logs (follow with -f)
  ${GREEN}status${NC}                   Show service status
  ${GREEN}exec${NC} <service>           Open shell in service container
  ${GREEN}health${NC}                   Check health of all services
  ${GREEN}reset-db${NC}                 Reset database (drop all data and reinitialize)
  ${GREEN}clean${NC}                    Remove all containers and volumes

${YELLOW}Available Services:${NC}
  ${CYAN}$ALL_SERVICES${NC}

${YELLOW}Modes:${NC}
  ${GREEN}prod${NC} (default)  Build from source inside Docker (use for production)
  ${GREEN}dev${NC}             Compile JAR locally, volume-mount into container (fast iteration)
  ${GREEN}apps${NC}            Build app containers only, connect to external PostgreSQL/Redis/RabbitMQ/MinIO

  Switch mode: ${CYAN}COMPOSE_MODE=apps $0 up${NC}

${YELLOW}Examples:${NC}
  $0 init                    # Interactive setup wizard
  $0 infra                   # Start infrastructure for local development
  $0 up                      # Start all services
  $0 up gateway user         # Start only gateway and user services
  $0 rebuild                 # Rebuild ALL services (slow, ~10 min)
  $0 rebuild wallet          # Rebuild only wallet (fast, ~2 min)
  $0 rebuild ai agent        # Rebuild ai + agent in parallel
  $0 logs -f gateway         # Follow gateway logs
  $0 exec postgres           # Open shell in postgres container
  $0 reset-db                # Drop and recreate database
  COMPOSE_MODE=dev $0 build  # Build all JAR files locally
  COMPOSE_MODE=apps $0 up    # Start app containers with external infra
EOF
}

# =====================================================
# Helpers
# =====================================================

get_service_jar() {
    local service=$1
    echo "$PROJECT_ROOT/actionow-$service/target/actionow-$service-0.0.1-SNAPSHOT.jar"
}

show_status() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Service Status${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    compose ps

    local gateway_port
    gateway_port=$(_env_get GATEWAY_PORT 8080)
    echo
    echo -e "${GREEN}Service URLs:${NC}"
    echo "  • Gateway:    http://localhost:${gateway_port}"
    if mode_supports_docker_infra; then
        echo "  • RabbitMQ:   http://localhost:15672"
        echo "  • MinIO:      http://localhost:9001"
    fi
    echo
}

# =====================================================
# Command Functions
# =====================================================

cmd_infra() {
    check_docker
    check_env

    if ! mode_supports_docker_infra; then
        log_warn "COMPOSE_MODE=apps uses external infrastructure; nothing to start via Docker."
        log_info "Configure and start PostgreSQL/Redis/RabbitMQ/MinIO on the host, then run '$0 up'."
        return
    fi

    log_info "Starting infrastructure services..."
    compose --profile infra up -d

    compose --profile infra ps
    log_success "Infrastructure is ready!"
    echo
    echo -e "${CYAN}Service URLs:${NC}"
    echo "  PostgreSQL: localhost:5432"
    echo "  Redis:      localhost:6379"
    echo "  RabbitMQ:   http://localhost:15672"
    echo "  MinIO:      http://localhost:9001"
    echo
    log_info "Run '$0 up' to start application services."
}

cmd_build() {
    local services=("$@")
    cd "$PROJECT_ROOT"

    if [ ${#services[@]} -eq 0 ]; then
        log_info "Building all services..."
        if command -v mvn &> /dev/null; then
            mvn clean package -DskipTests -q
        elif [ -f "./mvnw" ]; then
            ./mvnw clean package -DskipTests -q
        else
            log_error "Maven not found (mvn or ./mvnw)"
            exit 1
        fi
    else
        for service in "${services[@]}"; do
            log_step "Building actionow-$service..."
            if command -v mvn &> /dev/null; then
                mvn clean package -DskipTests -q -pl "actionow-$service" -am
            else
                ./mvnw clean package -DskipTests -q -pl "actionow-$service" -am
            fi
        done
    fi

    log_success "Build completed"
}

cmd_up() {
    check_docker
    check_env

    if [ "$COMPOSE_MODE" = "dev" ]; then
        if ! docker ps --format '{{.Names}}' | grep -q "actionow-postgres"; then
            log_warn "Infrastructure not running. Starting it first..."
            cmd_infra
        fi

        local services=("$@")
        if [ ${#services[@]} -eq 0 ]; then
            if [ ! -f "$PROJECT_ROOT/actionow-gateway/target/actionow-gateway-0.0.1-SNAPSHOT.jar" ]; then
                log_warn "JARs not found. Building first..."
                cmd_build
            fi
            log_info "Starting all services..."
            compose --profile infra --profile apps up -d
        else
            for service in "${services[@]}"; do
                local jar_path
                jar_path=$(get_service_jar "$service")
                if [ ! -f "$jar_path" ]; then
                    log_warn "JAR for $service not found. Building..."
                    cmd_build "$service"
                fi
                log_step "Starting $service..."
                compose --profile "$service" up -d
            done
        fi
    elif [ "$COMPOSE_MODE" = "apps" ]; then
        log_info "Starting app containers with external infrastructure..."
        compose up -d "$@"
    else
        log_info "Starting services..."
        compose_all_profiles up -d "$@"
    fi

    sleep 3
    cmd_status "$@"
    log_success "Services started"
}

cmd_down() {
    check_docker
    local services=("$@")

    if [ ${#services[@]} -eq 0 ]; then
        log_info "Stopping all services..."
        DB_PASSWORD="${DB_PASSWORD:-_}" \
        REDIS_PASSWORD="${REDIS_PASSWORD:-_}" \
        RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-_}" \
        INTERNAL_AUTH_SECRET="${INTERNAL_AUTH_SECRET:-_}" \
        JWT_SECRET="${JWT_SECRET:-_}" \
        compose_all_profiles down --remove-orphans
    else
        for service in "${services[@]}"; do
            log_step "Stopping $service..."
            compose stop "$service"
            compose rm -f "$service"
        done
    fi

    log_success "Services stopped"
}

cmd_restart() {
    cmd_down "$@"
    cmd_up "$@"
}

cmd_rebuild() {
    check_docker
    check_env

    local services="$*"

    if [ -z "$services" ]; then
        log_warn "Rebuilding ALL services. To rebuild a single service:"
        log_warn "  $0 rebuild <service>       e.g. $0 rebuild wallet"
        log_warn "  $0 rebuild <s1> <s2> ...   e.g. $0 rebuild ai agent"
    else
        log_info "Rebuilding: $services"
    fi

    cd "$DOCKER_DIR"

    local max_retries=2
    local build_ok=false
    for i in $(seq 1 $max_retries); do
        if DOCKER_BUILDKIT=1 compose_all_profiles build $services; then
            build_ok=true
            break
        fi
        if [ "$i" -lt "$max_retries" ]; then
            log_warn "Build attempt $i failed — retrying in 10s..."
            sleep 10
        fi
    done
    if [ "$build_ok" != "true" ]; then
        log_error "Build failed after $max_retries attempts."
        exit 1
    fi

    compose_all_profiles up -d $services

    show_status
}

cmd_logs() {
    check_docker
    compose logs "$@"
}

cmd_status() {
    check_docker
    compose ps -a "$@"
}

cmd_exec() {
    local service=$1

    if [ -z "$service" ]; then
        log_error "Please specify a service"
        echo "Usage: $0 exec <service>"
        echo "Available: $ALL_SERVICES $INFRA_SERVICES"
        exit 1
    fi

    docker exec -it "actionow-$service" /bin/sh
}

cmd_health() {
    "$SCRIPT_DIR/health-check.sh" "$@"
}

cmd_clean() {
    check_docker
    log_warn "This will remove all containers and data volumes!"

    read -p "Are you sure? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        log_info "Cancelled"
        return
    fi

    log_info "Stopping and removing all containers..."
    DB_PASSWORD="${DB_PASSWORD:-_}" \
    REDIS_PASSWORD="${REDIS_PASSWORD:-_}" \
    RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-_}" \
    INTERNAL_AUTH_SECRET="${INTERNAL_AUTH_SECRET:-_}" \
    JWT_SECRET="${JWT_SECRET:-_}" \
    compose_all_profiles down -v --remove-orphans

    read -p "Remove Docker images as well? (y/N): " remove_images
    if [ "$remove_images" = "y" ] || [ "$remove_images" = "Y" ]; then
        docker images --format '{{.Repository}}:{{.Tag}}' | grep "^actionow/" | xargs -r docker rmi
        log_success "Images removed"
    fi

    log_success "Cleanup completed"
}

# =====================================================
# Main Entry Point
# =====================================================

main() {
    check_docker

    local command="${1:-help}"
    shift || true

    case "$command" in
        init)        cmd_init "$@" ;;
        infra)       cmd_infra "$@" ;;
        up|start)    cmd_up "$@" ;;
        down|stop)   cmd_down "$@" ;;
        restart)     cmd_restart "$@" ;;
        rebuild)     cmd_rebuild "$@" ;;
        build)       cmd_build "$@" ;;
        logs)        cmd_logs "$@" ;;
        status|ps)   cmd_status "$@" ;;
        exec)        cmd_exec "$@" ;;
        health)      cmd_health "$@" ;;
        reset-db)    cmd_reset_db "$@" ;;
        clean)       cmd_clean "$@" ;;
        help|--help|-h|"")
            show_help ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
