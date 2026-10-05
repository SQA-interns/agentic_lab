#!/usr/bin/env bash
# Runs the project's checks and scanners. Each tool's full output goes to
# 02_output/logs/<phase>_<tool>.log; one summary line per tool is printed.
#
#   bash 02_output/scripts/verify.sh <phase> [tool ...] [--filter <pattern>]
#
# Default tools (no stack needed):
#   be-build be-test be-check be-mutation be-depcheck
#   fe-build fe-test fe-check fe-mutation fe-dup fe-audit
#   semgrep gitleaks cloc contracts
# Opt-in tools (need the running local stack): e2e
# --filter limits be-test (-Dtest), fe-test (file pattern), be-mutation (target tests) and e2e (--grep).
set -u

OUT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$OUT/.." && pwd)"
BE="$OUT/backend"
FE="$OUT/frontend"
LOGS="$OUT/logs"
SECRETS="$ROOT/01_input/00_general/tools/secrets.sh"
mkdir -p "$LOGS"

DEFAULT_TOOLS="be-build be-test be-check be-mutation be-depcheck fe-build fe-test fe-check fe-mutation fe-dup fe-audit semgrep gitleaks cloc contracts"

# Image pins (tech-stack.md, tooling)
SEMGREP_IMAGE="semgrep/semgrep:1.177.0"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1"
CLOC_IMAGE="aldanial/cloc:2.10"
REDOCLY_IMAGE="redocly/cli:2.57.0"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.63.0-noble"

[ $# -ge 1 ] || { echo "usage: verify.sh <phase> [tool ...] [--filter <pattern>]" >&2; exit 2; }
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

# Windows path for Docker volume mounts under Git Bash; plain path elsewhere.
winpath() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
# Docker calls only: keep Git Bash from rewriting container paths.
dockr() { MSYS_NO_PATHCONV=1 docker "$@"; }

FAILED=0
report() { # tool exit summary
  printf '%-12s exit=%-3s %s  log=%s\n' "$1" "$2" "$3" "02_output/logs/${PHASE}_$1.log"
  [ "$2" = 0 ] || FAILED=1
}

surefire_summary() { # log
  grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$1" | tail -n 1 \
    | sed 's/.*\(Tests run.*\)/\1/'
}

jacoco_summary() {
  local csv="$BE/target/site/jacoco/jacoco.csv"
  [ -f "$csv" ] || { echo "coverage: n/a"; return; }
  awk -F, 'NR > 1 { bm += $6; bc += $7; lm += $8; lc += $9 }
    END {
      printf "line %.1f%% (%d/%d), branch %s",
        (lc + lm) ? 100 * lc / (lc + lm) : 0, lc, lc + lm,
        (bc + bm) ? sprintf("%.1f%% (%d/%d)", 100 * bc / (bc + bm), bc, bc + bm) : "n/a"
    }' "$csv"
}

run_tool() {
  local tool="$1" log="$LOGS/${PHASE}_$1.log" rc=0 summary=""
  case "$tool" in
    be-build)
      (cd "$BE" && ./mvnw -B -ntp -DskipTests package) >"$log" 2>&1 || rc=$?
      summary="$(grep -E 'BUILD (SUCCESS|FAILURE)' "$log" | tail -n 1 | sed 's/\[INFO\] //')"
      ;;
    be-test)
      local args=()
      [ -n "$FILTER" ] && args=("-Dtest=$FILTER" "-Dsurefire.failIfNoSpecifiedTests=false")
      # coverage of this run only (JaCoCo appends to its data file otherwise)
      rm -f "$BE/target/jacoco.exec"
      (cd "$BE" && ./mvnw -B -ntp "${args[@]}" test) >"$log" 2>&1 || rc=$?
      summary="$(surefire_summary "$log"); $(jacoco_summary)"
      ;;
    be-check)
      (cd "$BE" && ./mvnw -B -ntp compile spotless:check pmd:check pmd:cpd-check spotbugs:check) >"$log" 2>&1 || rc=$?
      local bugs pmd cpd
      bugs="$(grep -oE 'Total bugs: [0-9]+' "$log" | tail -n 1 | grep -oE '[0-9]+')"
      pmd="$(grep -oE '(You have|has found) [0-9]+ (PMD )?violation' "$log" | grep -oE '[0-9]+' | head -n 1)"
      cpd="$(grep -oE '(You have|has found) [0-9]+ (CPD )?duplication' "$log" | grep -oE '[0-9]+' | head -n 1)"
      summary="spotless $(grep -q 'spotless.*violations' "$log" && echo fail || echo ok), pmd ${pmd:-0}, cpd ${cpd:-0}, spotbugs ${bugs:-0}"
      ;;
    be-mutation)
      # Unit tests only: acceptance and integration tests start containers per mutant, which
      # would take hours; Spring wiring classes are reached only by those tests.
      local p=si.konferenca.registration
      local args=(
        "-Dthreads=4"
        "-DtargetTests=${FILTER:-$p.application.*,$p.infrastructure.*,$p.api.*,$p.config.*}"
        "-DexcludedTestClasses=$p.acceptance.*,$p.integration.*,$p.ArchitectureTest"
        "-DexcludedClasses=$p.RegistrationApplication,$p.config.BackendConfig,$p.config.MailConfig,$p.config.SecurityConfig,$p.config.WebConfig,$p.config.AppProperties*"
      )
      (cd "$BE" && ./mvnw -B -ntp test-compile org.pitest:pitest-maven:mutationCoverage "${args[@]}") >"$log" 2>&1 || rc=$?
      summary="$(grep -E '>> Generated [0-9]+ mutations' "$log" | tail -n 1 | sed 's/.*>> //'); $(grep -E '>> Line Coverage' "$log" | tail -n 1 | sed 's/.*>> //')"
      ;;
    be-depcheck)
      (cd "$BE" && bash "$SECRETS" run NVD_API_KEY -- ./mvnw -B -ntp dependency-check:check) >"$log" 2>&1 || rc=$?
      summary="$(node "$OUT/scripts/severity-count.mjs" depcheck "$BE/target/dependency-check-report.json" 2>&1)"
      ;;
    fe-build)
      (cd "$FE" && npm run -s build) >"$log" 2>&1 || rc=$?
      summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E 'built in' | tail -n 1)"
      ;;
    fe-test)
      local args=()
      [ -n "$FILTER" ] && args=("$FILTER")
      (cd "$FE" && npx vitest run --coverage "${args[@]}") >"$log" 2>&1 || rc=$?
      summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E '^ *Tests ' | tail -n 1 | sed 's/^ *//'); $(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E '^(Lines|Branches) ' | tr -s ' ' | tr '\n' ' ')"
      ;;
    fe-check)
      (cd "$FE" && npm run -s check) >"$log" 2>&1 || rc=$?
      summary="$([ "$rc" = 0 ] && echo 'prettier, eslint, tsc ok' || grep -E 'error|warn|problem' "$log" | tail -n 1)"
      ;;
    fe-mutation)
      (cd "$FE" && npx stryker run) >"$log" 2>&1 || rc=$?
      summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E '^All files' | tail -n 1 \
        | awk -F'|' '{gsub(/ /,""); printf "score %s%%, killed %s, timeout %s, survived %s, no cov %s, errors %s", $2, $4, $5, $6, $7, $8}')"
      ;;
    fe-dup)
      (cd "$FE" && npx jscpd src) >"$log" 2>&1 || rc=$?
      summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E 'Total:' | tail -n 1 | tr -s ' ')"
      [ -n "$summary" ] || summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | tail -n 1)"
      ;;
    fe-audit)
      (cd "$FE" && npm audit --json) >"$log" 2>&1
      summary="$(node "$OUT/scripts/severity-count.mjs" npm-audit "$log" 2>&1)" || rc=$?
      ;;
    semgrep)
      local src
      src="$(winpath "$OUT")"
      dockr run --rm -v "$src:/src" -w /src "$SEMGREP_IMAGE" semgrep scan \
        --config p/default --config p/java --config p/typescript --config p/react --config p/secrets \
        --metrics=off --json --quiet \
        --exclude node_modules --exclude target --exclude dist --exclude coverage --exclude reports \
        --exclude logs --exclude .stryker-tmp --exclude mvnw --exclude mvnw.cmd \
        >"$log" 2>"$log.err" || rc=$?
      cat "$log.err" >>"$LOGS/${PHASE}_semgrep.stderr.log" 2>/dev/null
      rm -f "$log.err"
      summary="$(node "$OUT/scripts/severity-count.mjs" semgrep "$log" 2>&1)"
      ;;
    gitleaks)
      # History of the current branch (SB-09), then the working tree: tracked and untracked
      # files that git does not ignore, copied to a temporary directory.
      local repo tree tmp rc2=0
      repo="$(winpath "$ROOT")"
      tmp="$(mktemp -d)"
      (cd "$ROOT" && git ls-files -co --exclude-standard -z | tar --null -T - -cf - | tar -xf - -C "$tmp")
      tree="$(winpath "$tmp")"
      {
        echo "== history of the current branch =="
        dockr run --rm -v "$repo:/repo" "$GITLEAKS_IMAGE" git /repo --log-opts=HEAD --redact --no-banner -v || rc=$?
        echo "== working tree =="
        dockr run --rm -v "$tree:/tree" "$GITLEAKS_IMAGE" dir /tree --redact --no-banner -v || rc2=$?
      } >"$log" 2>&1
      rm -rf "$tmp"
      [ "$rc" = 0 ] && rc=$rc2
      summary="$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -oE '(no )?leaks found.*' | tr '\n' ';' | sed 's/;$//; s/;/ | /')"
      ;;
    cloc)
      local out
      out="$(winpath "$OUT")"
      {
        for part in backend/src/main backend/src/test frontend/src frontend/e2e; do
          [ -d "$OUT/$part" ] || continue
          echo "== $part =="
          dockr run --rm -v "$out:/out" -w /out "$CLOC_IMAGE" --quiet --csv "$part" || rc=$?
        done
      } >"$log" 2>&1
      summary="$(awk -F, '/^== /{p=$0; gsub(/ ?== ?/,"",p); next} $2=="SUM"{printf "%s %s code; ", p, $5}' "$log")"
      ;;
    contracts)
      # OpenAPI: redocly lint; JSON Schema and instances: ajv; SQL: psql in a throwaway PostgreSQL.
      local docs files=() sqls=() r1=0 r2=0 r3=0 pg
      docs="$(winpath "$OUT/docs")"
      if [ -d "$OUT/docs/02_contracts" ]; then
        while IFS= read -r f; do files+=("02_contracts/${f##*/}"); done \
          < <(find "$OUT/docs/02_contracts" -maxdepth 1 -name '*.openapi.yaml' | sort)
        while IFS= read -r f; do sqls+=("${f##*/}"); done \
          < <(find "$OUT/docs/02_contracts" -maxdepth 1 -name '*.sql' | sort)
      fi
      if [ ${#files[@]} -eq 0 ]; then
        dockr run --rm "$REDOCLY_IMAGE" --version >"$log" 2>&1 || rc=$?
        summary="no contracts yet; redocly $(tail -n 1 "$log")"
      else
        {
          echo "== redocly lint =="
          dockr run --rm -v "$docs:/spec" -w /spec "$REDOCLY_IMAGE" lint "${files[@]}" || r1=$?
          echo "== ajv =="
          node "$OUT/scripts/validate-contracts.mjs" || r2=$?
          echo "== psql =="
          pg="verify-contracts-pg-$$"
          dockr run -d --rm --name "$pg" -e POSTGRES_PASSWORD=verify-only -v "$docs/02_contracts:/contracts:ro" \
            "postgres:16.15-alpine" >/dev/null
          for _ in $(seq 1 30); do
            dockr exec "$pg" pg_isready -U postgres -q 2>/dev/null && break
            sleep 1
          done
          for s in "${sqls[@]}"; do
            echo "-- $s"
            dockr exec "$pg" psql -U postgres -v ON_ERROR_STOP=1 -q -f "/contracts/$s" && echo "ok    $s applies" || r3=1
          done
          dockr rm -f "$pg" >/dev/null
        } >"$log" 2>&1
        [ "$r1$r2$r3" = 000 ] || rc=1
        summary="redocly $([ "$r1" = 0 ] && echo ok || echo fail) ($(grep -cE 'validated in' "$log") files, $(grep -oE 'You have [0-9]+ (warning|error)s?' "$log" | grep -oE '[0-9]+' | paste -sd+ - | bc 2>/dev/null || echo 0) warnings/errors); ajv $(grep -E '^(all valid|[0-9]+ failed)' "$log"); sql $(grep -c 'applies' "$log")/${#sqls[@]} apply"
      fi
      ;;
    e2e)
      # Runs in the compose network of the local stack by default (E2E_NETWORK, E2E_BASE_URL and
      # MAILPIT_URL override it); organizer keys reach the container only through secrets.sh.
      local out args=()
      out="$(winpath "$OUT")"
      [ -n "$FILTER" ] && args=(--grep "$FILTER")
      MSYS_NO_PATHCONV=1 bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- \
        docker run --rm --network "${E2E_NETWORK:-registration_default}" --ipc=host \
        -e CI=1 -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD -e ORGANIZER_EMAILS \
        -e "E2E_BASE_URL=${E2E_BASE_URL:-http://frontend:8080}" -e "MAILPIT_URL=${MAILPIT_URL:-http://mailpit:8025}" \
        -v "$out:/work" -w /work/frontend "$PLAYWRIGHT_IMAGE" npx playwright test --pass-with-no-tests "${args[@]}" \
        >"$log" 2>&1 || rc=$?
      summary="$(grep -E '[0-9]+ (passed|failed|skipped|flaky)' "$log" | tr -s ' ' | tr '\n' ' ')"
      [ -n "$summary" ] || summary="$(tail -n 1 "$log")"
      [ -n "$summary" ] || summary="no end-to-end tests found"
      ;;
    *)
      echo "unknown tool: $tool" >&2
      FAILED=1
      return
      ;;
  esac
  report "$tool" "$rc" "$summary"
}

for t in $TOOLS; do
  run_tool "$t"
done
exit "$FAILED"
