#!/usr/bin/env bash
# Runs every check and scanner. Full output goes to 02_output/logs/<phase>_<tool>.log;
# one summary line per tool is printed: tool, exit code, key numbers, log path.
# Usage: verify.sh <phase> [tool ...]   (no tools = all)
# gitleaks scans the history of the checked-out branch only (other branches hold other runs).
# Tools: be-build be-format be-lint be-cpd be-spotbugs be-test be-mutation be-depcheck
#        fe-format fe-lint fe-typecheck fe-build fe-test fe-mutation fe-cpd fe-audit fe-audit-prod
#        semgrep gitleaks cloc contracts
set -uo pipefail

PHASE="${1:?usage: verify.sh <phase> [tool ...]}"
shift
OUT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$OUT/.." && pwd)"
BE="$OUT/backend"
FE="$OUT/frontend"
LOGS="$OUT/logs"
mkdir -p "$LOGS"

NODE_BIN="${NODE_BIN:-$HOME/.nvm/versions/node/v24.13.0/bin}"
[ -d "$NODE_BIN" ] && export PATH="$NODE_BIN:$PATH"

SEMGREP_IMAGE="semgrep/semgrep:1.177.0"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1"
CLOC_IMAGE="aldanial/cloc:2.10"

# Exports one key from .env into the environment without printing it.
env_from_dotenv() {
  local key="$1"
  [ -f "$ROOT/.env" ] || return 1
  local val
  val="$(sed -n "s/^${key}=//p" "$ROOT/.env" | tr -d '\r')"
  [ -n "$val" ] || return 1
  export "$key=$val"
}

docker_ok() { docker info >/dev/null 2>&1; }

line() { printf '%-14s exit=%-3s %-50s %s\n' "$1" "$2" "$3" "$4"; }

skip() { printf '%-14s SKIP     %s\n' "$1" "$2"; }

# run <tool> <dir> <command...>; sets RC and LOG
run() {
  local tool="$1" dir="$2"
  shift 2
  LOG="$LOGS/${PHASE}_${tool}.log"
  (cd "$dir" && "$@") >"$LOG" 2>&1
  RC=$?
}

mvn() { run "$1" "$BE" ./mvnw -B -ntp "${@:2}"; }

t_be_build() { mvn be-build package -DskipTests; line be-build $RC "$(grep -c '\[ERROR\]' "$LOG") errors" "$LOG"; }
t_be_format() { mvn be-format spotless:check; line be-format $RC "$(grep -c 'violations' "$LOG") violation blocks" "$LOG"; }
t_be_lint() { mvn be-lint compile pmd:check; line be-lint $RC "$(grep -cE '\[WARNING\] PMD|PMD Failure' "$LOG") PMD violations" "$LOG"; }
t_be_cpd() { mvn be-cpd pmd:cpd-check; line be-cpd $RC "$(grep -c 'duplication' "$LOG") duplications" "$LOG"; }
t_be_spotbugs() { mvn be-spotbugs compile spotbugs:check; line be-spotbugs $RC "$(grep -oE 'BugInstance size is [0-9]+' "$LOG" | tail -1)" "$LOG"; }
t_be_test() {
  mvn be-test verify
  local s
  s="$(grep -E 'Tests run: [0-9]+, Failures' "$LOG" | tail -1 | sed 's/^\[[A-Z]*\] *//')"
  line be-test $RC "${s:-no tests}" "$LOG"
}
t_be_mutation() { mvn be-mutation test-compile org.pitest:pitest-maven:mutationCoverage; line be-mutation $RC "$(grep -E 'Generated [0-9]+ mutations Killed' "$LOG" | tail -1 | sed 's/^>> //')" "$LOG"; }
t_be_depcheck() {
  if ! env_from_dotenv NVD_API_KEY; then skip be-depcheck "NVD_API_KEY missing or empty in .env"; return; fi
  mvn be-depcheck org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=11
  local f="$BE/target/dependency-check-report.json" n="?"
  [ -f "$f" ] && n="$(node -e 'const r=require(process.argv[1]);const c={};for(const d of r.dependencies||[])for(const v of d.vulnerabilities||[]){const s=(v.severity||"?").toUpperCase();c[s]=(c[s]||0)+1}console.log(JSON.stringify(c))' "$f")"
  line be-depcheck $RC "vulns by severity: $n" "$LOG"
}

npmr() { run "$1" "$FE" npm run --silent "${@:2}"; }

t_fe_format() { npmr fe-format format:check; line fe-format $RC "$(grep -c '\[warn\]' "$LOG") files unformatted" "$LOG"; }
t_fe_lint() { npmr fe-lint lint; line fe-lint $RC "$(grep -oE '[0-9]+ problems?' "$LOG" | tail -1)" "$LOG"; }
t_fe_typecheck() { npmr fe-typecheck typecheck; line fe-typecheck $RC "$(grep -c 'error TS' "$LOG") type errors" "$LOG"; }
t_fe_build() { npmr fe-build build; line fe-build $RC "$(grep -c -i 'error' "$LOG") error lines" "$LOG"; }
t_fe_test() { run fe-test "$FE" npx vitest run --coverage --passWithNoTests; line fe-test $RC "$(grep -E 'Tests +[0-9]' "$LOG" | tail -1 | tr -s ' ')" "$LOG"; }
t_fe_mutation() { npmr fe-mutation mutation; line fe-mutation $RC "$(grep -oE 'Final mutation score of [0-9.]+' "$LOG" | tail -1)" "$LOG"; }
t_fe_cpd() { npmr fe-cpd duplication; line fe-cpd $RC "$(grep -oE 'Found [0-9]+ clones' "$LOG" | tail -1)" "$LOG"; }
t_fe_audit() { run fe-audit "$FE" npm audit; line fe-audit $RC "$(grep -E 'vulnerabilit' "$LOG" | tail -1)" "$LOG"; }
t_fe_audit_prod() { run fe-audit-prod "$FE" npm audit --omit=dev; line fe-audit-prod $RC "$(grep -E 'vulnerabilit' "$LOG" | tail -1)" "$LOG"; }

t_semgrep() {
  if ! docker_ok; then skip semgrep "Docker Engine not reachable"; return; fi
  run semgrep "$OUT" docker run --rm -v "$OUT:/src" -w /src "$SEMGREP_IMAGE" \
    semgrep scan --config p/default --metrics off --json --output /src/logs/"${PHASE}"_semgrep.json \
    --exclude node_modules --exclude target --exclude dist --exclude logs \
    --exclude coverage --exclude reports --exclude .stryker-tmp
  local n="?"
  [ -f "$LOGS/${PHASE}_semgrep.json" ] && n="$(node -e 'const r=require(process.argv[1]);const c={};for(const x of r.results)c[x.extra.severity]=(c[x.extra.severity]||0)+1;console.log(JSON.stringify(c))' "$LOGS/${PHASE}_semgrep.json")"
  line semgrep $RC "findings by severity: $n" "$LOG"
}
t_gitleaks() {
  if ! docker_ok; then skip gitleaks "Docker Engine not reachable"; return; fi
  run gitleaks "$ROOT" docker run --rm -v "$ROOT:/repo" "$GITLEAKS_IMAGE" git /repo --redact --no-banner --log-opts=HEAD \
    --report-format json --report-path /repo/02_output/logs/"${PHASE}"_gitleaks.json
  line gitleaks $RC "$(grep -oE 'leaks found: [0-9]+|no leaks found' "$LOG" | tail -1)" "$LOG"
}
t_cloc() {
  if ! docker_ok; then skip cloc "Docker Engine not reachable"; return; fi
  run cloc "$OUT" docker run --rm -v "$OUT:/src" -w /src "$CLOC_IMAGE" \
    --exclude-dir=node_modules,target,dist,logs,coverage,reports,.stryker-tmp --exclude-ext=json backend frontend
  line cloc $RC "$(grep -E '^SUM' "$LOG" | tr -s ' ')" "$LOG"
}

t_contracts() {
  LOG="$LOGS/${PHASE}_contracts.log"
  python3 -I "$OUT/scripts/validate-contracts.py" >"$LOG" 2>&1
  RC=$?
  if docker_ok; then
    local c="contracts-sql-$$" pw
    pw="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
    docker run --rm -d --name "$c" -e POSTGRES_PASSWORD="$pw" postgres:16.15-alpine >/dev/null
    for _ in $(seq 60); do docker exec "$c" pg_isready -U postgres -q 2>/dev/null && break; sleep 1; done
    if docker exec -i "$c" psql -U postgres -v ON_ERROR_STOP=1 -q <"$OUT/docs/02_contracts/database.sql" >>"$LOG" 2>&1; then
      echo "PASS database.sql: applied to postgres:16.15-alpine" >>"$LOG"
    else
      echo "FAIL database.sql" >>"$LOG"; RC=1
    fi
    docker stop "$c" >/dev/null
  else
    echo "SKIP database.sql: Docker Engine not reachable" >>"$LOG"
  fi
  line contracts $RC "$(grep -c '^PASS' "$LOG") pass, $(grep -c '^FAIL' "$LOG") fail" "$LOG"
}

ALL="be-build be-format be-lint be-cpd be-spotbugs be-test be-mutation be-depcheck fe-format fe-lint fe-typecheck fe-build fe-test fe-mutation fe-cpd fe-audit fe-audit-prod semgrep gitleaks cloc contracts"
TOOLS="${*:-$ALL}"
for t in $TOOLS; do
  fn="t_${t//-/_}"
  if declare -F "$fn" >/dev/null; then "$fn"; else skip "$t" "unknown tool"; fi
done
