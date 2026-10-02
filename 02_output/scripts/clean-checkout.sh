#!/usr/bin/env bash
# Follows the READMEs from a clean checkout in a short path (phase 7, DoD-08): every documented
# command of the root README and of both component READMEs is run once.
# Run through: 02_output/scripts/verify.sh <phase> clean-checkout   (the local stack must be down)

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLONE="${CLEAN_CHECKOUT_DIR:-${TEMP:-/tmp}/rc}"
failures=0

run() { # description, command...
  local description="$1"
  shift
  printf '\n== %s\n$ %s\n' "$description" "$*"
  if "$@" >"$CLONE.out" 2>&1; then
    tail -n 3 "$CLONE.out" | cut -c1-160
    echo "PASS $description"
  else
    tail -n 25 "$CLONE.out" | cut -c1-200
    echo "FAIL $description"
    failures=$((failures + 1))
  fi
}

rm -rf "$CLONE"
printf '== clean checkout of %s into %s\n' "$(git -C "$ROOT" rev-parse --short HEAD)" "$CLONE"
git clone --quiet --no-hardlinks "$ROOT" "$CLONE" || exit 1
echo "untracked or ignored files in the checkout: $(git -C "$CLONE" status --short --ignored | wc -l | tr -d ' ')"
# The secrets are not copied (rules.md, "Secrets"): the commands that need `../.env` read the
# .env of the original checkout through the same option.
ENV_FILE="$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))/.env"
COMPOSE_FILE="$(cd "$CLONE/02_output" && (pwd -W 2>/dev/null || pwd))/docker-compose.yml"

cd "$CLONE/02_output/backend" || exit 1
run "backend README: build" ./mvnw -B -ntp -DskipTests package
run "backend README: check" ./mvnw -B -ntp compile spotless:check pmd:check spotbugs:check
run "backend README: test" ./mvnw -B -ntp verify

cd "$CLONE/02_output/frontend" || exit 1
run "frontend README: npm ci" npm ci --no-audit --no-fund
run "frontend README: build" npm run build
run "frontend README: check" npm run check
run "frontend README: test" npm test

cd "$CLONE/02_output" || exit 1
compose() { MSYS_NO_PATHCONV=1 docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
run "root README: quick start, start the stack (also the run command of the backend README)" \
  compose up -d --build --wait
run "root README: all four services healthy" \
  sh -c "[ \"\$(MSYS_NO_PATHCONV=1 docker compose --env-file '$ENV_FILE' -f '$COMPOSE_FILE' ps --format '{{.Health}}' | grep -c '^healthy\$')\" = 4 ]"
run "root README: registration form answers" curl -sf -o /dev/null http://127.0.0.1:8080/
run "root README: API answers through the frontend" curl -sf -o /dev/null http://127.0.0.1:8080/api/options
run "root README: Mailpit answers" curl -sf -o /dev/null http://127.0.0.1:8025/

cd "$CLONE/02_output/frontend" || exit 1
printf '\n== frontend README: run (development server with /api forwarded to the stack)\n$ npm run dev\n'
npm run dev >"$CLONE.dev" 2>&1 &
dev_pid=$!
for _ in $(seq 1 30); do
  curl -sf -o /dev/null http://127.0.0.1:5173/ && break
  sleep 1
done
run "frontend README: development server serves the page" curl -sf -o /dev/null http://127.0.0.1:5173/
run "frontend README: development server forwards /api" curl -sf -o /dev/null http://127.0.0.1:5173/api/form-config
kill "$dev_pid" 2>/dev/null
# On Windows the node process of the development server outlives its npm parent.
command -v taskkill >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep ':5173 .*LISTENING' | awk '{print $NF}' | sort -u \
  | while read -r pid; do taskkill //F //PID "$pid" >/dev/null 2>&1; done

cd "$CLONE/02_output" || exit 1
run "root README: stop the stack" compose down

cd "$ROOT" || exit 1
rm -rf "$CLONE" "$CLONE.out" "$CLONE.dev"
printf '\n== clean checkout finished: %s failed step(s)\n' "$failures"
exit "$failures"
