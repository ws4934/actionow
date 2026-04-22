#!/bin/bash

# =====================================================
# Actionow Database Docker Init Entrypoint
#
# This script is executed by PostgreSQL's docker-entrypoint
# on first database initialization.
#
# It runs all SQL migration scripts in order and tracks
# their execution for idempotency.
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

has_local_psql() {
    command -v psql >/dev/null 2>&1
}

resolve_docker_psql_host() {
    local db_host="${PGHOST:-127.0.0.1}"

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
    if has_local_psql; then
        psql "$@"
        return
    fi

    local docker_host
    docker_host=$(resolve_docker_psql_host)
    local -a docker_args=(
        run --rm -i
        -e "PGPASSWORD=${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"
        -v "$SCRIPT_DIR:$SCRIPT_DIR:ro"
        -w "$SCRIPT_DIR"
    )

    if [ "$(uname -s)" = "Linux" ]; then
        docker_args+=(--network host)
    fi

    docker "${docker_args[@]}" "${PSQL_CLIENT_IMAGE:-pgvector/pgvector:pg16}" psql \
        -h "$docker_host" \
        -p "${PGPORT:-5432}" \
        -U "${POSTGRES_USER}" \
        "$@"
}

echo "======================================"
echo "Actionow Database Initialization"
echo "======================================"
echo ""

# Create migration tracking table
run_psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE TABLE IF NOT EXISTS t_migration_history (
        id SERIAL PRIMARY KEY,
        filename VARCHAR(255) NOT NULL UNIQUE,
        checksum VARCHAR(64),
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        execution_time_ms INTEGER,
        success BOOLEAN DEFAULT TRUE
    );
    COMMENT ON TABLE t_migration_history IS 'Database migration history tracking';
EOSQL

# Function to check if migration is applied
is_applied() {
    local filename=$1
    local result=$(run_psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -t -A -c \
        "SELECT COUNT(*) FROM t_migration_history WHERE filename = '$filename' AND success = TRUE")
    [ "$result" -gt 0 ]
}

# Function to record migration
record_migration() {
    local filename=$1
    local success=$2
    run_psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c \
        "INSERT INTO t_migration_history (filename, success) VALUES ('$filename', $success) ON CONFLICT (filename) DO UPDATE SET success = EXCLUDED.success, applied_at = CURRENT_TIMESTAMP;"
}

# Run migrations in order
migration_count=0
error_count=0

for sql_file in "$SCRIPT_DIR"/*.sql; do
    if [ -f "$sql_file" ]; then
        filename=$(basename "$sql_file")

        # Skip if already applied
        if is_applied "$filename"; then
            echo "[SKIP] $filename (already applied)"
            continue
        fi

        echo "[RUN] $filename"

        if run_psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$sql_file"; then
            record_migration "$filename" "TRUE"
            echo "[OK] $filename applied successfully"
            ((migration_count++)) || true
        else
            record_migration "$filename" "FALSE"
            echo "[ERROR] $filename failed"
            ((error_count++)) || true
        fi
        echo ""
    fi
done

echo "======================================"
echo "Migration Summary"
echo "======================================"
echo "Applied: $migration_count"
echo "Errors: $error_count"
echo "======================================"

if [ $error_count -gt 0 ]; then
    echo "WARNING: Some migrations failed!"
    exit 1
fi

echo "Database initialization completed successfully!"
