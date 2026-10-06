#!/usr/bin/env bash
# Runtime demonstration on the local stack (DoD-06, DoD-P01..DoD-P04, NFR-02, NFR-04).
# Run from anywhere; writes 02_output/logs/06_runtime-demo.log. Secrets reach containers only via secrets.sh.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/02_output"
SECRETS="$ROOT/01_input/00_general/tools/secrets.sh"
KEYS=POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS
COMPOSE=(docker compose -f "$OUT/docker-compose.yml")
WORK="$OUT/logs/06_runtime-demo"
LOG="$OUT/logs/06_runtime-demo.log"
mkdir -p "$WORK"
exec > >(tee "$LOG") 2>&1

demo() {
  bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
    docker run --rm --add-host host.docker.internal:host-gateway --user "$(id -u):$(id -g)" \
    -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD -e ORGANIZER_EMAILS \
    -v "$OUT/scripts:/scripts:ro" -v "$WORK:/work" mcr.microsoft.com/playwright:v1.63.0-noble \
    node /scripts/runtime-demo.mjs "$1" /work/state.json
}

stored() {
  echo "== database rows (DoD-P02)"
  bash "$SECRETS" run $KEYS -- "${COMPOSE[@]}" exec -T postgres \
    psql -U registration -d registration -At -c \
    "SELECT type, id, json_copy_file FROM registration WHERE email LIKE 'demo.%' ORDER BY registered_at"
  echo "== JSON copies on the volume (DoD-P02)"
  bash "$SECRETS" run $KEYS -- "${COMPOSE[@]}" exec -T backend sh -c 'ls /var/lib/registration/json-copies | tail -n 5'
}

health() {
  echo "== health (NFR-04)"
  bash "$SECRETS" run $KEYS -- "${COMPOSE[@]}" ps --format '{{.Service}} {{.Status}}'
}

date -u +"start %Y-%m-%dT%H:%M:%SZ"
health
demo register; r1=$?
stored
echo "== recreate every container (NFR-02): docker compose down, then up"
bash "$SECRETS" run $KEYS -- "${COMPOSE[@]}" down
bash "$SECRETS" run $KEYS -- "${COMPOSE[@]}" up -d --wait
health
stored
demo check; r2=$?
date -u +"end %Y-%m-%dT%H:%M:%SZ"
[ $r1 -eq 0 ] && [ $r2 -eq 0 ] && echo "DEMO PASS" || echo "DEMO FAIL"
