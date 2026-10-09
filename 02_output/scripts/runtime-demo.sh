#!/usr/bin/env bash
# Runtime demonstration against the running local stack (DoD-06, DoD-P01..P04, NFR-01, NFR-04).
# Called by verify.sh (tool "runtime"); prints what was run and observed. Never prints a secret:
# organizer addresses are reduced to counts.
#
#   bash 02_output/scripts/runtime-demo.sh <run-label> <secrets.sh path> <windows repo root>

set -u
LABEL="${1:?label}"
SECRETS="${2:?secrets.sh}"
WROOT="${3:?windows root}"
BASE="http://127.0.0.1:8080"
MAILPIT="http://127.0.0.1:8025"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/logs"
STAMP="$(date -u +%Y%m%d%H%M%S)"

step() { printf '\n== %s\n' "$1"; }

step "containers and health checks (NFR-04)"
docker ps --filter label=com.docker.compose.project=registration \
  --format '{{.Label "com.docker.compose.service"}}: {{.Status}}'
curl -s "$BASE/api/registration-form" -o /dev/null -w 'GET /api/registration-form through nginx: %{http_code}\n'
docker exec registration-backend-1 wget -q -O - http://127.0.0.1:8080/actuator/health/readiness
echo

register() {
  curl -s -X POST "$BASE/api/registrations" -H 'Content-Type: application/json' \
    -H "Origin: $BASE" --data-binary @- -w '\n%{http_code}'
}

step "external registration (DoD-P01, NFR-01)"
EXTERNAL_EMAIL="runtime.external.$LABEL.$STAMP@example.si"
EXTERNAL=$(register <<JSON
{"type":"EXTERNAL","firstName":"Črtomir","lastName":"Šuštar Žižek","email":"$EXTERNAL_EMAIL","organization":"Univerza v Ljubljani","optionIds":["ws-ai","ev-reception","meal-lunch-1"],"consentIds":["data-processing"],"captchaToken":"test-valid"}
JSON
)
echo "$EXTERNAL"
EXTERNAL_ID=$(echo "$EXTERNAL" | head -n 1 | sed -n 's/.*"registrationId":"\([^"]*\)".*/\1/p')

step "student registration (DoD-P01)"
STUDENT_EMAIL="runtime.student.$LABEL.$STAMP@example.si"
STUDENT=$(register <<JSON
{"type":"STUDENT","firstName":"Žana","lastName":"Kovač","email":"$STUDENT_EMAIL","studyInstitution":"Fakulteta za računalništvo in informatiko","studyProgramme":"Računalništvo in informatika","studentId":"63209999","optionIds":["ws-security","other-career-fair"],"consentIds":["data-processing"],"captchaToken":"test-valid"}
JSON
)
echo "$STUDENT"
STUDENT_ID=$(echo "$STUDENT" | head -n 1 | sed -n 's/.*"registrationId":"\([^"]*\)".*/\1/p')

step "rejected registration: wrong anti-automation token"
register <<JSON
{"type":"EXTERNAL","firstName":"Bot","lastName":"Bot","email":"bot.$STAMP@example.si","organization":"x","optionIds":[],"consentIds":["data-processing"],"captchaToken":"forged"}
JSON
echo

step "database rows (DoD-P02)"
docker exec registration-postgres-1 psql -U registration -d registration -c \
  "SELECT id, type, first_name, last_name, organization, study_institution, registered_at FROM registration WHERE id IN ('$EXTERNAL_ID', '$STUDENT_ID') ORDER BY registered_at;"
docker exec registration-postgres-1 psql -U registration -d registration -c \
  "SELECT registration_id, option_id, category FROM registration_option WHERE registration_id IN ('$EXTERNAL_ID', '$STUDENT_ID') ORDER BY 1, 2;"

step "JSON copies on the volume (DoD-P02)"
docker exec registration-backend-1 ls -l "/data/registrations/registration-$EXTERNAL_ID.json" "/data/registrations/registration-$STUDENT_ID.json"
docker exec registration-backend-1 cat "/data/registrations/registration-$EXTERNAL_ID.json"
echo

step "emails in Mailpit (DoD-P03)"
sleep 2
curl -s "$MAILPIT/api/v1/messages?limit=200" > "$OUT_DIR/.mailpit-$STAMP.json"
node - "$OUT_DIR/.mailpit-$STAMP.json" "$EXTERNAL_EMAIL" "$STUDENT_EMAIL" "$EXTERNAL_ID" "$STUDENT_ID" "$MAILPIT" <<'NODE'
const [file, extEmail, stuEmail, extId, stuId, mailpit] = process.argv.slice(2);
const list = JSON.parse(require("fs").readFileSync(file, "utf8")).messages;
const to = (m, a) => m.To.some((t) => t.Address.toLowerCase() === a.toLowerCase());
(async () => {
  for (const [label, email, id] of [["external", extEmail, extId], ["student", stuEmail, stuId]]) {
    const participant = list.filter((m) => to(m, email));
    console.log(`${label} participant email: ${participant.length} message(s), subject "${participant[0]?.Subject}"`);
    const organizer = [];
    for (const m of list.filter((m) => m.Subject.startsWith("New registration"))) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${m.ID}`)).json();
      if (full.Text.includes(`Registration ID: ${id}`)) organizer.push(full);
    }
    const o = organizer[0];
    console.log(
      `${label} organizer email: ${organizer.length} message(s) to ${o ? o.To.length : 0} organizer address(es),` +
        ` attachment ${o ? o.Attachments.map((a) => a.FileName + " (" + a.ContentType + ")").join(", ") : "none"}`,
    );
    if (o) {
      const part = await (await fetch(`${mailpit}/api/v1/message/${o.ID}/part/${o.Attachments[0].PartID}`)).json();
      console.log(`${label} attachment registrationId matches: ${part.registrationId === id}`);
    }
  }
})();
NODE
rm -f "$OUT_DIR/.mailpit-$STAMP.json"

step "organizer workbook (DoD-P04)"
MSYS_NO_PATHCONV=1 bash "$SECRETS" run ORGANIZER_USERNAME,ORGANIZER_PASSWORD -- \
  docker run --rm --network registration_default -e ORGANIZER_USERNAME -e ORGANIZER_PASSWORD \
  -v "$WROOT/02_output/scripts:/demo" -v "$WROOT/02_output/logs:/out" \
  mcr.microsoft.com/playwright:v1.63.0-noble node /demo/organizer-download.mjs
WORKBOOK="$OUT_DIR/organizer-workbook.xlsx"
unzip -l "$WORKBOOK" | grep -E "xl/worksheets/sheet1.xml|xl/sharedStrings.xml"
for text in "Registration ID" "Consents" "$EXTERNAL_ID" "$STUDENT_ID" "Črtomir" "Šuštar Žižek" "Žana"; do
  if unzip -p "$WORKBOOK" xl/sharedStrings.xml | grep -qF "$text"; then echo "workbook contains: $text"; else echo "workbook MISSING: $text"; fi
done
rm -f "$WORKBOOK"

step "personal data in the backend log (ES-07)"
printf 'backend log lines containing the demo email addresses: %s\n' \
  "$(docker logs registration-backend-1 2>&1 | grep -cE "$EXTERNAL_EMAIL|$STUDENT_EMAIL")"
printf 'backend log lines containing the demo names: %s\n' \
  "$(docker logs registration-backend-1 2>&1 | grep -cE 'Črtomir|Žana')"

echo "$EXTERNAL_ID $STUDENT_ID" > "$OUT_DIR/.runtime-ids"
