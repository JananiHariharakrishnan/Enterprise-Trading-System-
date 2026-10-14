#!/usr/bin/env bash

set -euo pipefail

# Find the sprint folder
SPRINT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MIGRATIONS_DIR="${SPRINT_DIR}/migrations"
ENV_FILE="${SPRINT_DIR}/.env"

# ------------------------------------------------------------
# Save TARGET_DATABASE if it was already provided
# ------------------------------------------------------------

TARGET_DATABASE_FROM_ENV="${TARGET_DATABASE:-}"

# ------------------------------------------------------------
# Load repository .env
# ------------------------------------------------------------

if [[ -f "${SPRINT_DIR}/../../.env" ]]; then
    set -a
    source "${SPRINT_DIR}/../../.env"
    set +a
fi

if [[ -f "${ENV_FILE}" ]]; then
    set -a
    source "${ENV_FILE}"
    set +a
fi

# ------------------------------------------------------------
# Determine target database
#
# TARGET_DATABASE takes priority.
# Otherwise use POSTGRES_DB or parse DATABASE_URL from .env.
# ------------------------------------------------------------

TARGET_DATABASE="${TARGET_DATABASE_FROM_ENV:-${POSTGRES_DB:-${DB_NAME:-}}}"

# Parse DATABASE_URL if host/database is not set
if [[ -n "${DATABASE_URL:-}" ]]; then
    CLEAN_URL="${DATABASE_URL#jdbc:}"
    CLEAN_URL="${CLEAN_URL#postgresql://}"
    if [[ "$CLEAN_URL" == *"@"* ]]; then
        USERPASS="${CLEAN_URL%%@*}"
        HOSTPORTDB="${CLEAN_URL#*@}"
        export PGUSER="${PGUSER:-${POSTGRES_USER:-${DB_USER:-${USERPASS%%:*}}}}"
        export PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-${DB_PASSWORD:-${USERPASS#*:}}}}"
    else
        HOSTPORTDB="$CLEAN_URL"
    fi
    HOSTPORT="${HOSTPORTDB%%/*}"
    DB_NAME_FROM_URL="${HOSTPORTDB#*/}"
    DB_NAME_FROM_URL="${DB_NAME_FROM_URL%%\?*}"
    export PGHOST="${PGHOST:-${POSTGRES_HOST:-${DB_HOST:-${HOSTPORT%%:*}}}}"
    if [[ "$HOSTPORT" == *":"* ]]; then
        export PGPORT="${PGPORT:-${POSTGRES_PORT:-${DB_PORT:-${HOSTPORT#*:}}}}"
    fi
    TARGET_DATABASE="${TARGET_DATABASE:-$DB_NAME_FROM_URL}"
fi

if [[ -z "${TARGET_DATABASE}" ]]; then
    echo "ERROR: TARGET_DATABASE is not set and POSTGRES_DB / DATABASE_URL was not found in .env." >&2
    exit 1
fi

# ------------------------------------------------------------
# PostgreSQL connection configuration
# ------------------------------------------------------------

export PGHOST="${PGHOST:-${POSTGRES_HOST:-${DB_HOST:-localhost}}}"
export PGPORT="${PGPORT:-${POSTGRES_PORT:-${DB_PORT:-5432}}}"
export PGUSER="${PGUSER:-${POSTGRES_USER:-${DB_USER:-postgres}}}"

if [[ -z "${PGPASSWORD:-}" && -n "${POSTGRES_PASSWORD:-}" ]]; then
    export PGPASSWORD="${POSTGRES_PASSWORD}"
fi

# ------------------------------------------------------------
# Check migrations directory
# ------------------------------------------------------------

if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
    echo "ERROR: migrations/ directory not found." >&2
    exit 1
fi

# ------------------------------------------------------------
# Apply all SQL files in filename order
#
# This includes both migrations and seed SQL files.
# ------------------------------------------------------------

echo "Applying migrations and seed data to database: ${TARGET_DATABASE}"
echo "Includes tradingsystem.notification (009) and email opt-in (010)."

mapfile -d '' MIGRATIONS < <(
    find "${MIGRATIONS_DIR}" \
        -maxdepth 1 \
        -type f \
        -name '*.sql' \
        -print0 |
    LC_ALL=C sort -z
)

if [[ "${#MIGRATIONS[@]}" -eq 0 ]]; then
    echo "ERROR: No SQL files found in migrations/ directory." >&2
    exit 1
fi

for migration in "${MIGRATIONS[@]}"; do

    echo "Applying: $(basename "${migration}")"

    psql \
        -h "${PGHOST}" \
        -p "${PGPORT}" \
        -U "${PGUSER}" \
        -d "${TARGET_DATABASE}" \
        -v ON_ERROR_STOP=1 \
        --single-transaction \
        -f "${migration}"

done

echo "Verifying tradingsystem.notification"

NOTIFICATION_TABLE="$(
    psql \
        -h "${PGHOST}" \
        -p "${PGPORT}" \
        -U "${PGUSER}" \
        -d "${TARGET_DATABASE}" \
        -tAc "SELECT to_regclass('tradingsystem.notification')"
)"

if [[ "${NOTIFICATION_TABLE}" != "tradingsystem.notification" ]]; then
    echo "ERROR: tradingsystem.notification was not created. Expected migrations/009_create_notification.sql to add it." >&2
    exit 1
fi

echo "Database migration and seeding completed successfully."
