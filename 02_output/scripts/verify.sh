#!/usr/bin/env bash
# Runs the project's checks and scanners. Each tool writes its full output to
# 02_output/logs/<phase>_<tool>.log; one summary line per tool goes to stdout.
#
# Usage: verify.sh <phase> [tool ...]     (no tool = all tools)
# Tools: contracts backend-build backend-format backend-test backend-spotbugs backend-pmd
#        backend-mutation backend-depscan frontend-build frontend-format
#        frontend-lint frontend-typecheck frontend-test frontend-e2e
#        frontend-mutation frontend-duplication frontend-depscan
#        semgrep gitleaks cloc
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/02_output"
LOGS="$OUT/logs"
PHASE="${1:?usage: verify.sh <phase> [tool ...]}"
shift
mkdir -p "$LOGS"

# Toolchain: host JDK 21 (D-03) and the pinned Node.js from nvm (D-02).
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
NODE_BIN="${NODE_BIN:-$HOME/.nvm/versions/node/v24.13.0/bin}"
[ -d "$NODE_BIN" ] && export PATH="$NODE_BIN:$PATH"

ALL_TOOLS=(contracts backend-build backend-format backend-test backend-spotbugs backend-pmd
  backend-mutation backend-depscan frontend-build frontend-format frontend-lint
  frontend-typecheck frontend-test frontend-e2e frontend-mutation
  frontend-duplication frontend-depscan semgrep gitleaks cloc)
TOOLS=("$@")
[ ${#TOOLS[@]} -eq 0 ] && TOOLS=("${ALL_TOOLS[@]}")

MVN=("$OUT/backend/mvnw" -B -ntp -f "$OUT/backend/pom.xml")
STATUS=0

summary() { # tool exit log key-numbers
  printf '%-22s exit=%-3s %-60s %s\n' "$1" "$2" "$4" "${3#"$ROOT"/}"
  [ "$2" -ne 0 ] && STATUS=1
  return 0
}

first() { grep -Eo "$1" "$2" | tail -1; }

run_tool() {
  local t="$1" log="$LOGS/${PHASE}_$1.log" rc keys=""
  case "$t" in
    contracts)
      # D-18: redocly/cli for OpenAPI, host python3-jsonschema for JSON Schema,
      # the pinned postgres image for the SQL contract.
      local c="$OUT/docs/02_contracts" pg="contracts-pg-$$" r1 r2 r3
      {
        echo "== openapi (redocly lint)"
        docker run --rm -u "$(id -u):$(id -g)" -v "$c:/spec:ro" redocly/cli:2.57.0 lint /spec/openapi.yaml; r1=$?
        echo "== json schemas"
        python3 "$OUT/scripts/validate-contracts.py" "$c"; r2=$?
        echo "== database.sql (postgres:16.15-alpine)"
        docker run -d --rm --name "$pg" -e POSTGRES_PASSWORD=contracts postgres:16.15-alpine >/dev/null
        for _ in $(seq 1 30); do docker exec "$pg" pg_isready -U postgres -q && break; sleep 1; done
        sleep 1
        docker exec -i "$pg" psql -U postgres -v ON_ERROR_STOP=1 -q <"$c/database.sql"; r3=$?
        docker stop "$pg" >/dev/null
      } >"$log" 2>&1
      rc=$(( r1 || r2 || r3 ))
      keys="openapi=$r1 schemas=$r2 sql=$r3" ;;
    backend-build)
      "${MVN[@]}" -DskipTests package >"$log" 2>&1; rc=$?
      keys="$(first 'BUILD (SUCCESS|FAILURE)' "$log")" ;;
    backend-format)
      "${MVN[@]}" spotless:check >"$log" 2>&1; rc=$?
      keys="violations=$(grep -c 'The following files had format violations' "$log")" ;;
    backend-test)
      "${MVN[@]}" verify -Dspotbugs.skip -Dpmd.skip -Dcpd.skip >"$log" 2>&1; rc=$?
      keys="$(grep -E 'Tests run: [0-9]+, Failures' "$log" | grep -v ' -- in ' | tail -1 | sed 's/^\[[A-Z]*\] //')"
      local csv="$OUT/backend/target/site/jacoco/jacoco.csv"
      if [ -f "$csv" ]; then
        keys="$keys; $(awk -F, 'NR>1{lm+=$8;lc+=$9;bm+=$6;bc+=$7}END{if(lm+lc>0)printf "line=%.1f%% branch=%.1f%%",100*lc/(lm+lc),(bm+bc)?100*bc/(bm+bc):100}' "$csv")"
      fi ;;
    backend-spotbugs)
      "${MVN[@]}" -DskipTests compile spotbugs:check >"$log" 2>&1; rc=$?
      keys="$(first 'Total bugs: [0-9]+' "$log")"; keys="${keys:-Total bugs: 0}" ;;
    backend-pmd)
      "${MVN[@]}" -DskipTests compile pmd:check pmd:cpd-check >"$log" 2>&1; rc=$?
      keys="pmd=$(grep -cE 'PMD (Failure|Warning)' "$log") cpd=$(grep -cE 'CPD (Failure|Warning)' "$log")" ;;
    backend-mutation)
      "${MVN[@]}" test-compile org.pitest:pitest-maven:mutationCoverage >"$log" 2>&1; rc=$?
      keys="$(grep -E 'Generated [0-9]+ mutations Killed' "$log" | tail -1 | sed 's/^\[[A-Z]*\] >> //')" ;;
    backend-depscan)
      # No NVD_API_KEY (D-05): use the local NVD cache without updating it.
      local key=""
      [ -f "$ROOT/.env" ] && key="$(sed -n 's/^NVD_API_KEY=//p' "$ROOT/.env" | tr -d '\r')"
      if [ -n "$key" ]; then
        NVD_API_KEY="$key" "${MVN[@]}" -DskipTests package dependency-check:check -DnvdApiKeyEnvironmentVariable=NVD_API_KEY >"$log" 2>&1; rc=$?
      else
        "${MVN[@]}" -DskipTests package dependency-check:check -DautoUpdate=false >"$log" 2>&1; rc=$?
      fi
      local js="$OUT/backend/target/dependency-check-report.json"
      [ -f "$js" ] && keys="$(python3 - "$js" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); c={"critical":0,"high":0,"medium":0,"low":0}
for dep in d.get("dependencies",[]):
  for v in dep.get("vulnerabilities",[]):
    s=(v.get("cvssv3") or {}).get("baseScore") or (v.get("cvssv2") or {}).get("score") or 0
    k="critical" if s>=9 else "high" if s>=7 else "medium" if s>=4 else "low"; c[k]+=1
print(" ".join(f"{k}={v}" for k,v in c.items()))
PY
)" ;;
    frontend-build)
      (cd "$OUT/frontend" && npm run -s build) >"$log" 2>&1; rc=$?
      keys="$(first 'built in [0-9.]+m?s' "$log")" ;;
    frontend-format)
      (cd "$OUT/frontend" && npx prettier --check .) >"$log" 2>&1; rc=$?
      keys="unformatted=$(grep -c '^\[warn\] [^C]' "$log")" ;;
    frontend-lint)
      (cd "$OUT/frontend" && npx eslint . --max-warnings 0) >"$log" 2>&1; rc=$?
      keys="$(first '[0-9]+ problems? \([0-9]+ errors?, [0-9]+ warnings?\)' "$log")"; keys="${keys:-0 problems}" ;;
    frontend-typecheck)
      (cd "$OUT/frontend" && npx tsc --noEmit) >"$log" 2>&1; rc=$?
      keys="errors=$(grep -c 'error TS' "$log")" ;;
    frontend-test)
      (cd "$OUT/frontend" && npx vitest run --coverage --passWithNoTests) >"$log" 2>&1; rc=$?
      keys="$(grep -E '^ +Tests ' "$log" | tail -1 | sed 's/^ *//')"
      local cs="$OUT/frontend/coverage/coverage-summary.json"
      [ -f "$cs" ] && keys="$keys; $(python3 -c 'import json,sys;t=json.load(open(sys.argv[1]))["total"];print("line=%s%% branch=%s%%"%(t["lines"]["pct"],t["branches"]["pct"]))' "$cs")" ;;
    frontend-e2e)
      # KP-08: Playwright starts its own fresh stack (see playwright.config.ts).
      (cd "$OUT/frontend" && npx playwright test --pass-with-no-tests) >"$log" 2>&1; rc=$?
      keys="$(grep -E '^ +[0-9]+ (passed|failed|flaky|skipped)' "$log" | sed 's/^ *//;s/ (.*//' | tr '\n' ' ')" ;;
    frontend-mutation)
      (cd "$OUT/frontend" && npx stryker run) >"$log" 2>&1; rc=$?
      keys="$(grep -E 'Final mutation score' "$log" | tail -1)" ;;
    frontend-duplication)
      (cd "$OUT/frontend" && npx jscpd src --silent --exitCode 0) >"$log" 2>&1; rc=$?
      keys="$(grep -Eo 'Found [0-9]+ clones?\.?|Duplicated lines: .*' "$log" | tr '\n' ' ')"; keys="${keys:-$(tail -1 "$log")}" ;;
    frontend-depscan)
      # Gate = shipped (production) dependencies; the full audit incl. dev tooling
      # is kept for the record (D-10).
      (cd "$OUT/frontend" && npm audit --json) >"${log%.log}-all.json" 2>&1
      (cd "$OUT/frontend" && npm audit --omit=dev --json) >"$log" 2>&1; rc=$?
      local cnt='import json,sys;v=json.load(open(sys.argv[1]))["metadata"]["vulnerabilities"];print(" ".join(f"{k}={v[k]}" for k in ("critical","high","moderate","low")))'
      keys="shipped: $(python3 -c "$cnt" "$log" 2>/dev/null); all: $(python3 -c "$cnt" "${log%.log}-all.json" 2>/dev/null)" ;;
    semgrep)
      docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$ROOT:/src" -w /src semgrep/semgrep:1.177.0 \
        semgrep scan --metrics=off --config p/default --config p/java --config p/typescript \
        --config p/react --config p/nginx --config p/dockerfile --config p/secrets \
        --exclude node_modules --exclude target --exclude dist --exclude 02_output/logs \
        --json --output /src/02_output/logs/${PHASE}_semgrep.json 02_output >"$log" 2>&1; rc=$?
      local sj="$LOGS/${PHASE}_semgrep.json"
      [ -f "$sj" ] && keys="$(python3 -c 'import json,sys,collections;r=json.load(open(sys.argv[1]))["results"];c=collections.Counter(x["extra"]["severity"] for x in r);print("ERROR=%d WARNING=%d INFO=%d"%(c["ERROR"],c["WARNING"],c["INFO"]))' "$sj")" ;;
    gitleaks)
      # Only this run's commits: history before the start commit belongs to
      # earlier runs (D-11).
      local START_COMMIT
      START_COMMIT="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["startCommit"])' "$ROOT/03_statistics/run-log.json")"
      docker run --rm -u "$(id -u):$(id -g)" -v "$ROOT:/repo" zricethezav/gitleaks:v8.30.1 \
        git /repo --redact --no-banner -v --log-opts="$START_COMMIT..HEAD" >"$log" 2>&1; rc=$?
      keys="$(grep -Eo 'no leaks found|leaks found: [0-9]+' "$log" | tail -1)" ;;
    cloc)
      docker run --rm -u "$(id -u):$(id -g)" -v "$OUT:/src:ro" -w /src aldanial/cloc:2.10 \
        --exclude-dir=node_modules,target,dist,logs,coverage,reports,test-results,playwright-report,.stryker-tmp \
        --by-file-by-lang --quiet . >"$log" 2>&1; rc=$?
      keys="$(grep -E '^SUM:' "$log" | tail -1 | tr -s ' ')" ;;
    *) echo "unknown tool: $t" >&2; STATUS=1; return ;;
  esac
  summary "$t" "$rc" "$log" "$keys"
}

for t in "${TOOLS[@]}"; do run_tool "$t"; done
exit $STATUS
