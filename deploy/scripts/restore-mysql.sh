#!/usr/bin/env bash
# Restore a backup INTO the running production MySQL. This is an overwriting,
# destructive operation: the target database is dropped and recreated.
#
# Usage:
#   deploy/scripts/restore-mysql.sh <backup.sql[.gz]> [--env-file deploy/production/.env] [--force]
#
# Without --force the script asks for the word RESTORE as confirmation.
# Any failure (missing file, aborted confirmation, mysql error) exits non-zero.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_DIR/deploy/production/.env"
COMPOSE_FILE="$REPO_DIR/deploy/docker-compose.production.yml"
BACKUP_FILE=""
FORCE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --force)
            FORCE=true
            shift
            ;;
        -*)
            echo "Unknown option: $1" >&2
            exit 2
            ;;
        *)
            if [[ -z "$BACKUP_FILE" ]]; then
                BACKUP_FILE="$1"
            else
                echo "Unexpected extra argument: $1" >&2
                exit 2
            fi
            shift
            ;;
    esac
done

[[ -n "$BACKUP_FILE" ]] || {
    echo "Usage: deploy/scripts/restore-mysql.sh <backup.sql[.gz]> [--env-file deploy/production/.env] [--force]" >&2
    exit 2
}
[[ -f "$BACKUP_FILE" ]] || { echo "Backup file not found: $BACKUP_FILE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "Env file not found: $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_NAME:?DB_NAME is missing in $ENV_FILE}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is missing in $ENV_FILE}"

if [[ "$FORCE" != true ]]; then
    echo "This will DROP database '$DB_NAME' and replace it with: $BACKUP_FILE"
    read -r -p "Type RESTORE to continue: " answer
    if [[ "$answer" != "RESTORE" ]]; then
        echo "Restore aborted."
        exit 1
    fi
fi

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

echo "==> Recreating database '$DB_NAME'"
compose exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    mysql sh -c 'exec mysql -uroot --default-character-set=utf8mb4' <<SQL
DROP DATABASE IF EXISTS \`$DB_NAME\`;
CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL

echo "==> Importing backup"
case "$BACKUP_FILE" in
    *.gz)
        gzip -dc "$BACKUP_FILE" | compose exec -T \
            -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
            mysql sh -c "exec mysql -uroot --default-character-set=utf8mb4 '$DB_NAME'"
        ;;
    *)
        compose exec -T \
            -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
            mysql sh -c "exec mysql -uroot --default-character-set=utf8mb4 '$DB_NAME'" < "$BACKUP_FILE"
        ;;
esac

echo "==> Restore finished. Start/verify the backend and check business data before resuming traffic."
