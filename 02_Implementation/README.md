# Conference registration — implementation

Two applications plus shared runtime files (see `docs/specification.md` for acceptance criteria,
API, data model, consistency protocol and decisions):

| Path | Content |
|---|---|
| `backend/` | Java 21 · Spring Boot 3.5.16 · Maven Wrapper (Maven 3.9.16) · Flyway · PostgreSQL 16 |
| `frontend/` | React 19 · TypeScript 5.9 · Vite 6 · npm lockfile · Node 22 |
| `config/` | Option/consent catalog (`catalog.yaml`) and synthetic fixtures (`fixtures/`) |
| `compose.yaml`, `compose.prod.yaml` | Local stack (postgres, mailpit, backend, frontend) and production overlay |
| `deploy/nginx-external.conf` | Example external TLS-terminating nginx (AR-08) |
| `tools/` | `verify-all.sh` (all DoD checks), `runtime_probe.py`, `m3-catalog-restart.sh`, `init-local-env.sh`; `metrics.py` and `security_summary.py` are research instrumentation |
| `docs/` | `specification.md`, `resolved-versions.md` |

## Prerequisites

Java 21 (JDK), Docker with Compose v2, Node **22** (e.g. `nvm install 22`; the build/test
toolchain is pinned to Node 22 — do not use Node 24), Python 3 (verification tools), and for
security scans `semgrep` (e.g. `uv tool install semgrep`). No system Maven is needed.

## Run locally

```bash
cd 02_Implementation
./tools/init-local-env.sh          # writes .env with random local-only credentials (gitignored)
docker compose up -d --build --wait
open http://localhost:8088          # registration forms
open http://localhost:8025          # Mailpit (captured emails)
```

The local stack runs the backend with profile `local`: deterministic captcha (a "local test
captcha" checkbox; no call to Google) and Mailpit as SMTP catcher. Only the frontend
(`127.0.0.1:8088`) and Mailpit UI (`127.0.0.1:8025`) are published; backend and PostgreSQL are
reachable only on the internal Compose network.

Organizer export (HTTP Basic, credentials from `.env`):

```bash
source .env
curl -u "$APP_ORGANIZER_USERNAME:$APP_ORGANIZER_PASSWORD" -o registrations.xlsx \
  http://localhost:8088/api/organizer/registrations/export.xlsx
```

Data lives in the named volumes `conference_pgdata` (database) and `conference_backups`
(raw JSON under `registrations/`, crash leftovers quarantined in `orphaned/`). `docker compose down`
keeps them; `docker compose down -v` deletes them.

### Changing the conference catalog (US-003)

Edit `config/catalog.yaml` (or point `APP_CATALOG_FILE` at another file) and restart the
backend: `docker compose up -d --no-build --force-recreate backend`. No rebuild is needed.
Retire options with `active: false`; keep IDs stable. Invalid catalogs stop the backend at startup.
Consents are configured under `consents:`; the shipped catalog has none because no approved legal
wording was supplied. `config/fixtures/` contains synthetic fixtures (clearly marked as not legal
notices) used for verification.

## Environment variables (backend)

| Variable | Required | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `local` (compose default) or `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | PostgreSQL connection |
| `APP_CATALOG_PATH` | yes | Catalog YAML file path |
| `APP_BACKUP_DIR` | yes | Persistent raw-JSON directory (volume) |
| `APP_ORGANIZER_USERNAME`, `APP_ORGANIZER_PASSWORD` | yes | Export credential (password ≥ 12 chars) |
| `APP_ORGANIZER_EMAILS` | yes | Comma-separated organizer notification recipients |
| `APP_MAIL_FROM` | yes | Sender address |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` | prod | External SMTP (STARTTLS required by default) |
| `APP_CAPTCHA_SITE_KEY`, `APP_CAPTCHA_SECRET` | prod | Google reCAPTCHA v2 keys |
| `APP_TRUSTED_PROXIES` | no | Regex of proxy IPs whose `X-Forwarded-*` headers are trusted (default: private ranges) |
| `APP_RATE_LIMIT_PER_MINUTE` | no | Public submissions per client IP per minute (default 20) |

## Local versus production mode

* **local** (`compose.yaml`): profile `local`, deterministic captcha token, Mailpit, generated
  local credentials. Never use for real participants.
* **prod** (`docker compose -f compose.yaml -f compose.prod.yaml up -d`): profile `prod`,
  reCAPTCHA required, external SMTP required, Mailpit removed. The backend refuses to start if the
  reCAPTCHA keys are missing, if deterministic captcha mode is requested, or if `prod` is combined
  with `local`/`test`. TLS/Let's Encrypt is terminated by an external nginx on the host
  (`deploy/nginx-external.conf`), which overwrites `X-Forwarded-For`/`X-Forwarded-Proto`; the
  application containers speak HTTP only on the internal network.

## Checks

All Definition-of-Done checks (builds, unit/integration/component/browser tests, static analysis,
architecture, security scanners, container runtime probes) in one command:

```bash
./tools/verify-all.sh <label>      # evidence -> ../03_Metrics/evidence/
```

Individual commands:

```bash
# backend (unit + Testcontainers PostgreSQL ITs + ArchUnit + JaCoCo)
cd backend && ./mvnw -B verify
./mvnw -B spotless:check compile spotbugs:check pmd:check pmd:cpd-check -DskipTests
./mvnw -B org.owasp:dependency-check-maven:13.0.0:check      # NVD via official JSON feeds
# frontend (Node 22)
cd frontend && npm ci && npm run format:check && npm run lint && npm run typecheck \
  && npm run test:coverage && npm run build && npm run cpd && npm audit --audit-level=high
npm run e2e                          # Playwright against the running compose stack
# runtime
./tools/m3-catalog-restart.sh        # catalog change + restart demonstration
python3 tools/runtime_probe.py all   # mail outage/retry, idempotency, storage failure,
                                     # export auth, prod captcha isolation, recreation
```

Playwright needs browsers once: `npx playwright install chromium`.

## Operational limitations

* PostgreSQL and the JSON files are not one ACID transaction; ordering, compensation and
  reconciliation are described in `docs/specification.md` §5. Reconciliation runs at startup and
  every 10 minutes.
* Email delivery is at-least-once (a crash between SMTP acceptance and the status update can
  duplicate an email). Permanently failed emails stay `FAILED` in `email_outbox` and require
  operator attention (no UI).
* Rate limiting is in-memory per backend instance (single-instance architecture).
* Not production-ready without: approved consent/privacy wording, retention policy, repeated-email
  policy, real SMTP/reCAPTCHA credentials, TLS domain, backups of both volumes.
