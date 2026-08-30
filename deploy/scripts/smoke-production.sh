#!/usr/bin/env bash
# Production smoke test. Read-only by default; nothing in this script creates
# business data unless credentials are explicitly provided via environment.
#
# Required checks: HTTPS endpoint, backend health (via public API), passenger H5
# index, admin page, login API, SPA fallback.
#
# Environment:
#   SMOKE_BASE_URL      passenger site base URL   (default https://localhost)
#   SMOKE_ADMIN_URL     admin site base URL       (default $SMOKE_BASE_URL:8443)
#   SMOKE_INSECURE_TLS  "true" → curl -k (self-signed certificates only)
#   SMOKE_ADMIN_USERNAME / SMOKE_ADMIN_PASSWORD
#                       when both set: performs a real admin login and one
#                       authenticated read API call, then logs out
set -euo pipefail

BASE_URL="${SMOKE_BASE_URL:-https://localhost}"
BASE_URL="${BASE_URL%/}"
ADMIN_URL="${SMOKE_ADMIN_URL:-}"
if [[ -z "$ADMIN_URL" ]]; then
    if [[ "$BASE_URL" =~ ^https?://[^/]+:[0-9]+$ ]]; then
        ADMIN_URL="${BASE_URL%:*}:8443"
    else
        ADMIN_URL="$BASE_URL:8443"
    fi
fi
ADMIN_URL="${ADMIN_URL%/}"

CURL=(curl -sS --max-time 15)
if [[ "${SMOKE_INSECURE_TLS:-false}" == "true" ]]; then
    CURL+=(-k)
fi

FAILURES=0

check() {
    local name="$1" expected="$2" actual="$3"
    if [[ "$actual" == "$expected" ]]; then
        echo "PASS  $name"
    else
        echo "FAIL  $name (expected $expected, got $actual)"
        FAILURES=$((FAILURES + 1))
    fi
}

body_matches() {
    local name="$1" pattern="$2" url="$3"
    local body
    if body=$("${CURL[@]}" "$url" 2>/dev/null) && [[ "$body" =~ $pattern ]]; then
        echo "PASS  $name"
    else
        echo "FAIL  $name (missing pattern '$pattern' at $url)"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "==> Smoke testing $BASE_URL (admin: $ADMIN_URL)"

# 1. Passenger H5 index.
body_matches "Passenger H5 index" '<div id="app"' "$BASE_URL/"

# 2. Passenger SPA fallback (unknown order page must serve the app, not 404).
body_matches "Passenger SPA fallback" '<div id="app"' "$BASE_URL/order/SPA-FALLBACK-CHECK"

# 3. Public brand API proves nginx → backend proxying and DB read path.
body_matches "Public brand API" 'companyName' "$BASE_URL/api/v1/public/brand"

# 4. Admin login API exists and rejects bad credentials (auth flow alive).
status=$("${CURL[@]}" -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d '{"username":"__smoke__","password":"__wrong__"}' \
    "$BASE_URL/api/v1/auth/admin/login" || true)
check "Admin login API rejects bad credentials" 401 "$status"

# 5. Admin Web index.
body_matches "Admin Web index" '<div id="app"' "$ADMIN_URL/"

# 6. Admin SPA fallback.
body_matches "Admin SPA fallback" '<div id="app"' "$ADMIN_URL/orders"

# 7. Optional real admin login + authenticated read (no data mutation).
if [[ -n "${SMOKE_ADMIN_USERNAME:-}" && -n "${SMOKE_ADMIN_PASSWORD:-}" ]]; then
    echo "==> Real admin credentials provided; verifying authenticated read API"
    token=$("${CURL[@]}" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$SMOKE_ADMIN_USERNAME\",\"password\":\"$SMOKE_ADMIN_PASSWORD\"}" \
        "$BASE_URL/api/v1/auth/admin/login" \
        | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
    if [[ -n "$token" ]]; then
        echo "PASS  Admin login"
        status=$("${CURL[@]}" -o /dev/null -w '%{http_code}' \
            -H "Authorization: Bearer $token" \
            "$BASE_URL/api/v1/admin/places" || true)
        check "Authenticated admin read API" 200 "$status"
        "${CURL[@]}" -o /dev/null -X POST \
            -H "Authorization: Bearer $token" \
            "$BASE_URL/api/v1/auth/logout" || true
    else
        echo "FAIL  Admin login (no accessToken in response)"
        FAILURES=$((FAILURES + 1))
    fi
fi

if (( FAILURES > 0 )); then
    echo "SMOKE FAILED: $FAILURES check(s) failed" >&2
    exit 1
fi
echo "SMOKE PASSED"
