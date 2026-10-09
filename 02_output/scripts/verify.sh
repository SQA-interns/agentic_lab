#!/usr/bin/env bash
# Runs the project's checks and scanners (rules.md, "Output size"). Each tool's full output goes to
# 02_output/logs/<phase>_<tool>.log; one line per tool is printed: tool, exit code, key numbers, log.
#
#   bash 02_output/scripts/verify.sh <phase> [tools] [test-filter]
#
#   phase        label used in the log names, e.g. 0, 4, 6
#   tools        comma-separated tool names, or "default" (the default), or "all"
#                default: every tool that needs no running stack, except the slow ones
#                all:     default plus mutation testing and the backend dependency scan
#                e2e, stack-up and stack-down run only when named (e2e needs stack-up first)
#   test-filter  optional subset of tests: backend -Dtest pattern, vitest name filter,
#                Playwright --grep pattern
#
# Exit code: 0 when every tool exited 0, otherwise 1.

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/02_output"
BE="$OUT/backend"
FE="$OUT/frontend"
LOGS="$OUT/logs"
CONTRACTS="$OUT/docs/02_contracts"
SECRETS="$ROOT/01_input/00_general/tools/secrets.sh"
# Docker on Windows needs Windows-style paths; MSYS_NO_PATHCONV is set on Docker calls only.
WROOT="$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))"

PHASE="${1:?usage: verify.sh <phase> [tools] [test-filter]}"
TOOLS="${2:-default}"
FILTER="${3:-}"

IMG_SEMGREP="semgrep/semgrep:1.177.0"
IMG_GITLEAKS="zricethezav/gitleaks:v8.30.1"
IMG_CLOC="aldanial/cloc:2.10"
IMG_REDOCLY="redocly/cli:2.57.0"
IMG_PLAYWRIGHT="mcr.microsoft.com/playwright:v1.63.0-noble"
IMG_POSTGRES="postgres:16.15-alpine"

DEFAULT_TOOLS="be-build be-format be-lint be-spotbugs be-cpd be-test fe-build fe-format fe-lint fe-typecheck fe-cpd fe-test fe-audit contracts semgrep gitleaks cloc"
ALL_TOOLS="$DEFAULT_TOOLS be-mutation be-depscan fe-mutation"
KNOWN_TOOLS="$ALL_TOOLS e2e stack-up stack-down"

mkdir -p "$LOGS"
export FORCE_COLOR=0 NO_COLOR=1

case "$TOOLS" in
  default) SELECTED="$DEFAULT_TOOLS" ;;
  all) SELECTED="$ALL_TOOLS" ;;
  *) SELECTED="${TOOLS//,/ }" ;;
esac

# Evaluates a JavaScript expression over the parsed JSON file `d`; prints "n/a" when unreadable.
json() {
  node -e 'try { const d = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log(eval(process.argv[2])); } catch (e) { console.log("n/a"); }' "$1" "$2"
}

count() {
  if [ -f "$2" ]; then grep -o "$1" "$2" | wc -l | tr -d ' '; else echo "n/a"; fi
}

docker_run() {
  MSYS_NO_PATHCONV=1 docker run --rm "$@"
}

maven_tests() {
  local line
  line="$(grep -E '\[(INFO|ERROR|WARNING)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$1" | tail -n 1 | sed 's/^\[[A-Z]*\] //')"
  echo "${line:-Tests run: 0}"
}

FAILED=0
LOG=""

report() {
  local tool="$1" code="$2" key="$3"
  printf '%-12s exit=%-3s %-70s %s\n' "$tool" "$code" "$key" "${LOG#"$ROOT"/}"
  [ "$code" = 0 ] || FAILED=1
}

run_tool() {
  local tool="$1" code=0 key=""
  LOG="$LOGS/${PHASE}_${tool}.log"
  case "$tool" in
    be-build)
      (cd "$BE" && ./mvnw -B -DskipTests package) >"$LOG" 2>&1 || code=$?
      key="$(grep -cE '^\[ERROR\]' "$LOG") errors"
      ;;
    be-format)
      (cd "$BE" && ./mvnw -B spotless:check) >"$LOG" 2>&1 || code=$?
      key="$(grep -c 'violations' "$LOG") files with violations"
      ;;
    be-lint)
      (cd "$BE" && ./mvnw -B compile pmd:check) >"$LOG" 2>&1 || code=$?
      key="$(count '<violation ' "$BE/target/pmd.xml") PMD violations"
      ;;
    be-spotbugs)
      (cd "$BE" && ./mvnw -B compile spotbugs:check) >"$LOG" 2>&1 || code=$?
      key="$(count '<BugInstance ' "$BE/target/spotbugsXml.xml") SpotBugs bugs"
      ;;
    be-cpd)
      (cd "$BE" && ./mvnw -B pmd:cpd-check) >"$LOG" 2>&1 || code=$?
      key="$(count '<duplication ' "$BE/target/cpd.xml") duplications"
      ;;
    be-test)
      local args=()
      [ -n "$FILTER" ] && args=("-Dtest=$FILTER" "-Dsurefire.failIfNoSpecifiedTests=false")
      (cd "$BE" && ./mvnw -B verify "${args[@]}") >"$LOG" 2>&1 || code=$?
      key="$(maven_tests "$LOG")"
      if [ -f "$BE/target/site/jacoco/jacoco.csv" ]; then
        key="$key; $(awk -F, 'NR > 1 { lm += $8; lc += $9; bm += $6; bc += $7 }
          END { printf "line %.1f%%, branch %.1f%%", (lm + lc) ? 100 * lc / (lm + lc) : 0, (bm + bc) ? 100 * bc / (bm + bc) : 0 }' \
          "$BE/target/site/jacoco/jacoco.csv")"
      fi
      ;;
    be-mutation)
      rm -rf "$BE/target/pit-reports"
      (cd "$BE" && ./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage -DfailWhenNoMutations=false) >"$LOG" 2>&1 || code=$?
      key="$(grep -E '>> Generated [0-9]+ mutations Killed [0-9]+' "$LOG" | tail -n 1 | sed 's/^.*>> //')"
      key="${key:-no mutation summary}"
      ;;
    be-depscan)
      (cd "$BE" && bash "$SECRETS" run NVD_API_KEY -- ./mvnw -B org.owasp:dependency-check-maven:check) >"$LOG" 2>&1 || code=$?
      key="$(json "$BE/target/dependency-check-report.json" '
        const c = { critical: 0, high: 0, medium: 0, low: 0 };
        for (const dep of d.dependencies || []) for (const v of dep.vulnerabilities || []) {
          const s = (v.cvssv3 && v.cvssv3.baseScore) ?? (v.cvssv2 && v.cvssv2.score) ?? null;
          const k = s === null ? String(v.severity).toLowerCase() : s >= 9 ? "critical" : s >= 7 ? "high" : s >= 4 ? "medium" : "low";
          c[k in c ? k : "low"]++;
        }
        `critical ${c.critical}, high ${c.high}, medium ${c.medium}, low ${c.low}`')"
      ;;
    fe-build)
      (cd "$FE" && npm run build) >"$LOG" 2>&1 || code=$?
      key="$(grep -cE 'error' "$LOG") error lines"
      ;;
    fe-format)
      (cd "$FE" && npx prettier --check .) >"$LOG" 2>&1 || code=$?
      key="$(grep -c '^\[warn\] [^C]' "$LOG") unformatted files"
      ;;
    fe-lint)
      (cd "$FE" && npx eslint . --max-warnings 0) >"$LOG" 2>&1 || code=$?
      key="$(grep -oE '[0-9]+ problems?' "$LOG" | tail -n 1)"
      key="${key:-0 problems}"
      ;;
    fe-typecheck)
      (cd "$FE" && npx tsc --noEmit) >"$LOG" 2>&1 || code=$?
      key="$(grep -c 'error TS' "$LOG") type errors"
      ;;
    fe-cpd)
      (cd "$FE" && npx jscpd src --silent --reporters json --output reports/jscpd) >"$LOG" 2>&1 || code=$?
      key="$(json "$FE/reports/jscpd/jscpd-report.json" '`${d.statistics.total.clones} clones, ${d.statistics.total.percentage}% duplicated`')"
      ;;
    fe-test)
      local args=()
      [ -n "$FILTER" ] && args=("-t" "$FILTER")
      (cd "$FE" && npx vitest run --coverage "${args[@]}") >"$LOG" 2>&1 || code=$?
      key="$(grep -E '^ +Tests +' "$LOG" | tail -n 1 | sed 's/^ *//')"
      key="${key:-no tests}"
      key="$key; $(json "$FE/coverage/coverage-summary.json" '`line ${d.total.lines.pct}%, branch ${d.total.branches.pct}%`')"
      ;;
    fe-mutation)
      rm -rf "$FE/reports/mutation"
      (cd "$FE" && npx stryker run) >"$LOG" 2>&1 || code=$?
      key="$(json "$FE/reports/mutation/mutation.json" '
        let k = 0, s = 0, t = 0, n = 0;
        for (const f of Object.values(d.files)) for (const m of f.mutants) {
          if (m.status === "Killed") k++; else if (m.status === "Survived") s++;
          else if (m.status === "Timeout") t++; else if (m.status === "NoCoverage") n++;
        }
        const valid = k + s + t + n;
        `${valid} mutants, killed ${k}, survived ${s}, timeout ${t}, no coverage ${n}, score ${valid ? (100 * (k + t) / valid).toFixed(1) : "n/a"}%`')"
      ;;
    fe-audit)
      # Exit code follows the severity policy: Critical and High block; lower levels are counted.
      (cd "$FE" && npm audit --json --audit-level=high) >"$LOG" 2>&1 || code=$?
      key="$(json "$LOG" 'const v = d.metadata.vulnerabilities; `critical ${v.critical}, high ${v.high}, moderate ${v.moderate}, low ${v.low}`')"
      ;;
    contracts)
      {
        echo "== redocly lint"
        local specs
        specs="$(cd "$OUT/docs" && ls 02_contracts/*.yaml 2>/dev/null | tr '\n' ' ')"
        if [ -n "$specs" ]; then
          # shellcheck disable=SC2086
          docker_run -v "$WROOT/02_output/docs:/spec" -w /spec "$IMG_REDOCLY" lint $specs
        else
          echo "no OpenAPI contracts yet"; docker_run "$IMG_REDOCLY" --version
        fi
      } >"$LOG" 2>&1 || code=$?
      {
        echo "== ajv (JSON Schema 2020-12)"
        if [ -d "$CONTRACTS" ]; then
          (cd "$FE" && node scripts/validate-schemas.mjs "$CONTRACTS")
        else
          echo "no JSON Schema contracts yet"; (cd "$FE" && node -e 'console.log("ajv " + require("ajv/package.json").version)')
        fi
      } >>"$LOG" 2>&1 || code=$?
      {
        echo "== SQL (applied to an empty PostgreSQL)"
        local sql
        for sql in "$CONTRACTS"/*.sql; do
          [ -f "$sql" ] || { echo "no SQL contracts yet"; break; }
          MSYS_NO_PATHCONV=1 docker run --rm -i -e POSTGRES_PASSWORD=contract-check -e POSTGRES_HOST_AUTH_METHOD=trust \
            "$IMG_POSTGRES" sh -c 'docker-entrypoint.sh postgres >/tmp/pg.log 2>&1 & \
              for i in $(seq 60); do pg_isready -q -U postgres -h 127.0.0.1 && break; sleep 1; done; \
              psql -q -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -f - && echo "SQL valid"' <"$sql" &&
            echo "$(basename "$sql"): valid sql" || { echo "$(basename "$sql"): INVALID"; false; }
        done
      } >>"$LOG" 2>&1 || code=$?
      key="$(grep -cE 'INVALID|[1-9][0-9]* errors?\b' "$LOG") invalid; $(grep -c 'valid schema' "$LOG") schemas, $(grep -c ': valid sql' "$LOG") sql"
      ;;
    semgrep)
      # --no-git-ignore: also scan files not committed yet; build output is excluded explicitly.
      docker_run -v "$WROOT:/src" -w /src "$IMG_SEMGREP" semgrep scan --config p/default --metrics=off \
        --no-git-ignore --exclude=node_modules --exclude=target --exclude=dist --exclude=coverage \
        --exclude=reports --exclude=.stryker-tmp --json --output "/src/02_output/logs/${PHASE}_semgrep.json" \
        02_output/backend/src 02_output/frontend/src 02_output/docs/02_contracts >"$LOG" 2>&1 || code=$?
      key="$(json "$LOGS/${PHASE}_semgrep.json" '
        const c = {}; for (const r of d.results) c[r.extra.severity] = (c[r.extra.severity] || 0) + 1;
        `${d.results.length} results ${JSON.stringify(c)}, ${d.errors.length} errors`')"
      ;;
    gitleaks)
      local tree="$LOGS/${PHASE}_gitleaks-tree.json" hist="$LOGS/${PHASE}_gitleaks-history.json"
      {
        echo "== working tree (tracked and untracked, not ignored)"
        (cd "$ROOT" && git ls-files -co --exclude-standard -z | tar --null --ignore-failed-read -T - -cf -) |
          MSYS_NO_PATHCONV=1 docker run --rm -i --entrypoint sh "$IMG_GITLEAKS" -c \
            'mkdir /tmp/scan && tar -xf - -C /tmp/scan && gitleaks dir /tmp/scan --redact --no-banner --exit-code 0 --report-format json --report-path /tmp/report.json 1>&2 && cat /tmp/report.json' \
          >"$tree"
        echo "== history of the current branch"
        docker_run -v "$WROOT:/repo" -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*' \
          "$IMG_GITLEAKS" git /repo --log-opts=HEAD --redact --no-banner --exit-code 0 \
          --report-format json --report-path "/repo/02_output/logs/${PHASE}_gitleaks-history.json"
      } >"$LOG" 2>&1 || code=$?
      key="tree $(json "$tree" 'd.length') leaks, history $(json "$hist" 'd.length') leaks"
      ;;
    cloc)
      docker_run -v "$WROOT/02_output:/src" -w /src "$IMG_CLOC" --json --quiet \
        --exclude-dir=node_modules,target,dist,coverage,reports,.stryker-tmp,logs,docs \
        --not-match-f='package-lock\.json' \
        backend frontend >"$LOG" 2>&1 || code=$?
      key="$(json "$LOG" '`${d.SUM.nFiles} files, ${d.SUM.code} code lines`')"
      ;;
    e2e)
      # Needs the local stack (stack-up); joins its network. Organizer settings come from .env.
      local net="${E2E_NETWORK:-registration_default}" args=()
      docker network inspect "$net" >/dev/null 2>&1 || net=bridge
      [ -n "$FILTER" ] && args=("--grep" "$FILTER")
      MSYS_NO_PATHCONV=1 bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
        docker run --rm --network "$net" -e npm_config_update_notifier=false \
        -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD -e ORGANIZER_EMAILS \
        -e "E2E_BASE_URL=${E2E_BASE_URL:-http://frontend:8080}" \
        -e "E2E_MAILPIT_URL=${E2E_MAILPIT_URL:-http://mailpit:8025}" \
        -v "$WROOT/02_output/frontend:/work" -w /work "$IMG_PLAYWRIGHT" \
        npx playwright test --pass-with-no-tests "${args[@]}" >"$LOG" 2>&1 || code=$?
      key="$(grep -oE '[0-9]+ (passed|failed|flaky|skipped|did not run)' "$LOG" | tr '\n' ' ')"
      key="${key:-no tests run}"
      ;;
    stack-up)
      # Builds the backend jar and both images, starts the local stack and waits for health checks.
      {
        (cd "$BE" && ./mvnw -B -q -DskipTests package) &&
          (cd "$OUT" && bash "$SECRETS" run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
            docker compose up -d --build --wait --wait-timeout 240)
      } >"$LOG" 2>&1 || code=$?
      key="$(docker ps -a --filter label=com.docker.compose.project=registration \
        --format '{{.Label "com.docker.compose.service"}}={{.State}}/{{.Status}}' 2>&1 | sed 's/ (.*)//' | tr '\n' ' ')"
      ;;
    stack-down)
      # Stops the local stack; named volumes (data) are kept.
      (cd "$OUT" && bash "$SECRETS" run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
        docker compose down) >"$LOG" 2>&1 || code=$?
      key="stopped"
      ;;
    *)
      LOG=""
      report "$tool" 2 "unknown tool; known: $KNOWN_TOOLS"
      return
      ;;
  esac
  report "$tool" "$code" "$key"
}

for tool in $SELECTED; do
  run_tool "$tool"
done
exit "$FAILED"
