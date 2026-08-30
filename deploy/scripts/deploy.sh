#!/usr/bin/env bash
# Build and start the production compose stack, then wait for backend health.
#
# Usage:
#   deploy/scripts/deploy.sh [--env-file deploy/production/.env] [--skip-build]
#
# Steps: check environment → check .env → build → up -d → wait for health.
# Exits non-zero on any failure; it never prints "successful" before health is OK.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$REPO_DIR/deploy/docker-compose.production.yml"
ENV_FILE="$REPO_DIR/deploy/production/.env"
BUILD=true

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --skip-build)
            BUILD=false
            shift
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: deploy/scripts/deploy.sh [--env-file deploy/production/.env] [--skip-build]" >&2
            exit 2
            ;;
    esac
done

command -v docker >/dev/null 2>&1 || { echo "docker is not installed or not on PATH" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "docker compose plugin is not available" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "Env file not found: $ENV_FILE (copy deploy/production/.env.example first)" >&2; exit 1; }

if grep -q "CHANGE_ME" "$ENV_FILE"; then
    echo "Env file still contains CHANGE_ME placeholders: $ENV_FILE" >&2
    echo "Fill in real values before deploying." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
BACKEND_IMAGE="${BACKEND_IMAGE:-ride-dispatch-backend:local}"
NGINX_IMAGE="${NGINX_IMAGE:-ride-dispatch-nginx:local}"

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

echo "==> Building images (this runs the Maven and frontend builds inside Docker)"
if [[ "$BUILD" == true ]]; then
    compose build
else
    echo "    --skip-build: reusing existing images"
fi

echo "==> Preserving current images as rollback candidates"
for image in "$BACKEND_IMAGE" "$NGINX_IMAGE"; do
    if docker image inspect "$image" >/dev/null 2>&1; then
        docker tag "$image" "${image%:*}:rollback"
        echo "    $image -> ${image%:*}:rollback"
    fi
done

echo "==> Starting stack"
compose up -d

echo "==> Waiting for backend health (up to 180s)"
deadline=$((SECONDS + 180))
until compose ps backend | grep -q "healthy"; do
    if (( SECONDS > deadline )); then
        echo "Backend did not become healthy in time. Recent logs:" >&2
        compose logs --tail=50 backend >&2
        exit 1
    fi
    sleep 5
done

echo "==> Waiting for nginx (up to 60s)"
deadline=$((SECONDS + 60))
until compose ps --status running --services | grep -qx nginx; do
    if (( SECONDS > deadline )); then
        echo "Nginx did not start in time. Recent logs:" >&2
        compose logs --tail=50 nginx >&2
        exit 1
    fi
    sleep 5
done

compose ps

echo "==> Deploy finished; services are up. Run deploy/scripts/smoke-production.sh to verify."
