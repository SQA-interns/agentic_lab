#!/usr/bin/env bash
# Runs the project's checks and scanners; the only way a phase runs them (rules.md, "Output size").
#
#   bash 02_output/scripts/verify.sh <phase> [tool ...] [--filter <pattern>]
#
# Each tool writes its full output to 02_output/logs/<phase>_<tool>.log and prints one line:
# tool, exit code, key numbers, log path. Without tools, the default list runs (everything that
# needs no running stack). --filter limits tests: Surefire -Dtest pattern for be-test, test-name
# pattern for fe-test and e2e.
#
# Default tools:
#   be-build be-format be-lint be-static be-dup be-test be-mutation be-depscan
#   fe-build fe-format fe-lint fe-typecheck fe-test fe-mutation fe-dup fe-depscan
#   contracts-openapi contracts-schema contracts-sql semgrep gitleaks cloc
# Needs the running local stack (docker compose up in 02_output), not in the default list:
#   e2e
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/02_output"
BE="$OUT/backend"
FE="$OUT/frontend"
LOGS="$OUT/logs"
CONTRACTS="$OUT/docs/02_contracts"
SECRETS="$ROOT/01_input/00_general/tools/secrets.sh"
SUMMARIZE="$OUT/scripts/summarize.mjs"

# Pinned images (project/stack.md)
IMG_SEMGREP="semgrep/semgrep:1.177.0"
IMG_GITLEAKS="zricethezav/gitleaks:v8.30.1"
IMG_CLOC="aldanial/cloc:2.10"
IMG_REDOCLY="redocly/cli:2.57.0"
IMG_PLAYWRIGHT="mcr.microsoft.com/playwright:v1.63.0-noble"
IMG_POSTGRES="postgres:16.15-alpine"

# End-to-end tests run on the network of the local stack (docker compose project "registration-tanej04").
E2E_NETWORK="${E2E_NETWORK:-registration-tanej04_default}"
E2E_BASE_URL="${E2E_BASE_URL:-http://frontend:8080}"
E2E_MAILPIT_URL="${E2E_MAILPIT_URL:-http://mailpit:8025}"

DEFAULT_TOOLS="be-build be-format be-lint be-static be-dup be-test be-mutation be-depscan
  fe-build fe-format fe-lint fe-typecheck fe-test fe-mutation fe-dup fe-depscan
  contracts-openapi contracts-schema contracts-sql semgrep gitleaks cloc"

[ $# -ge 1 ] || { echo "usage: verify.sh <phase> [tool ...] [--filter <pattern>]"; exit 2; }
PHASE="$1"
shift
FILTER=""
TOOLS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --filter) FILTER="${2:-}"; shift 2 ;;
    *) TOOLS="$TOOLS $1"; shift ;;
  esac
done
[ -n "$TOOLS" ] || TOOLS="$DEFAULT_TOOLS"
mkdir -p "$LOGS"

# Docker gets Windows-style paths under Git Bash; path conversion is disabled on Docker calls only.
winpath() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
dk() { MSYS_NO_PATHCONV=1 docker "$@"; }
summary() { node "$SUMMARIZE" "$@"; }

RC=0
SUM=""
LOG=""

mvn_be() { (cd "$BE" && ./mvnw -B "$@"); }
npm_fe() { (cd "$FE" && npm run --silent "$@"); }

tool_be-build() { mvn_be -DskipTests package >"$LOG" 2>&1; RC=$?; SUM="$(grep -m1 -oE 'BUILD (SUCCESS|FAILURE)' "$LOG")"; }
tool_be-format() { mvn_be spotless:check >"$LOG" 2>&1; RC=$?; SUM="$(grep -m1 -oE 'BUILD (SUCCESS|FAILURE)' "$LOG")"; }
tool_be-lint() { mvn_be compile pmd:check >"$LOG" 2>&1; RC=$?; SUM="$(summary pmd "$BE/target/pmd.xml")"; }
tool_be-static() { mvn_be compile spotbugs:check >"$LOG" 2>&1; RC=$?; SUM="$(summary spotbugs "$BE/target/spotbugsXml.xml")"; }
tool_be-dup() { mvn_be pmd:cpd-check >"$LOG" 2>&1; RC=$?; SUM="$(summary cpd "$BE/target/cpd.xml")"; }

tool_be-test() {
  rm -rf "$BE/target/surefire-reports" "$BE/target/site/jacoco" "$BE/target/jacoco.exec"
  if [ -n "$FILTER" ]; then
    mvn_be test "-Dtest=$FILTER" -Dsurefire.failIfNoSpecifiedTests=false >"$LOG" 2>&1
  else
    mvn_be test >"$LOG" 2>&1
  fi
  RC=$?
  SUM="$(summary surefire "$BE/target/surefire-reports") $(summary jacoco "$BE/target/site/jacoco/jacoco.csv")"
}

tool_be-mutation() {
  mvn_be test-compile org.pitest:pitest-maven:mutationCoverage -DfailWhenNoMutations=false >"$LOG" 2>&1
  RC=$?
  SUM="$(summary pit "$BE/target/pit-reports/mutations.xml")"
}

tool_be-depscan() {
  (cd "$BE" && bash "$SECRETS" run NVD_API_KEY -- ./mvnw -B dependency-check:check) >"$LOG" 2>&1
  RC=$?
  SUM="$(summary depcheck "$BE/target/dependency-check-report.json")"
  # A Critical or High result fails the tool (SB-08); triage is recorded in the decisions log.
  case "$SUM" in *"critical=0 high=0"*) ;; *) RC=1 ;; esac
}

tool_fe-build() { npm_fe build >"$LOG" 2>&1; RC=$?; SUM="dist=$([ -f "$FE/dist/index.html" ] && echo ok || echo missing)"; }
tool_fe-format() { npm_fe format:check >"$LOG" 2>&1; RC=$?; SUM="unformatted=$(grep -c '^\[warn\] [^C]' "$LOG")"; }
tool_fe-lint() { (cd "$FE" && npx eslint . --max-warnings 0) >"$LOG" 2>&1; RC=$?; SUM="problems=$(grep -oE '[0-9]+ problems?' "$LOG" | grep -oE '^[0-9]+' || echo 0)"; }
tool_fe-typecheck() { npm_fe typecheck >"$LOG" 2>&1; RC=$?; SUM="errors=$(grep -c 'error TS' "$LOG")"; }

tool_fe-test() {
  rm -rf "$FE/coverage" "$FE/reports/vitest.json"
  local args=(run --coverage --reporter=default --reporter=json --outputFile.json=reports/vitest.json)
  [ -n "$FILTER" ] && args+=(-t "$FILTER")
  (cd "$FE" && npx vitest "${args[@]}") >"$LOG" 2>&1
  RC=$?
  SUM="$(summary vitest "$FE/reports/vitest.json" "$FE/coverage/coverage-summary.json")"
}

tool_fe-mutation() {
  rm -rf "$FE/reports/mutation"
  (cd "$FE" && npx stryker run) >"$LOG" 2>&1
  RC=$?
  SUM="$(summary stryker "$FE/reports/mutation/mutation.json")"
}

tool_fe-dup() {
  rm -rf "$FE/reports/jscpd"
  (cd "$FE" && npx jscpd src) >"$LOG" 2>&1
  RC=$?
  SUM="$(summary jscpd "$FE/reports/jscpd/jscpd-report.json")"
}

tool_fe-depscan() {
  local report="$LOGS/${PHASE}_fe-depscan.json"
  (cd "$FE" && npm audit --json) >"$report" 2>"$LOG"
  # npm audit exits 1 when it finds anything; the severities decide.
  SUM="$(summary audit "$report")"
  cat "$report" >>"$LOG"
  rm -f "$report"
  case "$SUM" in *"critical=0 high=0"*) RC=0 ;; *) RC=1 ;; esac
}

tool_contracts-openapi() {
  local specs=()
  local f
  if [ -d "$CONTRACTS" ]; then
    for f in "$CONTRACTS"/*.yaml "$CONTRACTS"/*.yml; do
      [ -f "$f" ] && specs+=("/spec/$(basename "$f")")
    done
  fi
  if [ ${#specs[@]} -eq 0 ]; then
    dk run --rm "$IMG_REDOCLY" --version >"$LOG" 2>&1
    RC=$?
    SUM="contracts=0 redocly=$(tail -n1 "$LOG")"
    return
  fi
  dk run --rm -v "$(winpath "$CONTRACTS"):/spec" -w /spec "$IMG_REDOCLY" lint "${specs[@]}" >"$LOG" 2>&1
  RC=$?
  SUM="contracts=${#specs[@]} $(summary redocly "$LOG")"
}

tool_contracts-schema() {
  node "$OUT/scripts/validate-contracts.mjs" "$CONTRACTS" >"$LOG" 2>&1
  RC=$?
  SUM="$(grep -m1 '^SUMMARY' "$LOG" | sed 's/^SUMMARY //')"
}

# Applies every SQL contract to a throwaway PostgreSQL container (pinned image, no stored data).
tool_contracts-sql() {
  local files=()
  local f
  for f in "$CONTRACTS"/*.sql; do
    [ -f "$f" ] && files+=("$f")
  done
  if [ ${#files[@]} -eq 0 ]; then
    RC=0
    SUM="contracts=0"
    : >"$LOG"
    return
  fi
  local name="verify-sql-$$"
  {
    dk run -d --rm --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust "$IMG_POSTGRES" >/dev/null
    local i
    for i in $(seq 1 60); do
      dk exec "$name" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && break
      sleep 1
    done
    RC=0
    for f in "${files[@]}"; do
      echo "== $(basename "$f")"
      dk exec -i "$name" psql -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -q <"$f" || RC=1
    done
    dk stop "$name" >/dev/null
  } >"$LOG" 2>&1
  SUM="contracts=${#files[@]} applied=$([ "$RC" = 0 ] && echo all || echo failed)"
}

tool_semgrep() {
  local report="${PHASE}_semgrep.json"
  dk run --rm -v "$(winpath "$OUT"):/src" -w /src "$IMG_SEMGREP" semgrep scan \
    --config p/security-audit --config p/owasp-top-ten --config p/java --config p/typescript --config p/react \
    --metrics=off --json --output "/src/logs/$report" \
    --exclude node_modules --exclude target --exclude dist --exclude coverage --exclude reports --exclude logs \
    backend/src frontend/src docs/02_contracts >"$LOG" 2>&1
  RC=$?
  SUM="$(summary semgrep "$LOGS/$report")"
  # An ERROR (High) result fails the tool; triage is recorded in the decisions log.
  case "$SUM" in "high=0 "*) ;; *) RC=1 ;; esac
}

# Working tree (tracked and untracked files that git does not ignore) and the history of HEAD only (SB-09).
tool_gitleaks() {
  local tree
  tree="$(mktemp -d)"
  (cd "$ROOT" && git ls-files -z -co --exclude-standard | xargs -0 cp --parents -t "$tree" 2>/dev/null)
  local hist="${PHASE}_gitleaks-history.json"
  local wt="${PHASE}_gitleaks-tree.json"
  {
    echo "== working tree"
    # Reviewed results only (scripts/.gitleaksignore, one fingerprint per accepted finding).
    dk run --rm -v "$(winpath "$tree"):/scan" -v "$(winpath "$LOGS"):/out" \
      -v "$(winpath "$OUT/scripts"):/ignore" "$IMG_GITLEAKS" \
      dir /scan --redact --no-banner --gitleaks-ignore-path /ignore/.gitleaksignore \
      --report-format json --report-path "/out/$wt"
    local rc_tree=$?
    echo "== history of HEAD"
    dk run --rm -v "$(winpath "$ROOT"):/repo" -v "$(winpath "$LOGS"):/out" \
      -v "$(winpath "$OUT/scripts"):/ignore" "$IMG_GITLEAKS" \
      git /repo --log-opts=HEAD --redact --no-banner --gitleaks-ignore-path /ignore/.gitleaksignore \
      --report-format json --report-path "/out/$hist"
    local rc_hist=$?
    echo "exit tree=$rc_tree history=$rc_hist"
    [ "$rc_tree" = 0 ] && [ "$rc_hist" = 0 ]
  } >"$LOG" 2>&1
  RC=$?
  rm -rf "$tree"
  SUM="leaks $(summary gitleaks "$LOGS/$wt" "$LOGS/$hist")"
}

# Lines of code per component, production and test separately (code-metrics).
tool_cloc() {
  local lists="$LOGS/.cloc"
  rm -rf "$lists"
  mkdir -p "$lists"
  (cd "$OUT" && find backend/src/main -type f 2>/dev/null) >"$lists/backend-main.txt"
  (cd "$OUT" && find backend/src/test -type f 2>/dev/null) >"$lists/backend-test.txt"
  (cd "$OUT" && find frontend/src -type f ! -name '*.test.ts' ! -name '*.test.tsx' ! -name 'test-setup.ts' 2>/dev/null) >"$lists/frontend-main.txt"
  (cd "$OUT" && { find frontend/src -type f \( -name '*.test.ts' -o -name '*.test.tsx' -o -name 'test-setup.ts' \); find frontend/tests -type f 2>/dev/null; }) >"$lists/frontend-test.txt"
  local reports=()
  local part
  RC=0
  : >"$LOG"
  for part in backend-main backend-test frontend-main frontend-test; do
    echo "== $part" >>"$LOG"
    if [ -s "$lists/$part.txt" ]; then
      dk run --rm -v "$(winpath "$OUT"):/src" -w /src "$IMG_CLOC" --json --quiet \
        "--list-file=/src/logs/.cloc/$part.txt" --out="/src/logs/${PHASE}_cloc-${part//-/}.json" >>"$LOG" 2>&1 || RC=1
    else
      echo '{"SUM":{"code":0}}' >"$LOGS/${PHASE}_cloc-${part//-/}.json"
    fi
    cat "$LOGS/${PHASE}_cloc-${part//-/}.json" >>"$LOG" 2>/dev/null
    reports+=("$LOGS/${PHASE}_cloc-${part//-/}.json")
  done
  SUM="code_lines $(summary cloc "${reports[@]}")"
  rm -rf "$lists" "${reports[@]}"
}

tool_e2e() {
  rm -f "$FE/reports/e2e.json"
  local args=(npx playwright test)
  [ -n "$FILTER" ] && args+=(--grep "$FILTER")
  [ -d "$FE/tests/e2e" ] || args+=(--pass-with-no-tests)
  MSYS_NO_PATHCONV=1 bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD -- docker run --rm --ipc=host \
    --network "$E2E_NETWORK" -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD \
    -e "E2E_BASE_URL=$E2E_BASE_URL" -e "E2E_MAILPIT_URL=$E2E_MAILPIT_URL" \
    -v "$(winpath "$FE"):/work" -w /work "$IMG_PLAYWRIGHT" "${args[@]}" >"$LOG" 2>&1
  RC=$?
  SUM="$(summary playwright "$FE/reports/e2e.json")"
}

FAILED=0
for t in $TOOLS; do
  if ! declare -F "tool_$t" >/dev/null; then
    printf '%-18s exit=%-3s %s\n' "$t" "-" "unknown tool"
    FAILED=1
    continue
  fi
  LOG="$LOGS/${PHASE}_${t}.log"
  RC=0
  SUM=""
  "tool_$t"
  printf '%-18s exit=%-3s %s  log=%s\n' "$t" "$RC" "$SUM" "${LOG#"$ROOT"/}"
  [ "$RC" = 0 ] || FAILED=1
done
exit "$FAILED"
