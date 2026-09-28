#!/usr/bin/env bash
# Generates a local-only .env with random credentials (never committed, never for production).
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  echo ".env already exists; leaving it unchanged"
  exit 0
fi
rand() { head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24; }
cat > .env <<ENV
POSTGRES_PASSWORD=$(rand)
APP_ORGANIZER_USERNAME=organizer
APP_ORGANIZER_PASSWORD=$(rand)
ENV
chmod 600 .env
echo "Wrote .env (local only)"
