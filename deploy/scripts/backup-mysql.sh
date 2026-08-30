#!/usr/bin/env bash
# Dump the production MySQL database to a timestamped gzip file and prune
# backups older than the retention window (only files matching our exact
# filename pattern are ever deleted).
#
# Usage:
#   deploy/scripts/backup-mysql.sh [--env-file deploy/production/.env] [--output-dir DIR]
#
# Output: <output-dir>/ride_dispatch_YYYYMMDD_HHMMSS.sql.gz
# Credentials are read from the env file, never hard-coded here.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_DIR/deploy/production/.env"
OUTPUT_DIR="$REPO_DIR/deploy/production/backups"
RETENTION_DAYS=14
COMPOSE_FILE="$REPO_DIR/deploy/docker-compose.production.yml"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: deploy/scripts/backup-mysql.sh [--env-file deploy/production/.env] [--output-dir DIR]" >&2
            exit 2
            ;;
    esac
done

[[ -f "$ENV_FILE" ]] || { echo "Env file not found: $ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_NAME:?DB_NAME is missing in $ENV_FILE}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is missing in $ENV_FILE}"

mkdir -p "$OUTPUT_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUTPUT_FILE="$OUTPUT_DIR/ride_dispatch_${STAMP}.sql.gz"

echo "==> Dumping database '$DB_NAME' to $OUTPUT_FILE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    mysql mysqldump -uroot \
    --single-transaction \
    --quick \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip > "$OUTPUT_FILE"

# A truncated dump must never look like a successful backup.
[[ -s "$OUTPUT_FILE" ]] || { echo "Backup file is empty; removing it" >&2; rm -f "$OUTPUT_FILE"; exit 1; }
if ! gzip -dc "$OUTPUT_FILE" | head -20 | grep -q "MySQL dump"; then
    echo "Backup file does not look like a mysqldump; removing it" >&2
    rm -f "$OUTPUT_FILE"
    exit 1
fi

echo "==> Pruning backups older than $RETENTION_DAYS days"
find "$OUTPUT_DIR" -maxdepth 1 -type f -name 'ride_dispatch_*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete

echo "Backup written: $OUTPUT_FILE"
