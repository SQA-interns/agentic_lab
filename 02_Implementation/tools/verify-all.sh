#!/usr/bin/env bash
# Runs every Definition-of-Done check against the current working tree and records each
# command's output/exit code via tools/metrics.py (evidence: 03_Metrics/evidence/commands/).
# Usage: tools/verify-all.sh <suite-label>   e.g. first-complete | final
set -uo pipefail
cd "$(dirname "$0")/.."
SUITE=${1:?suite label required}
export PATH="$HOME/.nvm/versions/node/v22.23.3/bin:$HOME/.local/bin:$PATH"
M="python3 tools/metrics.py"
SEC=../03_Metrics/evidence/security
mkdir -p "$SEC" ../03_Metrics/evidence/reports
declare -A RESULT

run() { # id cwd cmd...
  local id=$1 cwd=$2; shift 2
  $M run "${SUITE}__${id}" --cwd "$cwd" -- "$@" > /dev/null 2>&1
  RESULT[$id]=$?
  printf '%-28s exit=%s\n' "$id" "${RESULT[$id]}"
}

# --- D-02 build/tests, D-03 static, D-04 architecture (ArchUnit inside unit tests)
run backend-verify backend ./mvnw -B clean verify
cp -r backend/target/surefire-reports ../03_Metrics/evidence/reports/${SUITE}-surefire 2>/dev/null
cp -r backend/target/failsafe-reports ../03_Metrics/evidence/reports/${SUITE}-failsafe 2>/dev/null
cp backend/target/site/jacoco-unit/jacoco.csv ../03_Metrics/evidence/reports/${SUITE}-jacoco-unit.csv 2>/dev/null
cp backend/target/site/jacoco-it/jacoco.csv ../03_Metrics/evidence/reports/${SUITE}-jacoco-it.csv 2>/dev/null
run backend-static backend ./mvnw -B spotless:check compile spotbugs:check pmd:check pmd:cpd-check -DskipTests
cp backend/target/spotbugsXml.xml backend/target/pmd.xml backend/target/cpd.xml ../03_Metrics/evidence/reports/ 2>/dev/null
run frontend-ci frontend npm ci --no-audit --no-fund
run frontend-format frontend npm run format:check
run frontend-lint frontend npm run lint
run frontend-typecheck frontend npm run typecheck
run frontend-unit frontend npm run test:coverage
run frontend-build frontend npm run build
run frontend-jscpd frontend npm run cpd
cp frontend/report/jscpd/jscpd-report.json ../03_Metrics/evidence/reports/${SUITE}-jscpd.json 2>/dev/null
cp frontend/coverage/coverage-summary.json ../03_Metrics/evidence/reports/${SUITE}-frontend-coverage.json 2>/dev/null

# --- D-05 security scanners
run semgrep . semgrep scan --metrics=off --config p/default --config p/java --config p/typescript --config p/react --config p/secrets --config p/dockerfile --json --output "$SEC/semgrep.json" backend/src frontend/src frontend/e2e backend/Dockerfile frontend/Dockerfile frontend/nginx.conf compose.yaml
run dependency-check backend ./mvnw -B org.owasp:dependency-check-maven:13.0.0:check -DskipTests
cp backend/target/dependency-check-report.html backend/target/dependency-check-report.json "$SEC/" 2>/dev/null
(cd frontend && npm audit --json > "../$SEC/npm-audit.json" 2>/dev/null)
run npm-audit frontend npm audit --audit-level=high
run security-summary . python3 tools/security_summary.py

# --- D-06/D-07 container runtime
run compose-build . docker compose build
run compose-up . docker compose up -d --wait
run e2e frontend npm run e2e
run m3-catalog-restart . ./tools/m3-catalog-restart.sh
run probe-mail . python3 tools/runtime_probe.py mail
run probe-idempotency . python3 tools/runtime_probe.py idempotency
run probe-storage . python3 tools/runtime_probe.py storage
run probe-export . python3 tools/runtime_probe.py export
run probe-prod-isolation . python3 tools/runtime_probe.py prod-isolation
run probe-recreate . python3 tools/runtime_probe.py recreate
run e2e-after-recreate frontend npm run e2e

FAILED=0
for k in "${!RESULT[@]}"; do [[ ${RESULT[$k]} -ne 0 ]] && FAILED=1; done
echo "suite=$SUITE failed=$FAILED"
exit $FAILED
