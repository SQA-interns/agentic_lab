#!/usr/bin/env bash
# Runs docker compose for the local stack with the KEY=value lines of the repository .env,
# without printing any value. Usage: scripts/compose.sh <compose args...>
set -euo pipefail
OUT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$(mktemp)"
chmod 600 "$ENV_FILE"
trap 'rm -f "$ENV_FILE"' EXIT
grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$OUT/../.env" | tr -d '\r' >"$ENV_FILE" || true
docker compose --project-directory "$OUT" -f "$OUT/docker-compose.yml" --env-file "$ENV_FILE" "$@"
