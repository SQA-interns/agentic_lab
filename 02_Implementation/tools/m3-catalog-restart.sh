#!/usr/bin/env bash
# M3 / P-05 runtime demonstration: switch the mounted catalog file, recreate ONLY the backend
# container from the SAME image (no rebuild), verify the new catalog in API + browser, then
# restore the default catalog. Evidence goes to 03_Metrics/evidence/runtime/m3/.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=../03_Metrics/evidence/runtime/m3
mkdir -p "$OUT"
export PATH="$HOME/.nvm/versions/node/v22.23.3/bin:$PATH"

wait_ready() {
  for _ in $(seq 1 60); do
    if [[ "$(docker inspect -f '{{.State.Health.Status}}' conference-backend-1 2>/dev/null)" == healthy ]]; then return 0; fi
    sleep 2
  done
  echo "backend not healthy" >&2; return 1
}

{
  echo "## before: image and catalog"
  docker inspect -f 'backend image={{.Image}}' conference-backend-1
  curl -s localhost:8088/api/form-config
  echo
  echo "## switching catalog to config/fixtures/catalog-changed.yaml and recreating backend (no build)"
  APP_CATALOG_FILE=./config/fixtures/catalog-changed.yaml docker compose up -d --no-build --force-recreate backend
  wait_ready
  docker inspect -f 'backend image={{.Image}}' conference-backend-1
  curl -s localhost:8088/api/form-config
  echo
} > "$OUT/catalog-switch.txt" 2>&1

set +e
(cd frontend && npx playwright test --grep @catalog-changed --reporter=list) > "$OUT/playwright-catalog-changed.txt" 2>&1
RESULT=$?
set -e

{
  echo "## restoring default catalog"
  docker compose up -d --no-build --force-recreate backend
  wait_ready
  docker inspect -f 'backend image={{.Image}}' conference-backend-1
  curl -s localhost:8088/api/form-config
  echo
} >> "$OUT/catalog-switch.txt" 2>&1
tail -5 "$OUT/playwright-catalog-changed.txt"
exit $RESULT
