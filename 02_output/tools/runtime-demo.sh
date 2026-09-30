#!/usr/bin/env bash
# Runtime demonstration on the local Compose stack (DoD-06, NFR-02). Synthetic data only.
# Usage: tools/runtime-demo.sh   (stack must be running; organizer password in .local/organizer-demo-password)
set -euo pipefail
cd "$(dirname "$0")/.."
APP=${APP:-http://127.0.0.1:18080}
MP=${MP:-http://127.0.0.1:18025}
ORIGIN="Origin: http://127.0.0.1:18080"
PW=$(cat .local/organizer-demo-password)
step() { printf '\n== %s\n' "$*"; }
uuid() { python3 -c 'import uuid;print(uuid.uuid4())'; }
post() { # form body -> "status body"
  curl -s -o /tmp/demo-body -w '%{http_code}' -H "$ORIGIN" -H 'Content-Type: application/json' -X POST "$APP/api/registrations/$1" --data-binary "$2"
  printf ' %s\n' "$(cat /tmp/demo-body)"
}
mails_to() { curl -s "$MP/api/v1/search?query=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote('to:\"'+sys.argv[1]+'\"'))" "$1")" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(len(d["messages"] or []))'; }
wait_mails() { for _ in $(seq 1 90); do [ "$(mails_to "$1")" -ge "$2" ] && { echo "mails to $1: $(mails_to "$1")"; return 0; }; sleep 2; done; echo "TIMEOUT waiting for mail to $1"; return 1; }
export_rows() { curl -s -u "organizer:$PW" -o /tmp/demo.xlsx -w '%{http_code}' "$APP/api/organizer/export.xlsx"; echo; python3 - <<'PY'
import zipfile,re
z=zipfile.ZipFile('/tmp/demo.xlsx'); s=z.read('xl/worksheets/sheet1.xml').decode()
print('export data rows:', len(re.findall(r'<row ',s))-1)
PY
}
TAG=$(date +%s)
EXT_EMAIL="demo-ext-$TAG@example.test"; STU_EMAIL="demo-stu-$TAG@example.test"; OUT_EMAIL="demo-outage-$TAG@example.test"

step "Headers through the proxy"
curl -sI "$APP/" | grep -iE '^(HTTP|content-security-policy|x-frame-options|x-content-type-options|referrer-policy)'
curl -sI "$APP/api/catalog" | grep -iE '^(HTTP|content-security-policy|x-frame-options|cache-control)'
curl -s -o /dev/null -w 'actuator via proxy: %{http_code}\n' "$APP/actuator/health"

step "Catalog (US-003)"
curl -s "$APP/api/catalog" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["conferenceTitle"],d["captcha"],[ (g["id"],[o["id"] for o in g["options"]]) for g in d["groups"]])'

step "External registration (US-001, US-004, US-005)"
CRID=$(uuid)
BODY='{"clientRequestId":"'$CRID'","captchaToken":"local-captcha-ok","firstName":" Žiga ","lastName":"Šuštaršič","email":"'$EXT_EMAIL'","organization":"=Inštitut","selections":{"workshops":["ws-testing"],"events":[],"meals":["meal-lunch"],"other":[]},"consentGiven":true}'
R=$(post external "$BODY"); echo "$R"; EXT_ID=$(echo "$R" | python3 -c 'import sys,json;print(json.loads(sys.stdin.read().split(" ",1)[1])["registrationId"])')
echo "replay:"; post external "$BODY"
echo "same id, different content:"; post external "${BODY/Šuštaršič/Other}"

step "Student registration (US-002)"
post student '{"clientRequestId":"'$(uuid)'","captchaToken":"local-captcha-ok","firstName":"Nuša","lastName":"Novak","email":"'$STU_EMAIL'","studyInstitution":"Univerza","studyProgramme":"Računalništvo","studentId":"63-ŽČ","selections":{"events":["ev-reception"]},"consentGiven":true}'

step "Rejections store nothing"
post external '{"clientRequestId":"'$(uuid)'","captchaToken":"wrong","firstName":"X","lastName":"Y","email":"reject-'$TAG'@example.test","organization":"O","selections":{"workshops":["ws-retired"]},"consentGiven":false}'
post external '{"clientRequestId":"'$(uuid)'","captchaToken":"wrong","firstName":"X","lastName":"Y","email":"reject-'$TAG'@example.test","organization":"O","consentGiven":true}'
curl -s -o /dev/null -w 'foreign origin: %{http_code}\n' -H 'Origin: https://evil.test' -H 'Content-Type: application/json' -X POST "$APP/api/registrations/external" --data '{}'

step "Mail through the SMTP catcher (US-006, US-007)"
wait_mails "$EXT_EMAIL" 1; wait_mails "$STU_EMAIL" 1
curl -s "$MP/api/v1/search?query=$(python3 -c "import urllib.parse;print(urllib.parse.quote('subject:\"$EXT_ID\"'))")" | python3 -c 'import sys,json;m=json.load(sys.stdin)["messages"];print("organizer mail:",m[0]["Subject"],"to",[t["Address"] for t in m[0]["To"]],"attachments",m[0]["Attachments"])'
echo "reject mail count: $(mails_to "reject-$TAG@example.test")"

step "JSON store (US-005)"
docker compose exec -T backend sh -c "ls /data/registrations/registrations | grep -c json; test -f /data/registrations/registrations/$EXT_ID.json && echo file-for-$EXT_ID-present; id -u"

step "Export (US-008)"
curl -s -o /dev/null -w 'no credentials: %{http_code}\n' "$APP/api/organizer/export.xlsx"
WRONG="organizer:not-the-password"; curl -s -o /dev/null -w 'wrong password: %{http_code}\n' -u "$WRONG" "$APP/api/organizer/export.xlsx"
export_rows
BEFORE=$(python3 -c "import zipfile,re;print(len(re.findall(r'<row ',zipfile.ZipFile('/tmp/demo.xlsx').read('xl/worksheets/sheet1.xml').decode()))-1)")

step "Restart backend and recreate all containers with retained volumes (NFR-02)"
docker compose restart backend >/dev/null; until [ "$(docker compose ps backend --format '{{.Health}}')" = healthy ]; do sleep 2; done
export_rows
docker compose down >/dev/null 2>&1; docker compose up -d >/dev/null 2>&1; until [ "$(docker compose ps backend --format '{{.Health}}')" = healthy ]; do sleep 2; done; sleep 3
export_rows
AFTER=$(python3 -c "import zipfile,re;print(len(re.findall(r'<row ',zipfile.ZipFile('/tmp/demo.xlsx').read('xl/worksheets/sheet1.xml').decode()))-1)")
echo "rows before=$BEFORE after recreate=$AFTER"
docker compose exec -T backend sh -c "test -f /data/registrations/registrations/$EXT_ID.json && echo json-survived-recreate"

step "SMTP outage and recovery (AC-006-02, AC-007-02, NFR-02)"
docker compose stop mailpit >/dev/null
post external '{"clientRequestId":"'$(uuid)'","captchaToken":"local-captcha-ok","firstName":"Outage","lastName":"Test","email":"'$OUT_EMAIL'","organization":"O","consentGiven":true}'
OUT_ID=$(python3 -c 'import json;print(json.load(open("/tmp/demo-body"))["registrationId"])')
sleep 12
docker compose exec -T postgres psql -U conference -d conference -tAc "select kind, status, attempts from notification_outbox where registration_id='$OUT_ID' order by kind"
docker compose start mailpit >/dev/null
wait_mails "$OUT_EMAIL" 1
for _ in $(seq 1 60); do n=$(curl -s "$MP/api/v1/search?query=$(python3 -c "import urllib.parse;print(urllib.parse.quote('subject:\"$OUT_ID\"'))")" | python3 -c 'import sys,json;m=json.load(sys.stdin)["messages"] or [];print(len(m) and m[0]["Attachments"])'); [ "$n" != 0 ] && break; sleep 2; done
echo "organizer mail after recovery, attachments: $n"
docker compose exec -T postgres psql -U conference -d conference -tAc "select kind, status, attempts from notification_outbox where registration_id='$OUT_ID' order by kind"

step "Catalog change with restart, no rebuild (AC-003-02)"
TMP=.local/catalog-demo.json
python3 - <<'PY'
import json; c=json.load(open('config/conference.local.json'))
c['groups']['workshops'].append({"id":"ws-demo-new","name":"Runtime demo workshop","active":True})
c['groups']['workshops'][0]['active']=False
json.dump(c,open('.local/catalog-demo.json','w'),ensure_ascii=False,indent=2)
PY
CONFERENCE_CONFIG_FILE=./$TMP docker compose up -d backend >/dev/null 2>&1; until [ "$(docker compose ps backend --format '{{.Health}}')" = healthy ]; do sleep 2; done
curl -s "$APP/api/catalog" | python3 -c 'import sys,json;print("workshops now:",[o["id"] for o in json.load(sys.stdin)["groups"][0]["options"]])'
post external '{"clientRequestId":"'$(uuid)'","captchaToken":"local-captcha-ok","firstName":"C","lastName":"D","email":"cat-'$TAG'@example.test","organization":"O","selections":{"workshops":["ws-testing"]},"consentGiven":true}'
docker compose up -d backend >/dev/null 2>&1; until [ "$(docker compose ps backend --format '{{.Health}}')" = healthy ]; do sleep 2; done
curl -s "$APP/api/catalog" | python3 -c 'import sys,json;print("workshops restored:",[o["id"] for o in json.load(sys.stdin)["groups"][0]["options"]])'
rm -f "$TMP"
step "done"
