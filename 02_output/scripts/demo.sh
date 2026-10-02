#!/usr/bin/env bash
# Runtime demonstration against the running local stack (phase 6, step 3): the core user flows and
# DoD-P01..P04, NFR-02, NFR-04 and the runtime side of the security baseline. Uses made-up
# participants only. Prints what was run and observed; "PASS"/"FAIL" per check.
# Run through: 02_output/scripts/verify.sh <phase> demo   (after stack-up)

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/02_output"
BASE="${DEMO_BASE_URL:-http://127.0.0.1:8080}"
MAIL="${DEMO_MAILPIT_URL:-http://127.0.0.1:8025}"
# Docker Desktop on Windows needs Windows-style paths, and Git Bash must not rewrite the
# container-side paths of `compose exec` (see verify.sh).
ROOT_HOST="$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
# Temporary files are addressed relative to this directory, which every tool understands.
cd "$WORK" || exit 1
STAMP="$(date -u +%Y%m%d%H%M%S)"
EXT_EMAIL="demo.zunanji.$STAMP@example.org"
STU_EMAIL="demo.student.$STAMP@example.org"
failures=0

compose() {
  MSYS_NO_PATHCONV=1 docker compose --env-file "$ROOT_HOST/.env" \
    -f "$ROOT_HOST/02_output/docker-compose.yml" "$@"
}
step() { printf '\n== %s\n' "$*"; }
check() { # description, actual, expected
  if [ "$2" = "$3" ]; then
    printf 'PASS %s (%s)\n' "$1" "$2"
  else
    printf 'FAIL %s (observed %s, expected %s)\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}
status() { curl -s -o "body" -w '%{http_code}' "$@"; }
json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "body" | head -n 1; }
psql_value() { compose exec -T postgres psql -U registration -d registration -At -c "$1" | tr -d '\r'; }
copies() { compose exec -T backend sh -c 'ls /data/registrations | grep -c "\.json$"' | tr -d '\r'; }
# The organizer credentials go to curl through a config on stdin, never through the command line.
organizer_status() {
  printf 'user = "%s:%s"\n' \
    "$(sed -n 's/^ORGANIZER_USERNAME=//p' "$ROOT/.env" | tr -d '\r')" \
    "$(sed -n 's/^ORGANIZER_PASSWORD=//p' "$ROOT/.env" | tr -d '\r')" \
    | curl -s -K - -o "export.xlsx" -D "export.headers" -w '%{http_code}' "$BASE/api/registrations/export"
}

step "NFR-04 health and readiness"
compose ps --format '{{.Service}} {{.State}} {{.Health}}' | sort
check "services reported healthy" "$(compose ps --format '{{.Health}}' | grep -c '^healthy$')" 4
check "backend readiness" "$(compose exec -T backend wget -q -O - http://127.0.0.1:8080/actuator/health/readiness | tr -d '\r')" '{"status":"UP"}'

step "Form configuration and options (US-003)"
check "GET /api/form-config" "$(status "$BASE/api/form-config")" 200
grep -o '"mode":"[a-z]*"' "body"
check "GET /api/options" "$(status "$BASE/api/options")" 200
check "active options offered" "$(grep -o '"id":' "body" | wc -l | tr -d ' ')" 7
check "frontend page" "$(status "$BASE/")" 200

step "DoD-P01 one external and one student registration"
before_rows="$(psql_value 'select count(*) from registration')"
before_copies="$(copies)"
cat >"external.json" <<EOF
{"type":"EXTERNAL","firstName":"Živa","lastName":"Čučnik Šušteršič","email":"$EXT_EMAIL",
 "organization":"Inštitut za računalništvo Žalec","optionIds":["ws-testing","ev-dinner"],
 "consent":true,"captchaToken":"test-pass"}
EOF
cat >"student.json" <<EOF
{"type":"STUDENT","firstName":"Žan","lastName":"Košir","email":"$STU_EMAIL",
 "studyInstitution":"Univerza v Ljubljani","studyProgramme":"Računalništvo in informatika",
 "studentId":"63$STAMP","optionIds":["meal-lunch-day1"],"consent":true,"captchaToken":"test-pass"}
EOF
check "POST external registration" "$(status -H 'Content-Type: application/json' --data-binary "@external.json" "$BASE/api/registrations")" 201
EXT_ID="$(json_field id)"; echo "external id $EXT_ID"
check "POST student registration" "$(status -H 'Content-Type: application/json' --data-binary "@student.json" "$BASE/api/registrations")" 201
STU_ID="$(json_field id)"; echo "student id $STU_ID"

step "Rejections store and send nothing (AC-001-05, AC-001-12)"
sed 's/"email":"[^"]*"/"email":"not-an-email"/' "external.json" >"invalid.json"
check "POST with an invalid email" "$(status -H 'Content-Type: application/json' --data-binary "@invalid.json" "$BASE/api/registrations")" 400
grep -o '"field":"[a-zA-Z]*","code":"[a-z_]*"\|"code":"[a-z_]*","field":"[a-zA-Z]*"\|"code":"[a-z_]*"' "body" | head -n 2
sed 's/"captchaToken":"test-pass"/"captchaToken":"wrong"/' "external.json" >"nocaptcha.json"
check "POST with a failed anti-automation check" "$(status -H 'Content-Type: application/json' --data-binary "@nocaptcha.json" "$BASE/api/registrations")" 400

step "DoD-P02 each registration is in PostgreSQL and has its JSON copy"
check "rows added" "$(( $(psql_value 'select count(*) from registration') - before_rows ))" 2
psql_value "select id, type, accepted_at from registration where id in ('$EXT_ID','$STU_ID') order by accepted_at"
check "external row, Slovenian characters intact" "$(psql_value "select first_name || ' ' || last_name from registration where id = '$EXT_ID'")" "Živa Čučnik Šušteršič"
check "option rows of the external registration" "$(psql_value "select count(*) from registration_option where registration_id = '$EXT_ID'")" 2
check "JSON copies added" "$(( $(copies) - before_copies ))" 2
compose exec -T backend sh -c "ls -l /data/registrations/$EXT_ID.json /data/registrations/$STU_ID.json" | tr -d '\r' | awk '{print $1, $3, $5, $NF}'
check "JSON copy keeps the name" "$(compose exec -T backend sh -c "grep -c 'Čučnik Šušteršič' /data/registrations/$EXT_ID.json" | tr -d '\r')" 1

step "DoD-P03 participant and organizer emails arrive in Mailpit"
sleep 2
curl -s "$MAIL/api/v1/search?query=$EXT_EMAIL" >"mail.json"
grep -o '"Subject":"[^"]*"' "mail.json"
check "participant confirmation to the external participant" "$(grep -o '"Subject":"Potrditev prijave: [^"]*"' "mail.json" | wc -l | tr -d ' ')" 1
curl -s "$MAIL/api/v1/search?query=$EXT_ID" >"mail.json"
check "organizer notification for the external registration" "$(grep -o "\"Subject\":\"Nova prijava: [^\"]*($EXT_ID)\"" "mail.json" | wc -l | tr -d ' ')" 1
attachments=0
for message_id in $(grep -o '"ID":"[^"]*"' "mail.json" | cut -d'"' -f4); do
  curl -s "$MAIL/api/v1/message/$message_id" >"message.json"
  attachments=$((attachments + $(grep -o "\"FileName\":\"registration-$EXT_ID.json\"" "message.json" | wc -l)))
done
check "JSON attachment on the organizer notification" "$attachments" 1
curl -s "$MAIL/api/v1/search?query=$STU_ID" >"mail.json"
check "organizer notification for the student registration" "$(grep -o "\"Subject\":\"Nova prijava: [^\"]*($STU_ID)\"" "mail.json" | wc -l | tr -d ' ')" 1

step "DoD-P04 organizer export; refused without organizer access"
check "export with organizer credentials" "$(organizer_status)" 200
grep -i '^content-type\|^content-disposition\|^cache-control' "export.headers" | tr -d '\r'
unzip -l "export.xlsx" | grep -c 'xl/worksheets/sheet1.xml' | sed 's/^/worksheet parts: /'
unzip -p "export.xlsx" 'xl/*.xml' 'xl/worksheets/*.xml' >"workbook.xml" 2>/dev/null
check "workbook contains the external registration" "$(grep -c "$EXT_ID" "workbook.xml" | head -n 1)" 1
check "workbook contains the student registration" "$(grep -o "$STU_ID" "workbook.xml" | wc -l | tr -d ' ')" 1
check "workbook keeps Slovenian characters" "$(grep -o 'Čučnik Šušteršič' "workbook.xml" | head -n 1)" "Čučnik Šušteršič"
check "export without credentials" "$(status "$BASE/api/registrations/export")" 401
check "refusal carries no registration data" "$(grep -c "$EXT_EMAIL" "body")" 0
check "export with wrong credentials" "$(status -u 'someone:wrong-password' "$BASE/api/registrations/export")" 401

step "Security at runtime (SB-10, SB-11, SR-03)"
curl -s -D "page.headers" -o /dev/null "$BASE/"
curl -s -D "api.headers" -o /dev/null "$BASE/api/options"
for header in content-security-policy x-content-type-options x-frame-options referrer-policy; do
  check "page header $header" "$(grep -ci "^$header:" "page.headers")" 1
  check "API header $header" "$(grep -ci "^$header:" "api.headers")" 1
done
check "no cookie is set" "$(cat "page.headers" "api.headers" | grep -ci '^set-cookie:')" 0
check "backend runs as non-root" "$(compose exec -T backend id -u | tr -d '\r' | grep -c '^0$')" 0
check "frontend runs as non-root" "$(compose exec -T frontend id -u | tr -d '\r' | grep -c '^0$')" 0
compose ps --format '{{.Service}} {{.Ports}}' | sort
check "ports published beyond 127.0.0.1" "$(compose ps --format '{{.Ports}}' | grep -c '0\.0\.0\.0\|\[::\]')" 0
head -c 20000 /dev/zero | tr '\0' 'a' >"big.txt"
printf '{"type":"EXTERNAL","firstName":"%s"}' "$(cat "big.txt")" >"big.json"
check "request over the size limit" "$(status -H 'Content-Type: application/json' --data-binary "@big.json" "$BASE/api/registrations")" 413

step "ES-07 backend logs hold no personal data of the demonstration"
compose logs --no-color backend >"backend.log" 2>&1
check "log lines with a participant email or name" "$(grep -c "$EXT_EMAIL\|$STU_EMAIL\|Čučnik\|Košir" "backend.log")" 0
check "log lines naming the accepted registrations" "$(grep -c "registration $EXT_ID accepted\|registration $STU_ID accepted" "backend.log")" 2
check "stack traces in the log" "$(grep -c '^\s*at [a-z].*(.*\.java' "backend.log")" 0

step "NFR-02 data survive recreation of every container"
rows_before="$(psql_value 'select count(*) from registration')"
copies_before="$(copies)"
compose down >/dev/null 2>&1
check "containers after down" "$(compose ps -q | wc -l | tr -d ' ')" 0
compose up -d --wait >/dev/null 2>&1
check "services healthy after up" "$(compose ps --format '{{.Health}}' | grep -c '^healthy$')" 4
check "rows after recreation" "$(psql_value 'select count(*) from registration')" "$rows_before"
check "JSON copies after recreation" "$(copies)" "$copies_before"
check "export after recreation" "$(organizer_status)" 200
unzip -p "export.xlsx" 'xl/*.xml' 'xl/worksheets/*.xml' >"workbook.xml" 2>/dev/null
check "workbook still contains the external registration" "$(grep -o "$EXT_ID" "workbook.xml" | wc -l | tr -d ' ')" 1

printf '\n== demonstration finished: %s failed check(s)\n' "$failures"
exit "$failures"
