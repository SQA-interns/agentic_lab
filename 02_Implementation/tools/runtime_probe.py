#!/usr/bin/env python3
"""Runtime verification probes against the real docker-compose stack (verification tooling).

Every probe talks to the running containers: the public API through the frontend nginx,
PostgreSQL via `docker compose exec psql`, the backup volume via `docker compose exec`, and
Mailpit's HTTP API. Results are written as JSON to 03_Metrics/evidence/runtime/<probe>.json.

Usage: runtime_probe.py {mail|export|storage|idempotency|prod-isolation|recreate|all}
"""

from __future__ import annotations

import base64
import hashlib
import json
import re
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
import uuid
import zipfile
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from xml.etree import ElementTree

IMPL = Path(__file__).resolve().parents[1]
OUT = IMPL.parent / "03_Metrics" / "evidence" / "runtime"
BASE = "http://localhost:8088"
MAILPIT = "http://localhost:8025"
CAPTCHA = "local-test-captcha-pass"
EMPTY = {"workshops": [], "events": [], "meals": [], "otherActivities": []}


class ProbeFailure(AssertionError):
    pass


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def check(condition: bool, message: str) -> None:
    if not condition:
        raise ProbeFailure(message)


def compose(*args: str, check_rc: bool = True) -> str:
    proc = subprocess.run(
        ["docker", "compose", *args], cwd=IMPL, capture_output=True, text=True
    )
    if check_rc and proc.returncode != 0:
        raise ProbeFailure(f"docker compose {' '.join(args)} failed: {proc.stderr[-500:]}")
    return proc.stdout + proc.stderr


def sql(query: str) -> list[list[str]]:
    out = compose(
        "exec", "-T", "postgres", "psql", "-U", "conference", "-d", "conference",
        "-At", "-F", "|", "-c", query,
    )
    return [line.split("|") for line in out.splitlines() if line]


def backup_file(registration_id: str) -> str | None:
    check(re.fullmatch(r"[0-9a-f-]{36}", registration_id) is not None, "bad id")
    proc = subprocess.run(
        ["docker", "compose", "exec", "-T", "backend", "cat",
         f"/data/backups/registrations/{registration_id}.json"],
        cwd=IMPL, capture_output=True, text=True,
    )
    return proc.stdout if proc.returncode == 0 else None


def backup_listing(sub: str) -> list[str]:
    out = compose("exec", "-T", "backend", "ls", "-1", f"/data/backups/{sub}")
    return [line for line in out.splitlines() if line]


def http(method: str, url: str, body: object | None = None,
         headers: dict[str, str] | None = None, timeout: float = 30) -> tuple[int, bytes, dict]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as err:
        return err.code, err.read(), dict(err.headers)


def env_value(key: str) -> str:
    for line in (IMPL / ".env").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1]
    raise ProbeFailure(f"{key} missing from .env")


def external_payload(tag: str, **overrides: object) -> dict:
    body = {
        "clientRequestId": str(uuid.uuid4()),
        "firstName": " Špela ",
        "lastName": f"Probe-{tag}",
        "email": f"spela.{tag}.{uuid.uuid4().hex[:6]}@example.org",
        "organization": "Inštitut Čebela",
        "selections": {**EMPTY, "workshops": ["ws-data-science"], "meals": ["meal-lunch-day1"]},
        "consents": {},
        "captchaToken": CAPTCHA,
    }
    body.update(overrides)
    return body


def student_payload(tag: str, **overrides: object) -> dict:
    body = {
        "clientRequestId": str(uuid.uuid4()),
        "firstName": "Luka",
        "lastName": f"Študent-{tag}",
        "email": f"luka.{tag}.{uuid.uuid4().hex[:6]}@student.example.org",
        "studyInstitution": "Univerza v Ljubljani",
        "studyProgramme": "Računalništvo",
        "studentId": f"S-{uuid.uuid4().hex[:6]}",
        "selections": {**EMPTY, "events": ["ev-city-tour"]},
        "consents": {},
        "captchaToken": CAPTCHA,
    }
    body.update(overrides)
    return body


def register(kind: str, body: dict, expect: int = 201) -> dict:
    status, raw, _ = http("POST", f"{BASE}/api/registrations/{kind}", body)
    check(status == expect, f"POST {kind} expected {expect}, got {status}: {raw[:300]!r}")
    return json.loads(raw) if raw else {}


def wait_backend_healthy(timeout: float = 180) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        status, raw, _ = http("GET", f"{BASE}/actuator/health/readiness", timeout=5) \
            if _port_open() else (0, b"", {})
        if status == 200 and b'"UP"' in raw:
            return
        time.sleep(2)
    raise ProbeFailure("backend readiness not UP in time")


def _port_open() -> bool:
    try:
        urllib.request.urlopen(f"{BASE}/", timeout=3)
        return True
    except Exception:  # noqa: BLE001 - connection refused while restarting
        return False


def assert_stored(registration_id: str, first_name: str | None = None) -> dict:
    rows = sql(f"SELECT raw_json_sha256, first_name FROM registration WHERE id='{registration_id}'")
    check(len(rows) == 1, f"registration {registration_id} not in DB")
    content = backup_file(registration_id)
    check(content is not None, f"backup JSON missing for {registration_id}")
    sha = hashlib.sha256(content.encode()).hexdigest()
    check(sha == rows[0][0], "JSON file hash != DB raw_json_sha256")
    if first_name is not None:
        check(rows[0][1] == first_name, f"first_name {rows[0][1]!r} != {first_name!r}")
    return {"id": registration_id, "sha256": sha, "dbFirstName": rows[0][1]}


# ---------------------------------------------------------------- mail (M4, P-08)
def mailpit_messages() -> list[dict]:
    status, raw, _ = http("GET", f"{MAILPIT}/api/v1/messages?limit=500")
    check(status == 200, f"mailpit list {status}")
    return json.loads(raw).get("messages", [])


def mailpit_for(registration_id: str) -> list[dict]:
    found = []
    for msg in mailpit_messages():
        status, raw, _ = http("GET", f"{MAILPIT}/api/v1/message/{msg['ID']}")
        detail = json.loads(raw)
        if registration_id in detail.get("Text", ""):
            found.append(detail)
    return found


def verify_mail(registration_id: str, participant_email: str) -> dict:
    messages = mailpit_for(registration_id)
    to = sorted(a["Address"] for m in messages for a in m["To"])
    organizer_email = compose("exec", "-T", "backend", "printenv", "APP_ORGANIZER_EMAILS").strip()
    check(participant_email in to, f"participant mail missing; got {to}")
    check(organizer_email in to, f"organizer mail missing; got {to}")
    organizer = [m for m in messages if any(a["Address"] == organizer_email for a in m["To"])]
    attachments = organizer[0].get("Attachments", [])
    check(len(attachments) == 1, "organizer mail must have exactly one attachment")
    part = attachments[0]
    status, content, _ = http(
        "GET", f"{MAILPIT}/api/v1/message/{organizer[0]['ID']}/part/{part['PartID']}"
    )
    check(status == 200, "attachment download failed")
    file_content = backup_file(registration_id) or ""
    check(content.decode() == file_content, "organizer attachment != stored JSON file")
    check(part["FileName"] == f"registration-{registration_id}.json", "attachment name")
    return {"recipients": to, "attachment": part["FileName"],
            "attachmentSha256": hashlib.sha256(content).hexdigest()}


def wait_for(predicate, timeout: float, interval: float = 2):  # type: ignore[no-untyped-def]
    deadline = time.time() + timeout
    last: Exception | None = None
    while time.time() < deadline:
        try:
            result = predicate()
            if result:
                return result
        except (ProbeFailure, OSError) as exc:
            last = exc
        time.sleep(interval)
    raise ProbeFailure(f"timeout waiting: {last}")


def outbox_status(registration_id: str) -> list[list[str]]:
    return sql(
        "SELECT kind, status, attempts, coalesce(last_error,'') FROM email_outbox "
        f"WHERE registration_id='{registration_id}' ORDER BY kind"
    )


def probe_mail() -> dict:
    result: dict = {"steps": []}
    http("DELETE", f"{MAILPIT}/api/v1/messages")
    body = external_payload("mail")
    reg = register("external", body)
    rid = reg["registrationId"]
    check(reg["emailStatus"] == "PENDING", "acceptance must not claim delivery")
    delivered = wait_for(lambda: verify_mail(rid, body["email"]), 60)
    result["steps"].append({"normalDelivery": delivered, "outbox": outbox_status(rid)})

    compose("stop", "mailpit")
    result["steps"].append({"mailpitStoppedAt": now()})
    try:
        body2 = student_payload("outage")
        reg2 = register("student", body2)
        rid2 = reg2["registrationId"]
        stored = assert_stored(rid2, "Luka")
        pending = wait_for(
            lambda: (rows := outbox_status(rid2)) and all(
                r[1] == "PENDING" and int(r[2]) >= 1 and r[3] for r in rows) and rows,
            60,
        )
        result["steps"].append({"duringOutage": {"stored": stored, "outbox": pending}})
    finally:
        compose("start", "mailpit")
        result["steps"].append({"mailpitStartedAt": now()})
    wait_for(lambda: http("GET", f"{MAILPIT}/api/v1/messages")[0] == 200, 60)
    delivered2 = wait_for(lambda: verify_mail(rid2, body2["email"]), 150, 5)
    sent = wait_for(
        lambda: (rows := outbox_status(rid2)) and all(r[1] == "SENT" for r in rows) and rows, 60
    )
    result["steps"].append({"afterRecovery": {"mail": delivered2, "outbox": sent}})
    result["duplicateSemantics"] = (
        "at-least-once: a crash between SMTP acceptance and the SENT update can resend"
    )
    return result


# ---------------------------------------------------------------- export (M5, P-09)
def parse_xlsx(data: bytes) -> list[list[str]]:
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with zipfile.ZipFile(BytesIO(data)) as zf:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in zf.namelist():
            root = ElementTree.fromstring(zf.read("xl/sharedStrings.xml"))
            shared = ["".join(t.text or "" for t in si.iter(f"{{{ns['m']}}}t"))
                      for si in root.findall("m:si", ns)]
        sheet = ElementTree.fromstring(zf.read("xl/worksheets/sheet1.xml"))
    rows = []
    for row in sheet.iter(f"{{{ns['m']}}}row"):
        values = []
        for cell in row.findall("m:c", ns):
            v = cell.find("m:v", ns)
            inline = cell.find("m:is", ns)
            if cell.get("t") == "s" and v is not None:
                values.append(shared[int(v.text or "0")])
            elif inline is not None:
                values.append("".join(t.text or "" for t in inline.iter(f"{{{ns['m']}}}t")))
            else:
                values.append(v.text if v is not None and v.text else "")
        rows.append(values)
    return rows


def probe_export() -> dict:
    user, password = env_value("APP_ORGANIZER_USERNAME"), env_value("APP_ORGANIZER_PASSWORD")
    url = f"{BASE}/api/organizer/registrations/export.xlsx"
    names = [r[0] for r in sql("SELECT last_name FROM registration")]
    check(names, "need at least one registration before export probe")
    unauthorized = {}
    for label, headers in {
        "none": {},
        "wrong-password": {"Authorization": "Basic " + base64.b64encode(
            f"{user}:wrong-password-123".encode()).decode()},
        "wrong-user": {"Authorization": "Basic " + base64.b64encode(
            f"participant:{password}".encode()).decode()},
    }.items():
        status, raw, _ = http("GET", url, headers=headers)
        leaked = [n for n in names if n.encode() in raw]
        check(status == 401, f"unauthorized ({label}) got {status}")
        check(not leaked and not raw.startswith(b"PK"), f"unauthorized ({label}) leaked data")
        unauthorized[label] = {"status": status, "bodyBytes": len(raw)}
    auth = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()
    status, raw, headers = http("GET", url, headers={"Authorization": auth})
    check(status == 200, f"authorized export got {status}")
    rows = parse_xlsx(raw)
    header, data = rows[0], rows[1:]
    db = sql(
        "SELECT id, participant_type, first_name, last_name, email, coalesce(organization,''), "
        "coalesce(study_institution,''), coalesce(study_programme,''), coalesce(student_id,'') "
        "FROM registration ORDER BY created_at, id"
    )
    check(len(data) == len(db), f"workbook rows {len(data)} != DB rows {len(db)}")
    for xrow, drow in zip(data, db):
        expected = [drow[0], None, drow[1], *drow[2:]]
        actual = [xrow[0], None, xrow[2], *xrow[3:10]]
        check(actual == expected, f"row mismatch {actual} vs {expected}")
    types = sorted({r[2] for r in data})
    check(types == ["EXTERNAL", "STUDENT"], f"workbook must contain both forms, got {types}")
    return {
        "unauthorized": unauthorized,
        "authorized": {"status": status, "contentType": headers.get("Content-Type"),
                       "cacheControl": headers.get("Cache-Control"),
                       "header": header, "rows": len(data), "dbRows": len(db),
                       "participantTypes": types},
    }


# ---------------------------------------------------------------- storage failure (P-07)
def probe_storage() -> dict:
    result: dict = {}
    before = int(sql("SELECT count(*) FROM registration")[0][0])
    files_before = len(backup_listing("registrations"))
    # 1) backup volume not writable -> 503, no DB row
    compose("exec", "-T", "-u", "root", "backend", "chmod", "0555",
            "/data/backups/staging", "/data/backups/registrations")
    try:
        status, raw, _ = http("POST", f"{BASE}/api/registrations/external",
                              external_payload("fs-fail"))
        ready = http("GET", f"{BASE}/actuator/health/readiness")
    finally:
        compose("exec", "-T", "-u", "root", "backend", "chmod", "0755",
                "/data/backups/staging", "/data/backups/registrations")
    after = int(sql("SELECT count(*) FROM registration")[0][0])
    check(status == 503 and json.loads(raw)["code"] == "STORAGE_UNAVAILABLE",
          f"fs failure expected 503, got {status}")
    check(after == before, "DB row created despite failed JSON write")
    result["backupWriteFailure"] = {"status": status, "readinessDuringFailure": ready[0],
                                    "dbCountBefore": before, "dbCountAfter": after}
    # 2) database unavailable after JSON publish -> 503, file removed
    compose("stop", "postgres")
    try:
        status2, raw2, _ = http("POST", f"{BASE}/api/registrations/external",
                                external_payload("db-fail"), timeout=60)
        files_during = backup_listing("registrations")
    finally:
        compose("start", "postgres")
    wait_backend_healthy()
    check(status2 == 503, f"db failure expected 503, got {status2}: {raw2[:200]!r}")
    check(len(files_during) == files_before, "published JSON left behind after DB failure")
    result["databaseFailure"] = {"status": status2, "filesBefore": files_before,
                                 "filesAfter": len(files_during)}
    # 3) recovery: same stack accepts again
    reg = register("external", external_payload("after-recovery"))
    result["afterRecovery"] = assert_stored(reg["registrationId"], "Špela")
    return result


# ---------------------------------------------------------------- idempotency (P-07)
def probe_idempotency() -> dict:
    body = student_payload("idem")
    first = register("student", body, 201)
    replay_body = dict(body, captchaToken="token-not-reusable")
    second = register("student", replay_body, 200)
    check(first["registrationId"] == second["registrationId"] and second["replayed"],
          "replay must return the original registration")
    conflict = register("student", dict(body, studyProgramme="Drugačen"), 409)
    rid = first["registrationId"]
    rows = sql(f"SELECT count(*) FROM registration WHERE client_request_id='{body['clientRequestId']}'")
    outbox = sql(f"SELECT count(*) FROM email_outbox WHERE registration_id='{rid}'")
    check(rows[0][0] == "1", "duplicate registration row")
    check(outbox[0][0] == "2", f"expected 2 outbox rows, got {outbox}")
    return {"first": first, "replay": second, "conflictCode": conflict.get("code"),
            "rowsForRequestId": rows[0][0], "outboxRows": outbox[0][0]}


# ---------------------------------------------------------------- prod isolation (ST-05)
def run_prod_backend(extra_env: dict[str, str]) -> dict:
    env = ["-e", "SPRING_PROFILES_ACTIVE=" + extra_env.pop("SPRING_PROFILES_ACTIVE", "prod")]
    for k, v in extra_env.items():
        env += ["-e", f"{k}={v}"]
    proc = subprocess.run(
        ["docker", "compose", "run", "--rm", "--no-deps", "-T", *env, "backend"],
        cwd=IMPL, capture_output=True, text=True, timeout=180,
    )
    log = proc.stdout + proc.stderr
    reason = re.findall(r"(CaptchaConfigurationException: [^\n]+|Reason: [^\n]+"
                        r"|APPLICATION FAILED TO START)", log)
    return {"exitCode": proc.returncode, "evidence": reason[:4]}


def probe_prod_isolation() -> dict:
    cases = {
        "prod-without-recaptcha-secret": {},
        "prod-with-test-captcha-mode": {"APP_CAPTCHA_MODE": "test",
                                        "APP_CAPTCHA_TEST_TOKEN": "x"},
        "prod-plus-local-profile": {"SPRING_PROFILES_ACTIVE": "prod,local"},
        "no-profile-test-mode": {"SPRING_PROFILES_ACTIVE": "default",
                                 "APP_CAPTCHA_MODE": "test", "APP_CAPTCHA_TEST_TOKEN": "x"},
    }
    results = {}
    for name, env in cases.items():
        outcome = run_prod_backend(dict(env))
        check(outcome["exitCode"] != 0, f"{name}: backend started but must fail closed")
        check(any("aptcha" in e or "Reason" in e for e in outcome["evidence"]),
              f"{name}: failure not attributable to captcha configuration: {outcome}")
        results[name] = outcome
    return results


# ---------------------------------------------------------------- recreation (P-10)
def probe_recreate() -> dict:
    before = sql("SELECT id, raw_json_sha256 FROM registration ORDER BY id")
    check(before, "need registrations before recreation")
    compose("down")  # no -v: named volumes preserved
    compose("up", "-d", "--no-build")
    wait_backend_healthy()
    after = sql("SELECT id, raw_json_sha256 FROM registration ORDER BY id")
    check(after == before, "registrations changed across container recreation")
    for rid, sha in after:
        content = backup_file(rid)
        check(content is not None and hashlib.sha256(content.encode()).hexdigest() == sha,
              f"backup for {rid} missing/mismatched after recreation")
    return {"registrationsBefore": len(before), "registrationsAfter": len(after),
            "jsonFilesVerified": len(after)}


PROBES = {
    "mail": probe_mail,
    "export": probe_export,
    "storage": probe_storage,
    "idempotency": probe_idempotency,
    "prod-isolation": probe_prod_isolation,
    "recreate": probe_recreate,
}


def main() -> int:
    names = sys.argv[1:] or ["all"]
    if names == ["all"]:
        names = list(PROBES)
    OUT.mkdir(parents=True, exist_ok=True)
    failed = 0
    for name in names:
        record: dict = {"probe": name, "start": now()}
        try:
            record["result"] = PROBES[name]()
            record["status"] = "pass"
        except (ProbeFailure, subprocess.SubprocessError, OSError) as exc:
            record["status"] = "fail"
            record["error"] = str(exc)
            record["traceback"] = traceback.format_exc()
            failed += 1
        record["end"] = now()
        (OUT / f"{name}.json").write_text(json.dumps(record, indent=2, ensure_ascii=False))
        print(f"[{record['status']}] {name}" + (f": {record['error']}" if 'error' in record else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
