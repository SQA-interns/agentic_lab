#!/usr/bin/env bash
# Runs the project's checks and scanners; one summary line per tool.
# Usage: 02_output/scripts/verify.sh <phase> [tool,tool,...|default|all] [test-filter]
#   phase        log prefix, e.g. 00, 04, 06  -> 02_output/logs/<phase>_<tool>.log
#   tools        comma-separated names (see TOOLS below); "default" omits tools that need a running stack
#   test-filter  backend: surefire -Dtest pattern; frontend: vitest file filter
# Exit code: 0 when every selected tool exited 0, otherwise 1.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/02_output"
BE="$OUT/backend"
FE="$OUT/frontend"
LOGS="$OUT/logs"
# SECRETS_SH: a clean checkout without .env uses the original repository's secrets.sh.
SECRETS="${SECRETS_SH:-$ROOT/01_input/00_general/tools/secrets.sh}"
NODE_BIN="${NODE_BIN:-$HOME/.nvm/versions/node/v24.13.0/bin}"
[ -d "$NODE_BIN" ] && export PATH="$NODE_BIN:$PATH"
export NPM_CONFIG_UPDATE_NOTIFIER=false

SEMGREP_IMAGE="semgrep/semgrep:1.177.0"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1"
CLOC_IMAGE="aldanial/cloc:2.10"
REDOCLY_IMAGE="redocly/cli:2.57.0"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.63.0-noble"

DEFAULT_TOOLS="be-build,be-test,be-format,be-lint,be-static,fe-build,fe-test,fe-format,fe-lint,fe-typecheck,fe-duplication,contracts,semgrep,gitleaks,cloc"
SLOW_TOOLS="be-mutation,fe-mutation,be-depscan,fe-depscan"
STACK_TOOLS="e2e"

PHASE="${1:?usage: verify.sh <phase> [tools] [test-filter]}"
SELECTION="${2:-default}"
FILTER="${3:-}"
case "$SELECTION" in
  default) SELECTION="$DEFAULT_TOOLS" ;;
  all) SELECTION="$DEFAULT_TOOLS,$SLOW_TOOLS" ;;
esac
mkdir -p "$LOGS"
FAILED=0
DOCKER_USER="$(id -u):$(id -g)"

mvnw() { (cd "$BE" && ./mvnw -B -ntp "$@"); }
npmrun() { (cd "$FE" && npm run -s "$@"); }

# --- key-number extractors (read a log, print "k=v ...") ---
maven_status() { grep -qE 'BUILD SUCCESS' "$1" && echo "build=ok" || echo "build=fail"; }
surefire_totals() {
  grep -E '^\[(INFO|ERROR|WARNING)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$1" | tail -1 |
    sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+)/tests=\1 failures=\2 errors=\3 skipped=\4/'
}
jacoco_totals() {
  local csv="$BE/target/site/jacoco/jacoco.csv"
  [ -f "$csv" ] || { echo "coverage=none"; return; }
  awk -F, 'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
    END {printf "line=%.1f%% branch=%.1f%%", (lm+lc)?100*lc/(lm+lc):0, (bm+bc)?100*bc/(bm+bc):0}' "$csv"
}
vitest_totals() {
  local t f
  t=$(grep -E '^ +Tests +' "$1" | tail -1 | sed -E 's/\x1b\[[0-9;]*m//g')
  f=$(echo "$t" | grep -oE '[0-9]+ failed' | grep -oE '[0-9]+' || true)
  echo "tests=\"$(echo "$t" | sed -E 's/^ +Tests +//; s/ +\(.*//')\" failed=${f:-0}"
}
vitest_coverage() {
  local s="$FE/coverage/coverage-summary.json"
  [ -f "$s" ] || { echo "coverage=none"; return; }
  node -e 'const t=require(process.argv[1]).total;console.log(`line=${t.lines.pct}% branch=${t.branches.pct}%`)' "$s"
}

run_tool() {
  local tool="$1" log="$LOGS/${PHASE}_$1.log" rc numbers=""
  : >"$log"
  case "$tool" in
    be-build)
      mvnw -DskipTests package >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log")" ;;
    be-test)
      if [ -n "$FILTER" ]; then
        mvnw test -Dtest="$FILTER" -Dsurefire.failIfNoSpecifiedTests=false >"$log" 2>&1; rc=$?
      else
        mvnw test >"$log" 2>&1; rc=$?
      fi
      numbers="$(surefire_totals "$log") $(jacoco_totals)" ;;
    be-format)
      mvnw spotless:check >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log") violations=$(grep -c 'The following files had format violations' "$log")" ;;
    be-lint)
      mvnw -DskipTests compile pmd:check pmd:cpd-check >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log") pmd=$(grep -cE '^\[(ERROR|WARNING)\] PMD Failure' "$log") cpd=$(grep -cE 'CPD Failure|Found a [0-9]+ line' "$log")" ;;
    be-static)
      mvnw -DskipTests compile spotbugs:check >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log") bugs=$(grep -oE 'BugInstance size is [0-9]+' "$log" | grep -oE '[0-9]+$' | tail -1)" ;;
    be-mutation)
      mvnw test-compile org.pitest:pitest-maven:mutationCoverage >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log") $(grep -E '>> Generated [0-9]+ mutations' "$log" | tail -1 | sed -E 's/.*Generated ([0-9]+) mutations Killed ([0-9]+) \(([0-9]+)%\).*/mutations=\1 killed=\2 score=\3%/')" ;;
    be-depscan)
      (cd "$BE" && bash "$SECRETS" run NVD_API_KEY -- ./mvnw -B -ntp org.owasp:dependency-check-maven:check) >"$log" 2>&1; rc=$?
      numbers="$(maven_status "$log") $(node -e '
        const fs=require("fs"),p=process.argv[1];if(!fs.existsSync(p)){console.log("report=none");process.exit()}
        const c={critical:0,high:0,medium:0,low:0};
        for(const d of JSON.parse(fs.readFileSync(p)).dependencies||[])for(const v of d.vulnerabilities||[]){
          const s=(v.cvssv3?.baseSeverity||v.cvssv2?.severity||v.severity||"low").toLowerCase();c[s in c?s:"low"]++}
        console.log(Object.entries(c).map(([k,v])=>k+"="+v).join(" "))' "$BE/target/dependency-check-report.json")" ;;
    fe-build)
      npmrun build >"$log" 2>&1; rc=$?
      numbers="build=$([ $rc -eq 0 ] && echo ok || echo fail)" ;;
    fe-test)
      (cd "$FE" && npx --no-install vitest run --coverage $FILTER) >"$log" 2>&1; rc=$?
      numbers="$(vitest_totals "$log") $(vitest_coverage)" ;;
    fe-format)
      (cd "$FE" && npx --no-install prettier --check .) >"$log" 2>&1; rc=$?
      numbers="unformatted=$(grep -c '^\[warn\] [^C]' "$log")" ;;
    fe-lint)
      (cd "$FE" && npx --no-install eslint . --max-warnings 0) >"$log" 2>&1; rc=$?
      numbers="$(grep -oE '[0-9]+ problems? \([0-9]+ errors?, [0-9]+ warnings?\)' "$log" | tail -1)"
      [ -z "$numbers" ] && numbers="problems=0" ;;
    fe-typecheck)
      (cd "$FE" && npx --no-install tsc --noEmit) >"$log" 2>&1; rc=$?
      numbers="errors=$(grep -cE 'error TS[0-9]+' "$log")" ;;
    fe-duplication)
      (cd "$FE" && npx --no-install jscpd src --reporters console,json --output reports/jscpd) >"$log" 2>&1; rc=$?
      numbers="$(node -e 'const fs=require("fs"),p=process.argv[1];if(!fs.existsSync(p)){console.log("report=none");process.exit()}
        const t=JSON.parse(fs.readFileSync(p)).statistics.total;console.log(`clones=${t.clones} duplicated=${t.percentage}%`)' "$FE/reports/jscpd/jscpd-report.json")" ;;
    fe-mutation)
      (cd "$FE" && npx --no-install stryker run) >"$log" 2>&1; rc=$?
      numbers="$(node -e 'const fs=require("fs"),p=process.argv[1];if(!fs.existsSync(p)){console.log("report=none");process.exit()}
        const c={};for(const f of Object.values(JSON.parse(fs.readFileSync(p)).files))for(const m of f.mutants)c[m.status]=(c[m.status]||0)+1;
        const det=(c.Killed||0)+(c.Timeout||0),valid=det+(c.Survived||0)+(c.NoCoverage||0);
        console.log(`mutants=${valid} killed=${c.Killed||0} survived=${c.Survived||0} nocov=${c.NoCoverage||0} score=${valid?(100*det/valid).toFixed(1):0}%`)' "$FE/reports/mutation/mutation.json")" ;;
    fe-depscan)
      # Exit code fails on High or Critical only; all levels are counted.
      (cd "$FE" && npm audit --json --audit-level=high) >"$log" 2>&1; rc=$?
      numbers="$(node -e 'const v=JSON.parse(require("fs").readFileSync(process.argv[1])).metadata.vulnerabilities;
        console.log(`critical=${v.critical} high=${v.high} medium=${v.moderate} low=${v.low}`)' "$log" 2>/dev/null || echo "report=unparsed")" ;;
    contracts)
      local specs=() schemas=()
      [ -d "$OUT/docs/02_contracts" ] && while IFS= read -r f; do specs+=("$f"); done < <(cd "$OUT" && find docs/02_contracts -name '*openapi*.yaml' | sort)
      [ -d "$OUT/docs/02_contracts" ] && while IFS= read -r f; do schemas+=("$f"); done < <(cd "$OUT" && find docs/02_contracts -name '*.schema.json' | sort)
      if [ ${#specs[@]} -eq 0 ] && [ ${#schemas[@]} -eq 0 ]; then
        echo "no contracts yet" >"$log"; rc=0; numbers="openapi=0 schemas=0"
      else
        rc=0
        if [ ${#specs[@]} -gt 0 ]; then
          docker run --rm --user "$DOCKER_USER" -v "$OUT:/spec" -w /spec "$REDOCLY_IMAGE" lint "${specs[@]}" >>"$log" 2>&1 || rc=1
        fi
        # Each X.schema.json must compile (strict); X.json, when present, must validate against it.
        for s in "${schemas[@]}"; do
          (cd "$FE" && node -e 'const fs=require("fs"),Ajv=require("ajv/dist/2020").default;
            const a=new Ajv({strict:true,allErrors:true,validateFormats:false});const [s,i]=process.argv.slice(1);
            const v=a.compile(JSON.parse(fs.readFileSync(s)));console.log("valid schema",s);
            if(fs.existsSync(i)){if(!v(JSON.parse(fs.readFileSync(i)))){console.log("error: instance",i,JSON.stringify(v.errors));process.exit(1)}console.log("valid instance",i)}' \
            "$OUT/$s" "$OUT/${s%.schema.json}.json") >>"$log" 2>&1 || rc=1
        done
        # SQL contracts: applied in one transaction to a throwaway PostgreSQL (the stack's image).
        local sqls=()
        while IFS= read -r f; do sqls+=("$f"); done < <(cd "$OUT" && find docs/02_contracts -name '*.sql' | sort)
        if [ ${#sqls[@]} -gt 0 ]; then
          local pg="verify-contracts-$$"
          docker run -d --rm --name "$pg" -e POSTGRES_HOST_AUTH_METHOD=trust -v "$OUT/docs/02_contracts:/contracts:ro" postgres:16.15-alpine >>"$log" 2>&1
          for _ in $(seq 1 30); do docker exec "$pg" pg_isready -U postgres -q 2>/dev/null && break; sleep 1; done
          for f in "${sqls[@]}"; do
            docker exec "$pg" psql -U postgres -v ON_ERROR_STOP=1 --single-transaction -q -f "/contracts/${f#docs/02_contracts/}" >>"$log" 2>&1 \
              && echo "valid sql $f" >>"$log" || { echo "error: sql $f" >>"$log"; rc=1; }
          done
          docker stop "$pg" >/dev/null 2>&1
        fi
        numbers="openapi=${#specs[@]} schemas=${#schemas[@]} sql=${#sqls[@]} errors=$(grep -cE '^(\[[0-9]+\] |error)' "$log")"
      fi ;;
    semgrep)
      docker run --rm --user "$DOCKER_USER" -e SEMGREP_ENABLE_VERSION_CHECK=0 -e HOME=/tmp -v "$OUT:/src" -w /src "$SEMGREP_IMAGE" \
        semgrep scan --metrics=off --config p/default --config p/owasp-top-ten --config p/java --config p/typescript --config p/react \
        --exclude node_modules --exclude target --exclude dist --exclude logs --exclude reports --exclude coverage --exclude .stryker-tmp \
        --json --output /src/logs/"${PHASE}"_semgrep.json >"$log" 2>&1; rc=$?
      # standards/security.md: ERROR/WARNING/INFO -> High/Medium/Low; rules that already use CRITICAL/HIGH/MEDIUM/LOW count as reported.
      numbers="$(node -e 'const r=JSON.parse(require("fs").readFileSync(process.argv[1])).results;
        const m={CRITICAL:"critical",ERROR:"high",HIGH:"high",WARNING:"medium",MEDIUM:"medium",INFO:"low",LOW:"low"},c={critical:0,high:0,medium:0,low:0};
        for(const x of r)c[m[x.extra.severity]||"low"]++;console.log(Object.entries(c).map(([k,v])=>k+"="+v).join(" "))' "$LOGS/${PHASE}_semgrep.json" 2>/dev/null || echo "report=none")" ;;
    gitleaks)
      # Working tree = tracked and untracked-not-ignored files only (never .env); history = commits reachable from HEAD.
      local tree; tree="$(mktemp -d)"
      (cd "$ROOT" && git ls-files -co --exclude-standard -z | xargs -0 -I{} cp --parents {} "$tree"/ 2>/dev/null)
      docker run --rm --user "$DOCKER_USER" -v "$ROOT:/repo" -w /repo "$GITLEAKS_IMAGE" git --redact --no-banner --log-opts=HEAD \
        --report-format json --report-path /repo/02_output/logs/"${PHASE}"_gitleaks-history.json /repo >"$log" 2>&1; local r1=$?
      docker run --rm --user "$DOCKER_USER" -v "$tree:/tree" -v "$LOGS:/logs" "$GITLEAKS_IMAGE" dir --redact --no-banner \
        --report-format json --report-path /logs/"${PHASE}"_gitleaks-tree.json /tree >>"$log" 2>&1; local r2=$?
      rm -rf "$tree"
      rc=$(( r1 || r2 ))
      numbers="history=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1])).length)' "$LOGS/${PHASE}_gitleaks-history.json" 2>/dev/null || echo '?') tree=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1])).length)' "$LOGS/${PHASE}_gitleaks-tree.json" 2>/dev/null || echo '?')" ;;
    cloc)
      rc=0
      for part in backend/src/main backend/src/test frontend/src frontend/e2e; do
        [ -d "$OUT/$part" ] || continue
        echo "== $part" >>"$log"
        docker run --rm --user "$DOCKER_USER" -v "$OUT:/src" -w /src "$CLOC_IMAGE" --quiet --csv "$part" >>"$log" 2>&1 || rc=1
      done
      numbers="$(awk -F, '/^== /{p=$0; sub(/^== /,"",p)} /,SUM,/{printf "%s=%s ", p, $5}' "$log")" ;;
    e2e)
      # Needs the local stack (docker compose up) on E2E_BASE_URL / MAILPIT_URL; not in the default list.
      # Organizer credentials reach the container only through secrets.sh (passed by name with -e KEY).
      # Host ports are reached as host.docker.internal (Docker Desktop runs containers in a VM, so
      # --network host would not see the host's loopback).
      bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
        docker run --rm --add-host host.docker.internal:host-gateway --user "$DOCKER_USER" -e HOME=/tmp -e CI=1 \
        -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD -e ORGANIZER_EMAILS \
        -e E2E_BASE_URL="${E2E_BASE_URL:-http://host.docker.internal:8088}" \
        -e MAILPIT_URL="${MAILPIT_URL:-http://host.docker.internal:8026}" \
        -v "$FE:/work" -w /work "$PLAYWRIGHT_IMAGE" \
        npx --no-install playwright test ${FILTER:+$FILTER} >"$log" 2>&1; rc=$?
      numbers="$(grep -oE '[0-9]+ (passed|failed|flaky|skipped|did not run)' "$log" | tr '\n' ' ')" ;;
    *)
      echo "unknown tool: $tool" >"$log"; rc=2 ;;
  esac
  [ "$rc" -ne 0 ] && FAILED=1
  printf '%-15s exit=%-3s %s  log=%s\n' "$tool" "$rc" "$numbers" "${log#"$ROOT/"}"
}

IFS=',' read -r -a SELECTED <<<"$SELECTION"
for t in "${SELECTED[@]}"; do run_tool "$t"; done
exit "$FAILED"
