#!/bin/bash
# =====================================================
# Actionow Health Check Script
# Checks health status of all services
# =====================================================

set -e

# Source shared library (provides colors, paths, env detection, logging)
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

# Load environment variables for port values
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

# Service definitions
declare -A SERVICES=(
    ["gateway"]="${GATEWAY_PORT:-8080}"
    ["user"]="${USER_PORT:-8081}"
    ["workspace"]="${WORKSPACE_PORT:-8082}"
    ["wallet"]="${WALLET_PORT:-8083}"
    ["project"]="${PROJECT_PORT:-8084}"
    ["ai"]="${AI_PORT:-8086}"
    ["task"]="${TASK_PORT:-8087}"
    ["collab"]="${COLLAB_PORT:-8088}"
    ["system"]="${SYSTEM_PORT:-8089}"
    ["canvas"]="${CANVAS_PORT:-8090}"
    ["agent"]="${AGENT_PORT:-8091}"
    ["billing"]="${BILLING_PORT:-8092}"
)

declare -A INFRA=(
    ["postgres"]="${POSTGRES_EXPOSE_PORT:-5432}"
    ["redis"]="${REDIS_EXPOSE_PORT:-6379}"
    ["rabbitmq"]="${RABBITMQ_MANAGEMENT_PORT:-15672}"
    ["minio"]="${MINIO_CONSOLE_PORT:-9001}"
)

# =====================================================
# Health Check Functions
# =====================================================

check_http() {
    local name="$1"
    local port="$2"
    local path="${3:-/actuator/health}"
    local timeout="${4:-5}"

    local url="http://localhost:${port}${path}"
    local status

    status=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "$timeout" "$url" 2>/dev/null || echo "000")

    if [ "$status" = "200" ]; then
        echo -e "  ${GREEN}✓${NC} $name (port $port): ${GREEN}healthy${NC}"
        return 0
    else
        echo -e "  ${RED}✗${NC} $name (port $port): ${RED}unhealthy${NC} (HTTP $status)"
        return 1
    fi
}

check_tcp() {
    local name="$1"
    local port="$2"
    local timeout="${3:-2}"

    if timeout "$timeout" bash -c "echo >/dev/tcp/localhost/$port" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} $name (port $port): ${GREEN}reachable${NC}"
        return 0
    else
        echo -e "  ${RED}✗${NC} $name (port $port): ${RED}unreachable${NC}"
        return 1
    fi
}

check_container() {
    local name="$1"
    local container="actionow-$name"

    local status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "not found")
    local health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")

    if [ "$status" = "running" ]; then
        if [ "$health" = "healthy" ]; then
            echo -e "  ${GREEN}✓${NC} $container: ${GREEN}running (healthy)${NC}"
            return 0
        elif [ "$health" = "starting" ]; then
            echo -e "  ${YELLOW}○${NC} $container: ${YELLOW}starting${NC}"
            return 1
        else
            echo -e "  ${YELLOW}○${NC} $container: ${YELLOW}running${NC}"
            return 0
        fi
    else
        echo -e "  ${RED}✗${NC} $container: ${RED}$status${NC}"
        return 1
    fi
}

# =====================================================
# Main
# =====================================================

echo
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "${BLUE}       Actionow Health Check${NC}"
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "Mode: ${YELLOW}${COMPOSE_MODE}${NC}"
echo

MODE="${1:-all}"
VERBOSE="${2:-}"

healthy_count=0
unhealthy_count=0

case "$MODE" in
    containers|c)
        echo -e "${YELLOW}Container Status:${NC}"
        echo

        if [ "$COMPOSE_MODE" != "apps" ]; then
            for name in "${!INFRA[@]}"; do
                if check_container "$name"; then
                    ((healthy_count++))
                else
                    ((unhealthy_count++))
                fi
            done

            echo
        else
            echo -e "  ${YELLOW}○${NC} External infra is not managed by Docker in apps mode"
            echo
        fi

        for name in "${!SERVICES[@]}"; do
            if check_container "$name"; then
                ((healthy_count++))
            else
                ((unhealthy_count++))
            fi
        done
        ;;

    infra|i)
        echo -e "${YELLOW}Infrastructure Health:${NC}"
        echo

        if check_tcp "postgres" "${POSTGRES_EXPOSE_PORT:-5432}"; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        if check_tcp "redis" "${REDIS_EXPOSE_PORT:-6379}"; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        if check_http "rabbitmq" "${RABBITMQ_MANAGEMENT_PORT:-15672}" "/api/health/checks/alarms" 3; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        if check_http "minio" "${MINIO_PORT:-9000}" "/minio/health/live" 3; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi
        ;;

    services|s)
        echo -e "${YELLOW}Service Health:${NC}"
        echo

        for name in "${!SERVICES[@]}"; do
            port="${SERVICES[$name]}"
            if check_http "$name" "$port"; then
                ((healthy_count++))
            else
                ((unhealthy_count++))
            fi
        done
        ;;

    all|*)
        echo -e "${YELLOW}Infrastructure:${NC}"
        echo

        if check_tcp "postgres" "${POSTGRES_EXPOSE_PORT:-5432}"; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        if check_tcp "redis" "${REDIS_EXPOSE_PORT:-6379}"; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        if check_http "rabbitmq" "${RABBITMQ_MANAGEMENT_PORT:-15672}" "/api/health/checks/alarms" 3; then
            ((healthy_count++))
        else
            ((unhealthy_count++))
        fi

        echo
        echo -e "${YELLOW}Application Services:${NC}"
        echo

        for name in "${!SERVICES[@]}"; do
            port="${SERVICES[$name]}"
            if check_http "$name" "$port"; then
                ((healthy_count++))
            else
                ((unhealthy_count++))
            fi
        done
        ;;
esac

# Summary
echo
echo -e "${BLUE}────────────────────────────────────────${NC}"
total=$((healthy_count + unhealthy_count))
echo -e "Summary: ${GREEN}$healthy_count${NC}/${total} services healthy"

if [ $unhealthy_count -gt 0 ]; then
    echo -e "${RED}Warning: $unhealthy_count service(s) unhealthy${NC}"
    exit 1
else
    echo -e "${GREEN}All services healthy ✓${NC}"
    exit 0
fi
