#!/usr/bin/env bash
# Runs checks and scanners; full output goes to 02_output/logs/<phase>_<tool>.log,
# one summary line per tool goes to stdout (rules.md, "Output size").
#
# Usage: 02_output/scripts/verify.sh <phase> [tool ...]
#   no tool given = every tool in ALL_TOOLS
# Exit code: number of tools that failed.

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/02_output"
LOGS="$OUT/logs"
# Docker Desktop on Windows needs a Windows-style path for bind mounts, and Git Bash
# must not rewrite the container-side paths (MSYS_NO_PATHCONV on the docker calls only).
ROOT_HOST="$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))"

SEMGREP_IMAGE=semgrep/semgrep:1.177.0
GITLEAKS_IMAGE=zricethezav/gitleaks:v8.30.1
CLOC_IMAGE=aldanial/cloc:2.10
REDOCLY_IMAGE=redocly/cli:2.57.0
POSTGRES_IMAGE=postgres:16.15-alpine
PLAYWRIGHT_IMAGE=mcr.microsoft.com/playwright:v1.63.0-noble

ALL_TOOLS="backend-build backend-check backend-test backend-deps backend-depscan \
frontend-build frontend-check frontend-test frontend-audit semgrep gitleaks cloc contracts"

PHASE="${1:?usage: verify.sh <phase> [tool ...]}"
shift
TOOLS="${*:-$ALL_TOOLS}"
mkdir -p "$LOGS"

mvnw() { (cd "$OUT/backend" && ./mvnw -B -ntp "$@"); }
npmr() { (cd "$OUT/frontend" && npm "$@"); }

# Last line of the log that matches the pattern, or "-".
key() { sed 's/\[[0-9;]*m//g' "$2" | grep -E "$1" | tail -n 1 | tr -s ' ' | cut -c1-110 | grep . || echo "-"; }

run_tool() {
  local tool="$1" log="$LOGS/${PHASE}_$1.log" rc=0 numbers="-"
  case "$tool" in
    backend-build)
      mvnw -DskipTests package >"$log" 2>&1 || rc=$?
      numbers="$(key 'BUILD (SUCCESS|FAILURE)' "$log")" ;;
    backend-check)
      mvnw compile spotless:check pmd:check spotbugs:check >"$log" 2>&1 || rc=$?
      numbers="$(key 'BUILD (SUCCESS|FAILURE)|BugInstance size|PMD Failure' "$log")" ;;
    backend-test)
      # TEST_FILTER limits the run to matching test classes (per-story runs in phase 4).
      mvnw verify ${TEST_FILTER:+-Dtest="$TEST_FILTER" -Dsurefire.failIfNoSpecifiedTests=false} >"$log" 2>&1 || rc=$?
      numbers="$(grep -E 'Tests run:.*Fail|No tests to run' "$log" | tail -n 1 | tr -s ' ' | cut -c1-110)" ;;
    backend-deps)
      mvnw dependency:list -DincludeScope=test -Dsort=true >"$log" 2>&1 || rc=$?
      numbers="artifacts=$(grep -cE ':(compile|runtime|test|provided)' "$log")" ;;
    backend-depscan)
      # The key is passed to the tool without being shown (rules.md, "Secrets").
      (export NVD_API_KEY="$(sed -n 's/^NVD_API_KEY=//p' "$ROOT/.env" | tr -d '\r')"
       mvnw org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7) >"$log" 2>&1 || rc=$?
      numbers="$(key 'BUILD (SUCCESS|FAILURE)|CVSS score' "$log")" ;;
    frontend-build)
      npmr run build >"$log" 2>&1 || rc=$?
      numbers="$(key 'built in|error' "$log")" ;;
    frontend-check)
      npmr run check >"$log" 2>&1 || rc=$?
      numbers="$(key 'problems?|error|All matched files' "$log")" ;;
    frontend-test)
      npmr test >"$log" 2>&1 || rc=$?
      numbers="$(key 'Tests +[0-9]|No test files' "$log")" ;;
    frontend-audit)
      npmr audit --audit-level=high >"$log" 2>&1 || rc=$?
      numbers="$(key 'vulnerabilit' "$log")" ;;
    semgrep)
      MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT_HOST/02_output:/src" "$SEMGREP_IMAGE" \
        semgrep scan --config p/default --error --metrics=off \
        --exclude logs --exclude node_modules --exclude target --exclude dist --exclude coverage --exclude reports /src >"$log" 2>&1 || rc=$?
      numbers="$(key 'Findings: |findings' "$log")" ;;
    gitleaks)
      # History of the current branch only (D-04): other branches belong to other runs.
      MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT_HOST:/repo" "$GITLEAKS_IMAGE" \
        detect --source /repo --no-banner --redact --verbose --log-opts="HEAD" >"$log" 2>&1 || rc=$?
      numbers="$(key 'leaks found|no leaks' "$log")" ;;
    contracts)
      # Every contract in docs/02_contracts is read by a parser: OpenAPI by Redocly,
      # JSON schemas and their documents by ajv, the SQL schema by PostgreSQL itself.
      {
        echo "== openapi (redocly lint)"
        MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT_HOST/02_output/docs/02_contracts:/spec" "$REDOCLY_IMAGE"           lint /spec/openapi.yaml || rc=1
        echo "== json (ajv)"
        node "$OUT/scripts/validate-contracts.mjs" || rc=1
        echo "== sql (postgres)"
        MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT_HOST/02_output/docs/02_contracts:/c:ro" "$POSTGRES_IMAGE"           su postgres -c 'initdb -D /tmp/pg >/dev/null && pg_ctl -D /tmp/pg -w -o "-c listen_addresses=" start >/dev/null             && psql -v ON_ERROR_STOP=1 -q -f /c/registration-schema.sql && echo "sql tables=$(psql -At -c "select count(*) from pg_tables where schemaname = current_schema()")"' || rc=1
      } >"$log" 2>&1
      numbers="$(grep -cE 'Woohoo|is valid' "$log") openapi ok; $(key 'json contracts:' "$log"); $(key 'sql tables=' "$log")" ;;
    e2e)
      # Not in ALL_TOOLS: needs the running local stack. Playwright runs in its own container on
      # the network of the stack (D-15); the organizer credentials are passed without being
      # shown (rules.md, "Secrets").
      (export E2E_ORGANIZER_USERNAME="$(sed -n 's/^ORGANIZER_USERNAME=//p' "$ROOT/.env" | tr -d '')"
       export E2E_ORGANIZER_PASSWORD="$(sed -n 's/^ORGANIZER_PASSWORD=//p' "$ROOT/.env" | tr -d '')"
       MSYS_NO_PATHCONV=1 docker run --rm --network "${E2E_NETWORK:-registration_default}"          -v "$ROOT_HOST/02_output/frontend:/work" -w /work          -e E2E_BASE_URL="${E2E_BASE_URL:-http://frontend:8080}"          -e E2E_MAILPIT_URL="${E2E_MAILPIT_URL:-http://mailpit:8025}"          -e E2E_ORGANIZER_USERNAME -e E2E_ORGANIZER_PASSWORD -e CI=1          "$PLAYWRIGHT_IMAGE" node node_modules/@playwright/test/cli.js test) >"$log" 2>&1 || rc=$?
      numbers="$(key '[0-9]+ (passed|failed)' "$log")" ;;
    cloc)
      MSYS_NO_PATHCONV=1 docker run --rm -v "$ROOT_HOST/02_output:/tmp" "$CLOC_IMAGE" \
        --exclude-dir=node_modules,target,dist,logs,docs,coverage,reports . >"$log" 2>&1 || rc=$?
      numbers="$(key '^SUM' "$log")" ;;
    *)
      echo "unknown tool" >"$log"; rc=2 ;;
  esac
  echo "$tool | exit=$rc | $numbers | 02_output/logs/${PHASE}_$tool.log"
  return $rc
}

failed=0
for t in $TOOLS; do
  run_tool "$t" || failed=$((failed + 1))
done
exit $failed
