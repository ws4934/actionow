#!/bin/bash
# =====================================================
# Actionow Database Operations
# PostgreSQL helpers and reset-db command.
# Do NOT execute directly — sourced by deploy.sh.
# =====================================================

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

# =====================================================
# psql Helpers
# =====================================================

has_local_psql() {
    command -v psql &> /dev/null
}

resolve_docker_psql_host() {
    local db_host="$1"

    case "$(uname -s)" in
        Linux)
            echo "$db_host"
            ;;
        *)
            if [ "$db_host" = "127.0.0.1" ] || [ "$db_host" = "localhost" ]; then
                echo "host.docker.internal"
            else
                echo "$db_host"
            fi
            ;;
    esac
}

run_psql() {
    local db_name="$1"
    shift

    if has_local_psql; then
        PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}" psql \
            -h "${PGHOST:-127.0.0.1}" \
            -p "${PGPORT:-5432}" \
            -U "${POSTGRES_USER:-postgres}" \
            -d "$db_name" \
            "$@"
        return
    fi

    local docker_host
    docker_host=$(resolve_docker_psql_host "${PGHOST:-127.0.0.1}")
    local -a docker_args=(run --rm -i -e "PGPASSWORD=${PGPASSWORD:-${DB_PASSWORD:-}}")

    if [ "$(uname -s)" = "Linux" ]; then
        docker_args+=(--network host)
    fi

    docker "${docker_args[@]}" "${PSQL_CLIENT_IMAGE:-pgvector/pgvector:pg16}" psql \
        -h "$docker_host" \
        -p "${PGPORT:-5432}" \
        -U "${POSTGRES_USER:-postgres}" \
        -d "$db_name" \
        "$@"
}

detect_maintenance_db() {
    local requested_db="$1"
    local candidate

    for candidate in "$requested_db" postgres template1; do
        if run_psql "$candidate" -t -A -c "SELECT 1" >/dev/null 2>&1; then
            echo "$candidate"
            return 0
        fi
    done

    return 1
}

# =====================================================
# reset-db Command
# =====================================================

cmd_reset_db() {
    check_docker
    check_env

    if ! mode_supports_docker_infra; then
        local db_host db_port db_name db_user db_password admin_db
        db_host=$(_env_get DB_HOST 127.0.0.1)
        db_port=$(_env_get DB_PORT 5432)
        db_name=$(_env_get DB_NAME actionow)
        db_user=$(_env_get DB_USER actionow)
        db_password=$(_env_get DB_PASSWORD "")
        admin_db=$(_env_get DB_ADMIN_DB postgres)

        log_warn "This will DELETE database '$db_name' on $db_host:$db_port and reinitialize it!"
        echo
        echo -e "${CYAN}This operation will:${NC}"
        echo "  1. Stop all application containers"
        echo "  2. Terminate active PostgreSQL connections to $db_name"
        echo "  3. Drop and recreate database $db_name"
        echo "  4. Run docker/init-db/*.sql against the host PostgreSQL"
        echo

        read -p "Are you sure? (y/N): " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            log_info "Cancelled"
            return
        fi

        if [ -z "$db_password" ]; then
            log_error "DB_PASSWORD is empty in $ENV_FILE"
            exit 1
        fi

        log_step "Stopping app containers..."
        cmd_down 2>/dev/null || true

        export DB_PASSWORD="$db_password"
        export PGPASSWORD="$db_password"
        export PGHOST="$db_host"
        export PGPORT="$db_port"
        export POSTGRES_USER="$db_user"
        export POSTGRES_DB="$db_name"
        export PSQL_CLIENT_IMAGE="${PSQL_CLIENT_IMAGE:-pgvector/pgvector:pg16}"

        if ! has_local_psql; then
            log_warn "psql not found on host; using temporary Docker client image: $PSQL_CLIENT_IMAGE"
        fi

        local maintenance_db
        if ! maintenance_db=$(detect_maintenance_db "$admin_db"); then
            log_error "Unable to connect to a maintenance database (tried: $admin_db, postgres, template1)"
            exit 1
        fi

        log_step "Using maintenance database: $maintenance_db"
        run_psql "$maintenance_db" -v ON_ERROR_STOP=1 --set db_name="$db_name" <<'EOSQL'
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = :'db_name' AND pid <> pg_backend_pid();
SELECT format('DROP DATABASE IF EXISTS %I', :'db_name') \gexec
SELECT format('CREATE DATABASE %I', :'db_name') \gexec
EOSQL

        log_step "Running initialization scripts on host PostgreSQL..."
        (
            cd "$DOCKER_DIR/init-db"
            ./00-init-entrypoint.sh
        )

        log_info "Initialized schemas:"
        run_psql "$db_name" -c \
            "SELECT schema_name FROM information_schema.schemata
             WHERE schema_name NOT IN ('pg_catalog','information_schema','pg_toast')
             ORDER BY schema_name" 2>/dev/null || true

        echo
        log_success "Database reset completed!"
        echo
        echo -e "${CYAN}Default admin account:${NC}"
        echo "  Username: actionow"
        echo "  Password: (see docker/init-db/02-system-seed.sql — change on first login)"
        echo "  Email:    admin@actionow.ai"
        echo
        log_info "Run '$0 up' to start application services."
        return
    fi

    log_warn "This will DELETE all database data and reinitialize!"
    echo
    echo -e "${CYAN}This operation will:${NC}"
    echo "  1. Stop all application services"
    echo "  2. Stop PostgreSQL container"
    echo "  3. Delete PostgreSQL data volume"
    echo "  4. Restart PostgreSQL (triggers init scripts)"
    echo "  5. Wait for database initialization"
    echo

    read -p "Are you sure? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        log_info "Cancelled"
        return
    fi

    local db_user db_name
    db_user=$(_env_get DB_USER actionow)
    db_name=$(_env_get DB_NAME actionow)

    log_step "Stopping all services..."
    compose --profile infra --profile apps down --remove-orphans 2>/dev/null || true

    # Determine volume name (COMPOSE_PROJECT_NAME defaults to "actionow" in env file)
    local project_name
    project_name=$(_env_get COMPOSE_PROJECT_NAME actionow)
    local volume_name="${project_name}_postgres_data"

    log_step "Removing PostgreSQL data volume: $volume_name"

    if docker volume ls -q | grep -q "^${volume_name}$"; then
        docker volume rm "$volume_name"
        log_success "Volume $volume_name removed"
    else
        local found_volume
        found_volume=$(docker volume ls -q | grep -E "postgres_data$" | head -1)
        if [ -n "$found_volume" ]; then
            log_info "Found volume: $found_volume"
            docker volume rm "$found_volume"
            log_success "Volume $found_volume removed"
        else
            log_warn "No PostgreSQL volume found — may be first run"
        fi
    fi

    log_step "Starting PostgreSQL..."
    compose --profile infra up -d postgres

    log_step "Waiting for PostgreSQL to initialize..."
    local max_attempts=60
    local attempt=0
    while [ $attempt -lt $max_attempts ]; do
        if docker exec actionow-postgres pg_isready -U "$db_user" -d "$db_name" > /dev/null 2>&1; then
            if docker exec actionow-postgres psql -U "$db_user" -d "$db_name" \
                -c "SELECT 1 FROM t_user LIMIT 1" > /dev/null 2>&1; then
                break
            fi
        fi
        sleep 2
        ((attempt++)) || true
        echo -n "."
    done
    echo

    if [ $attempt -ge $max_attempts ]; then
        log_error "Database initialization timed out"
        log_info "Check logs with: $0 logs postgres"
        exit 1
    fi

    log_info "Initialized schemas:"
    docker exec actionow-postgres psql -U "$db_user" -d "$db_name" -c \
        "SELECT schema_name FROM information_schema.schemata
         WHERE schema_name NOT IN ('pg_catalog','information_schema','pg_toast')
         ORDER BY schema_name" 2>/dev/null || true

    echo
    log_success "Database reset completed!"
    echo
    echo -e "${CYAN}Default admin account:${NC}"
    echo "  Username: actionow"
    echo "  Password: (see docker/init-db/02-system-seed.sql — change on first login)"
    echo "  Email:    admin@actionow.ai"
    echo
    log_info "Run '$0 up' to start application services."
}
