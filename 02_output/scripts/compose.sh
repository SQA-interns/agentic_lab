#!/usr/bin/env bash
# Runs docker compose for the local stack with the keys it needs from the repository-root .env,
# exported without printing them (rules.md "Secrets"). Works with any .env layout that has
# KEY=value lines. Usage: 02_output/scripts/compose.sh [-p project] <compose args>, e.g. "up -d --build --wait".
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
for key in POSTGRES_PASSWORD ORGANIZER_EMAILS ORGANIZER_USERNAME ORGANIZER_PASSWORD; do
  value="$(sed -n "s/^${key}=//p" "$ROOT/.env" | tail -1 | tr -d '\r')"
  export "$key=$value"
done
exec docker compose -f "$ROOT/02_output/docker-compose.yml" "$@"
