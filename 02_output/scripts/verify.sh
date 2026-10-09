#!/usr/bin/env bash
# Runs every check and scanner that exists so far (rules.md "Output size").
# Usage: 02_output/scripts/verify.sh <phase> [tool ...]
#   Without tool names, every tool runs. Full output: 02_output/logs/<phase>_<tool>.log
#   Prints one line per tool: tool, exit code, key numbers, log path.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/02_output"
LOGS="$OUT/logs"
BACKEND="$OUT/backend"
FRONTEND="$OUT/frontend"
PHASE="${1:?usage: verify.sh <phase> [tool ...]}"
shift
mkdir -p "$LOGS"
# Node.js from tech-stack.md; nvm installs are not on a non-interactive PATH.
NODE_BIN="${NVM_DIR:-$HOME/.nvm}/versions/node/v24.13.0/bin"
[ -d "$NODE_BIN" ] && PATH="$NODE_BIN:$PATH"

# Image tags from tech-stack.md
SEMGREP_IMAGE="semgrep/semgrep:1.177.0"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1"
CLOC_IMAGE="aldanial/cloc:2.10"

line() { printf '%-18s exit=%-3s %-50s %s\n' "$1" "$2" "$3" "${4#"$ROOT"/}"; }

# Runs a command in a directory, logs everything, returns the exit code.
run() {
  local log="$1" dir="$2"
  shift 2
  # Redact generated or configured passwords that frameworks print (rules.md "Secrets").
  (cd "$dir" && "$@") 2>&1 | sed -E 's/(password: )[^[:space:]]+/\1[REDACTED]/I' >"$log"
  return "${PIPESTATUS[0]}"
}

mvn_tool() {
  local name="$1"
  shift
  local log="$LOGS/${PHASE}_${name}.log"
  run "$log" "$BACKEND" ./mvnw -B "$@"
  local rc=$?
  local tests
  tests="$(grep -E 'Tests run:.*Fail' "$log" | tail -1 | sed 's/^\[[A-Z]*\] *//')"
  line "$name" "$rc" "${tests:-$(grep -oE 'BUILD (SUCCESS|FAILURE)' "$log" | tail -1)}" "$log"
}

backend_build() { mvn_tool backend-build package -DskipTests; }
backend_test() { mvn_tool backend-test verify; }
backend_check() {
  local log="$LOGS/${PHASE}_backend-check.log"
  run "$log" "$BACKEND" ./mvnw -B spotless:check pmd:check pmd:cpd-check spotbugs:check
  local rc=$?
  local pmd bugs
  pmd="$(grep -cE 'PMD Failure' "$log")"
  bugs="$(grep -oE 'BugInstance size is [0-9]+' "$log" | grep -oE '[0-9]+$' | tail -1)"
  line backend-check "$rc" "pmd=${pmd} spotbugs=${bugs:-?}" "$log"
}
backend_mutation() { mvn_tool backend-mutation test-compile org.pitest:pitest-maven:mutationCoverage; }

dependency_check() {
  local log="$LOGS/${PHASE}_dependency-check.log"
  # The key is passed to the tool without being shown (rules.md "Secrets").
  run "$log" "$BACKEND" env NVD_API_KEY="$(sed -n 's/^NVD_API_KEY=//p' "$ROOT/.env" | tr -d '\r')" \
    ./mvnw -B org.owasp:dependency-check-maven:12.1.0:check
  local rc=$?
  local json="$BACKEND/target/dependency-check-report.json"
  local counts="report missing"
  if [ -f "$json" ]; then
    counts="$(python3 - "$json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
c = {"critical": 0, "high": 0, "medium": 0, "low": 0}
for dep in d.get("dependencies", []):
    for v in dep.get("vulnerabilities", []):
        s = (v.get("cvssv3") or {}).get("baseScore") or (v.get("cvssv2") or {}).get("score") or 0
        k = "critical" if s >= 9 else "high" if s >= 7 else "medium" if s >= 4 else "low"
        c[k] += 1
print(" ".join(f"{k}={n}" for k, n in c.items()))
PY
)"
    cp "$json" "$LOGS/${PHASE}_dependency-check.json"
  fi
  line dependency-check "$rc" "$counts" "$log"
}

frontend_npm() {
  local name="$1"
  shift
  local log="$LOGS/${PHASE}_${name}.log"
  if [ ! -f "$FRONTEND/package.json" ] || ! command -v npm >/dev/null 2>&1; then
    line "$name" "-" "skipped: frontend or npm not available" "$log"
    return
  fi
  run "$log" "$FRONTEND" npm "$@"
  local rc=$?
  local key
  key="$(grep -E 'Tests +[0-9]|passed|failed|problems?|vulnerabilit' "$log" | tail -1 | tr -s ' ')"
  line "$name" "$rc" "${key:0:50}" "$log"
}
frontend_build() { frontend_npm frontend-build run build; }
frontend_test() { frontend_npm frontend-test test; }
frontend_check() { frontend_npm frontend-check run check; }
frontend_e2e() { frontend_npm frontend-e2e run e2e; }
npm_audit() { frontend_npm npm-audit audit --audit-level=high; }

semgrep() {
  local log="$LOGS/${PHASE}_semgrep.log"
  docker run --rm -v "$OUT:/src" -w /src "$SEMGREP_IMAGE" \
    semgrep scan --config p/default --metrics off --error \
    --exclude logs --exclude node_modules --exclude target --exclude dist \
    --json --output /src/logs/"${PHASE}"_semgrep.json >"$log" 2>&1
  local rc=$?
  local n
  n="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["results"]))' \
    "$LOGS/${PHASE}_semgrep.json" 2>/dev/null)"
  line semgrep "$rc" "findings=${n:-?}" "$log"
}

gitleaks() {
  local log="$LOGS/${PHASE}_gitleaks.log"
  # Only this run's commits (D-01): history before the starting commit belongs to earlier runs.
  local start
  start="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["startCommit"])' \
    "$ROOT/03_statistics/run-log.json")"
  docker run --rm -v "$ROOT:/repo" "$GITLEAKS_IMAGE" \
    git /repo --no-banner --redact --log-opts="${start}..HEAD" --report-format json \
    --report-path /repo/02_output/logs/"${PHASE}"_gitleaks.json >"$log" 2>&1
  local rc=$?
  local n
  n="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))))' \
    "$LOGS/${PHASE}_gitleaks.json" 2>/dev/null)"
  line gitleaks "$rc" "leaks=${n:-?}" "$log"
}

cloc() {
  local log="$LOGS/${PHASE}_cloc.log"
  docker run --rm -v "$OUT:/src" -w /src "$CLOC_IMAGE" \
    --exclude-dir=node_modules,target,dist,logs,coverage,reports . >"$log" 2>&1
  local rc=$?
  line cloc "$rc" "$(grep -E '^SUM:' "$log" | tr -s ' ')" "$log"
}

contracts() {
  local log="$LOGS/${PHASE}_contracts.log" dir="$OUT/docs/02_contracts" rc=0
  : >"$log"
  for f in registration-api.openapi.yaml recaptcha-verify.openapi.yaml; do
    (cd "$dir" && npx --yes @redocly/cli@2.62.0 lint --format stylish "$f") >>"$log" 2>&1 || rc=1
  done
  python3 - "$dir" >>"$log" 2>&1 <<'PY' || rc=1
import json, sys, pathlib
from jsonschema import Draft202012Validator
d = pathlib.Path(sys.argv[1])
pairs = {"ui-form.json": "ui-form.schema.json",
         "examples/conference-config.example.json": "conference-config.schema.json",
         "examples/registration-copy.example.json": "registration-copy.schema.json"}
for s in sorted(d.glob("*.schema.json")):
    Draft202012Validator.check_schema(json.loads(s.read_text("utf-8")))
    print("schema ok:", s.name)
for inst, sch in pairs.items():
    Draft202012Validator(json.loads((d / sch).read_text("utf-8"))).validate(json.loads((d / inst).read_text("utf-8")))
    print("instance ok:", inst, "against", sch)
PY
  local cid
  cid="$(docker run -d --rm -e POSTGRES_PASSWORD=contract-check postgres:16.15-alpine)"
  for _ in $(seq 1 30); do docker exec "$cid" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done
  sleep 2
  docker exec -i "$cid" psql -U postgres -v ON_ERROR_STOP=1 -q <"$dir/database.sql" >>"$log" 2>&1 \
    && echo "sql ok: database.sql" >>"$log" || rc=1
  docker rm -f "$cid" >/dev/null 2>&1
  line contracts "$rc" "$(grep -cE ' ok:|validated|Woohoo' "$log") ok lines, $(grep -ciE 'error' "$log") error lines" "$log"
}

ALL=(contracts backend_build backend_check backend_test frontend_build frontend_check frontend_test frontend_e2e
  dependency_check npm_audit semgrep gitleaks cloc)
TOOLS=("$@")
[ ${#TOOLS[@]} -eq 0 ] && TOOLS=("${ALL[@]}")
for t in "${TOOLS[@]}"; do "${t//-/_}"; done
