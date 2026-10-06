#!/usr/bin/env bash
# Phase 6 runtime demonstration against the local compose stack (DoD-06, DoD-P01..P04, NFR-02,
# NFR-04, KP-01, KP-02). Secrets are read from ../.env into variables and never printed.
# Usage: runtime-demo.sh   (writes nothing but stdout; redirect to 02_output/logs/)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/02_output"
ENV_FILE="$ROOT/.env"
val() { sed -n "s/^$1=//p" "$ENV_FILE" | tr -d '\r'; }
ORG_USER="$(val ORGANIZER_USERNAME)"
ORG_PASS="$(val ORGANIZER_PASSWORD)"
FRONTEND="http://127.0.0.1:${FRONTEND_PORT:-8090}"
MAILPIT="http://127.0.0.1:${MAILPIT_PORT:-8025}"
RUN="$(date +%s)"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$OUT/docker-compose.yml")
FAIL=0

step() { printf '\n== %s\n' "$*"; }
check() { # description, condition result (0 = ok)
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; FAIL=1; fi
}
header_count() { grep -ic "^$2:" <<<"$1"; }

step "start the stack from scratch (docker compose up --build --wait)"
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 300 >/dev/null 2>&1
check "stack started and healthy" $?
"${COMPOSE[@]}" ps --format '{{.Service}} {{.State}} {{.Health}}'

step "NFR-04 health and readiness"
r=$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/healthz"); check "frontend /healthz -> $r" $([ "$r" = 200 ]; echo $?)
"${COMPOSE[@]}" exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health/readiness; echo
check "every container reports healthy" $("${COMPOSE[@]}" ps --format '{{.Health}}' | grep -vq '^healthy$'; [ $? -ne 0 ]; echo $?)

step "SB-10 / KP-02 security headers exactly once (HTML and /api)"
for path in / /api/options /assets/missing.js; do
  h=$(curl -s -D - -o /dev/null "$FRONTEND$path")
  for name in Content-Security-Policy X-Content-Type-Options X-Frame-Options Referrer-Policy; do
    n=$(header_count "$h" "$name"); check "$path $name count=$n" $([ "$n" = 1 ]; echo $?)
  done
done

step "KP-01 client-supplied Host is not reflected"
h=$(curl -s -D - -o /dev/null -H 'Host: evil.example' "$FRONTEND/assets")
grep -i '^location:' <<<"$h"
check "no redirect to evil.example" $(grep -qi 'evil.example' <<<"$h"; [ $? -ne 0 ]; echo $?)
h=$(curl -s -D - -o /dev/null -H 'Host: evil.example' "$FRONTEND/api/options")
check "/api via proxy with forged Host still 200" $(grep -q '^HTTP/1.1 200' <<<"$h"; echo $?)

step "DoD-P01 one external and one student registration (NFR-01 characters)"
EXT_EMAIL="ext.$RUN@example.si"; STU_EMAIL="stu.$RUN@example.si"
ext=$(curl -s -w ' %{http_code}' -H 'Content-Type: application/json' "$FRONTEND/api/registrations" -d @- <<JSON
{"type":"EXTERNAL","firstName":"Špela","lastName":"Čeh Žagar","email":"$EXT_EMAIL","organization":"Občina Škofja Loka","optionIds":["ws-ai","meal-lunch-1"],"consentGiven":true,"recaptchaToken":"test-pass"}
JSON
)
echo "external: $ext"; check "external registration 201" $(grep -q ' 201$' <<<"$ext"; echo $?)
stu=$(curl -s -w ' %{http_code}' -H 'Content-Type: application/json' "$FRONTEND/api/registrations" -d @- <<JSON
{"type":"STUDENT","firstName":"Luka","lastName":"Kranjc","email":"$STU_EMAIL","studyInstitution":"Univerza v Ljubljani","studyProgramme":"Računalništvo","studentId":"6326$RUN","optionIds":["ev-reception"],"consentGiven":true,"recaptchaToken":"test-pass"}
JSON
)
echo "student: $stu"; check "student registration 201" $(grep -q ' 201$' <<<"$stu"; echo $?)

step "DoD-P02 rows in PostgreSQL and JSON copies on the volume"
"${COMPOSE[@]}" exec -T postgres psql -U registration -d registration -At -c \
  "SELECT registration_type, first_name, last_name, (SELECT count(*) FROM registration_option o WHERE o.registration_id = r.id) AS options FROM registration r ORDER BY received_at"
rows=$("${COMPOSE[@]}" exec -T postgres psql -U registration -d registration -At -c "SELECT count(*) FROM registration")
check "2 rows stored (got $rows)" $([ "$rows" = 2 ]; echo $?)
"${COMPOSE[@]}" exec -T backend ls -l /data/json-copies
copies=$("${COMPOSE[@]}" exec -T backend sh -c 'ls /data/json-copies/*.json | wc -l')
check "2 JSON copies (got $copies)" $([ "$copies" = 2 ]; echo $?)
"${COMPOSE[@]}" exec -T backend sh -c 'cat /data/json-copies/*.json' | grep -E '"(firstName|lastName|organization)"'

step "DoD-P03 participant and organizer emails in Mailpit"
sleep 3
curl -s "$MAILPIT/api/v1/messages" | python3 -c '
import json,sys
m=json.load(sys.stdin)["messages"]
for x in m: print(x["Subject"], "| attachments:", x["Attachments"])
p=[x for x in m if x["Subject"].startswith("Registration received")]
o=[x for x in m if x["Subject"].startswith("New ") and x["Attachments"]==1]
print("participant mails:", len(p), "organizer mails with JSON:", len(o))
sys.exit(0 if len(p)==2 and len(o)==2 else 1)'
check "2 participant + 2 organizer emails with attachment" $?

step "DoD-P04 export: organizer gets a valid workbook, anonymous is refused"
code=$(curl -s -o /tmp/registrations-demo.xlsx -w '%{http_code}' -u "$ORG_USER:$ORG_PASS" "$FRONTEND/api/admin/registrations/export")
check "organizer export -> $code" $([ "$code" = 200 ]; echo $?)
python3 - /tmp/registrations-demo.xlsx <<'PY'
import sys, zipfile, re
z = zipfile.ZipFile(sys.argv[1])
text = "".join(z.read(n).decode() for n in z.namelist() if n.startswith("xl/") and n.endswith(".xml"))
for word in ("Špela", "Čeh Žagar", "Občina Škofja Loka", "Računalništvo"):
    print(word, "in workbook:", word in text)
print("valid xlsx parts:", len(z.namelist()))
sys.exit(0 if all(w in text for w in ("Špela", "Čeh Žagar", "Računalništvo")) else 1)
PY
check "workbook opens and keeps č/š/ž" $?
rm -f /tmp/registrations-demo.xlsx
code=$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/api/admin/registrations/export"); check "anonymous export -> $code" $([ "$code" = 401 ]; echo $?)
code=$(curl -s -o /dev/null -w '%{http_code}' -u "$ORG_USER:wrong-password" "$FRONTEND/api/admin/registrations/export"); check "wrong password -> $code" $([ "$code" = 401 ]; echo $?)

step "ES-07 / SB-07 no personal data in container logs"
logs=$("${COMPOSE[@]}" logs backend frontend 2>&1)
for word in "$EXT_EMAIL" "$STU_EMAIL" "Špela" "Kranjc"; do
  check "logs do not contain a submitted value" $(grep -qF "$word" <<<"$logs"; [ $? -ne 0 ]; echo $?)
done
check "logs contain no stack traces" $(grep -q 'at si.konferenca' <<<"$logs"; [ $? -ne 0 ]; echo $?)

step "NFR-02 data survives recreation of every container (down, up)"
"${COMPOSE[@]}" down >/dev/null 2>&1
"${COMPOSE[@]}" up -d --wait --wait-timeout 300 >/dev/null 2>&1
rows=$("${COMPOSE[@]}" exec -T postgres psql -U registration -d registration -At -c "SELECT count(*) FROM registration")
copies=$("${COMPOSE[@]}" exec -T backend sh -c 'ls /data/json-copies/*.json | wc -l')
check "after recreation rows=$rows copies=$copies" $([ "$rows" = 2 ] && [ "$copies" = 2 ]; echo $?)

step "SB-11 processes run as non-root"
echo "backend uid: $("${COMPOSE[@]}" exec -T backend id -u)  frontend uid: $("${COMPOSE[@]}" exec -T frontend id -u)"
check "backend non-root" $([ "$("${COMPOSE[@]}" exec -T backend id -u)" != 0 ]; echo $?)
check "frontend non-root" $([ "$("${COMPOSE[@]}" exec -T frontend id -u)" != 0 ]; echo $?)

step "result"
[ $FAIL -eq 0 ] && echo "RUNTIME DEMO PASSED" || echo "RUNTIME DEMO FAILED"
exit $FAIL
