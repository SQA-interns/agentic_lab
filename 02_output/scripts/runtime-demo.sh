#!/usr/bin/env bash
# Phase 6 runtime demonstration against the local compose stack (DoD-06, DoD-P01..P04, NFR-01,
# NFR-02, NFR-04). Prints what was run and observed; never prints a secret value.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$ROOT/02_output/scripts/compose.sh"
FRONT=http://127.0.0.1:8081
MAILPIT=http://127.0.0.1:8025
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
USER_="$(sed -n 's/^ORGANIZER_USERNAME=//p' "$ROOT/.env" | tail -1 | tr -d '\r')"
PASS_="$(sed -n 's/^ORGANIZER_PASSWORD=//p' "$ROOT/.env" | tail -1 | tr -d '\r')"
STAMP="$(date -u +%s)"
EXT="ext-$STAMP@example.si"
STU="stu-$STAMP@example.si"

step() { printf '\n== %s\n' "$*"; }

step "build backend jar and start the stack"
(cd "$ROOT/02_output/backend" && ./mvnw -B -q -DskipTests package) && echo "jar built"
"$COMPOSE" up -d --build --wait 2>&1 | grep -E 'Healthy|Started|Error' | sort -u
docker ps --filter name=registration- --format '{{.Names}} {{.Status}}' | sort

step "NFR-04 health and readiness"
curl -s http://127.0.0.1:8080/actuator/health/liveness; echo
curl -s http://127.0.0.1:8080/actuator/health/readiness; echo

step "frontend page and SB-10 headers (static, via nginx)"
curl -s -o /dev/null -w 'GET / -> %{http_code}\n' "$FRONT/"
curl -sI "$FRONT/" | grep -iE '^(content-security-policy|x-frame-options|x-content-type-options|referrer-policy):' | cut -c1-80
step "KP-02 /api headers (each once)"
curl -sI "$FRONT/api/registration-form/external" | grep -iE '^(content-security-policy|x-frame-options|x-content-type-options|referrer-policy|cache-control):' | sort | uniq -c | cut -c1-80

step "DoD-P01 external registration through the frontend /api (NFR-01 characters)"
curl -s -w '\n-> %{http_code}\n' -H 'Content-Type: application/json' "$FRONT/api/registrations" -d "{\"type\":\"EXTERNAL\",\"firstName\":\"Špela\",\"lastName\":\"Čebašek\",\"email\":\"$EXT\",\"organization\":\"Zavod Žar\",\"optionIds\":[\"ws-testing-ai\",\"ev-gala-dinner\"],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-pass\"}" | tee "$WORK/ext.json"
step "DoD-P01 student registration"
curl -s -w '\n-> %{http_code}\n' -H 'Content-Type: application/json' "$FRONT/api/registrations" -d "{\"type\":\"STUDENT\",\"firstName\":\"Luka\",\"lastName\":\"Kranjc\",\"email\":\"$STU\",\"studyInstitution\":\"Univerza v Mariboru\",\"studyProgramme\":\"Računalništvo\",\"studentId\":\"E1234567\",\"optionIds\":[\"ws-testing-ai\"],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-pass\"}" | tee "$WORK/stu.json"
step "rejections: duplicate email, failed anti-automation"
curl -s -o /dev/null -w 'duplicate -> %{http_code}\n' -H 'Content-Type: application/json' "$FRONT/api/registrations" -d "{\"type\":\"STUDENT\",\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"$EXT\",\"studyInstitution\":\"U\",\"studyProgramme\":\"P\",\"studentId\":\"1\",\"optionIds\":[],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"test-pass\"}"
curl -s -o /dev/null -w 'bad token -> %{http_code}\n' -H 'Content-Type: application/json' "$FRONT/api/registrations" -d "{\"type\":\"STUDENT\",\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"z-$STAMP@example.si\",\"studyInstitution\":\"U\",\"studyProgramme\":\"P\",\"studentId\":\"1\",\"optionIds\":[],\"consentIds\":[\"data-processing\"],\"recaptchaToken\":\"nope\"}"

EXT_ID="$(python3 -c 'import json,sys; print(json.loads(open(sys.argv[1]).read().split("\n")[0])["id"])' "$WORK/ext.json")"
STU_ID="$(python3 -c 'import json,sys; print(json.loads(open(sys.argv[1]).read().split("\n")[0])["id"])' "$WORK/stu.json")"

step "DoD-P02 rows in PostgreSQL"
"$COMPOSE" exec -T db psql -U registration -d registration -At -c "select id, type, first_name, last_name, email from registration where id in ('$EXT_ID','$STU_ID') order by type"
step "DoD-P02 JSON copies on the volume"
"$COMPOSE" exec -T backend sh -c "ls -l /data/json-copies/$EXT_ID.json /data/json-copies/$STU_ID.json && head -c 160 /data/json-copies/$EXT_ID.json; echo"

step "DoD-P03 emails in Mailpit"
sleep 3
for e in "$EXT" "$STU"; do
  curl -s "$MAILPIT/api/v1/search?query=%22$e%22" | python3 -c '
import json,sys
# Organizer addresses come from .env and are masked (rules.md "Secrets").
for m in json.load(sys.stdin)["messages"]:
    to = [a["Address"] if a["Address"] == sys.argv[1] else "<ORGANIZER_EMAILS>" for a in m["To"]]
    print(" ", to, "|", m["Subject"], "| attachments:", m.get("Attachments",0))' "$e"
done

step "DoD-P04 export with and without organizer access"
curl -s -u "$USER_:$PASS_" -o "$WORK/export.xlsx" -w 'with credentials -> %{http_code} %{content_type}\n' "$FRONT/api/registrations/export"
python3 - "$WORK/export.xlsx" "$EXT" "$STU" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
text = "".join(z.read(n).decode() for n in z.namelist() if n.startswith("xl/"))
print("  valid xlsx entries:", len(z.namelist()), "| contains both registrations:", sys.argv[2] in text and sys.argv[3] in text, "| Slovenian kept:", "Špela" in text and "Čebašek" in text)
PY
curl -s -o /dev/null -w 'without credentials -> %{http_code}\n' "$FRONT/api/registrations/export"
curl -s -o /dev/null -w 'wrong password -> %{http_code}\n' -u "$USER_:wrong-password-000000" "$FRONT/api/registrations/export"

step "NFR-02 recreate every container; data survives"
"$COMPOSE" down 2>&1 | grep -c Removed | sed 's/^/containers removed: /'
"$COMPOSE" up -d --wait 2>&1 | grep -cE 'Healthy' | sed 's/^/healthy after up: /'
"$COMPOSE" exec -T db psql -U registration -d registration -At -c "select count(*) from registration where id in ('$EXT_ID','$STU_ID')"
"$COMPOSE" exec -T backend sh -c "ls /data/json-copies/$EXT_ID.json /data/json-copies/$STU_ID.json | wc -l"
