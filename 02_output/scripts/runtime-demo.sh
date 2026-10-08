#!/usr/bin/env bash
# Runtime demonstration on the local stack (DoD-06, DoD-P01..P04, NFR-02, NFR-04, SB-11, KP-02).
# Starts a fresh stack, exercises the core flows and prints observations only (no secret values).
# Usage: scripts/runtime-demo.sh   (requires the backend jar: cd backend && ./mvnw -B package -DskipTests)
set -uo pipefail
OUT="$(cd "$(dirname "$0")/.." && pwd)"
C="$OUT/scripts/compose.sh"
BASE="http://127.0.0.1:8081"
MAIL="http://127.0.0.1:8025/api/v1"
ORG_USER="$(sed -n 's/^ORGANIZER_USERNAME=//p' "$OUT/../.env" | tr -d '\r')"
ORG_PASS="$(sed -n 's/^ORGANIZER_PASSWORD=//p' "$OUT/../.env" | tr -d '\r')"
psql() { "$C" exec -T db psql -U registration -d registration -tA -c "$1"; }
step() { printf '\n== %s\n' "$*"; }
RUN="$(date -u +%s)"

step "fresh stack (down -v, up --build --wait)"
"$C" down -v >/dev/null 2>&1
"$C" up -d --build --wait >/dev/null 2>&1; echo "up exit=$?"
"$C" ps --format '{{.Service}} {{.Status}}'

step "NFR-04 health and readiness"
"$C" exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health/liveness; echo
"$C" exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health/readiness; echo

step "SB-11 processes are not root"
echo "backend uid=$("$C" exec -T backend id -u) frontend uid=$("$C" exec -T frontend id -u)"

step "GET /api/form"
curl -s "$BASE/api/form" | head -c 160; echo

step "DoD-P01 one external and one student registration"
EXT="demo-ext-$RUN@example.si"; STU="demo-stu-$RUN@example.si"
curl -s -o /dev/null -w 'external HTTP %{http_code}\n' -H 'Content-Type: application/json' "$BASE/api/registrations" \
  -d "{\"type\":\"EXTERNAL\",\"firstName\":\"Čedomir\",\"lastName\":\"Šuštaršič\",\"email\":\"$EXT\",\"organization\":\"Žalec d.o.o.\",\"optionIds\":[\"ws-testing\",\"meal-dinner\"],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-mode-pass\"}"
curl -s -o /dev/null -w 'student HTTP %{http_code}\n' -H 'Content-Type: application/json' "$BASE/api/registrations" \
  -d "{\"type\":\"STUDENT\",\"firstName\":\"Žiga\",\"lastName\":\"Kovač\",\"email\":\"$STU\",\"studyInstitution\":\"Univerza v Ljubljani\",\"studyProgramme\":\"Računalništvo\",\"studentId\":\"63210001\",\"optionIds\":[\"ev-career-fair\"],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-mode-pass\"}"
curl -s -o /dev/null -w 'invalid token HTTP %{http_code}\n' -H 'Content-Type: application/json' "$BASE/api/registrations" \
  -d "{\"type\":\"STUDENT\",\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"demo-bad-$RUN@example.si\",\"studyInstitution\":\"U\",\"studyProgramme\":\"P\",\"studentId\":\"1\",\"optionIds\":[],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"wrong\"}"

step "DoD-P02 rows in PostgreSQL and JSON copies"
psql "select type, first_name, last_name, (select count(*) from registration_option o where o.registration_id = r.id) from registration r order by received_at"
"$C" exec -T backend sh -c 'ls /data/registrations | sed "s/^/copy: /"'

step "DoD-P03 emails in Mailpit (after 3 s)"
sleep 3
curl -s "$MAIL/messages" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{for(const m of JSON.parse(s).messages)console.log(`${m.Subject} | to ${m.To.map(t=>t.Address.endsWith("@example.si")?t.Address:"<organizer from .env>").join(",")} | attachments ${m.Attachments}`)})'

step "DoD-P04 export with and without organizer access"
EXPORT="$(mktemp)"
curl -s -o "$EXPORT" -w 'organizer HTTP %{http_code} %{content_type} %{size_download} bytes\n' -u "$ORG_USER:$ORG_PASS" "$BASE/api/export"
head -c 2 "$EXPORT"; echo " (zip magic)"; rm -f "$EXPORT"
curl -s -o /dev/null -w 'no credentials HTTP %{http_code}\n' "$BASE/api/export"
curl -s -o /dev/null -w 'wrong password HTTP %{http_code}\n' -u "$ORG_USER:wrong-password-000" "$BASE/api/export"

step "KP-02 each security header once"
for u in / /api/form; do
  echo "$u: $(curl -s -D - -o /dev/null "$BASE$u" | tr -d '\r' | grep -ciE '^(content-security-policy|x-frame-options|x-content-type-options|referrer-policy):') headers, duplicates: $(curl -s -D - -o /dev/null "$BASE$u" | tr -d '\r' | cut -d: -f1 | sort | uniq -d | tr '\n' ' ')"
done

step "NFR-02 data survives recreating every container (down, up)"
"$C" down >/dev/null 2>&1
"$C" up -d --wait >/dev/null 2>&1; echo "up exit=$?"
echo "rows after recreate: $(psql 'select count(*) from registration')"
"$C" exec -T backend sh -c 'echo "copies after recreate: $(ls /data/registrations | wc -l)"'
curl -s -o /dev/null -w 'export after recreate HTTP %{http_code}\n' -u "$ORG_USER:$ORG_PASS" "$BASE/api/export"
curl -s -o /dev/null -w 'same email again HTTP %{http_code}\n' -H 'Content-Type: application/json' "$BASE/api/registrations" \
  -d "{\"type\":\"EXTERNAL\",\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"$EXT\",\"organization\":\"O\",\"optionIds\":[],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-mode-pass\"}"

step "ES-07 backend log carries no participant data"
"$C" logs backend 2>&1 | grep -cE "$EXT|$STU|Čedomir|Žiga|Šuštaršič" | sed 's/^/log lines with personal data: /'

step "teardown"
"$C" down -v >/dev/null 2>&1; echo "down exit=$?"
