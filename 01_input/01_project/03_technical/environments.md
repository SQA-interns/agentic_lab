# Environments

> Owner: Architect · Read in: phases 0, 2, 4, 6, 7 · Agent: read-only

Versions belong only in tech-stack.md. No environment was accessed or verified
while preparing these specifications.

| Environment | Purpose | Execution |
|---|---|---|
| local | Synthetic full-app development/demo | Compose PostgreSQL, isolated SMTP catcher, backend/frontend; persistent DB/JSON volumes |
| test | Automated repeatable verification | Unit/component runners, PostgreSQL Testcontainers, Playwright; disposable isolated state |
| production | Documented eventual deployment, outside this run | App containers behind external HTTPS nginx, real SMTP/captcha and persistent volumes |

## Configuration

Names are proposed binding names for the generated application; preserve or document
an explicit mapping in its README. Secret values are exclusively in setup's example.

| Setting | Environment | Default policy |
|---|---|---|
| APP_PROFILE | all | Explicit local/test/production; never silently enable test bypass in production |
| DB_URL, DB_USERNAME | all | Generated local service/db/user names allowed; production explicit |
| SMTP_HOST, SMTP_PORT, SMTP_TLS_ENABLED | all | Isolated catcher locally; production explicit secure relay settings |
| MAIL_FROM, ORGANIZER_EMAILS | all | Synthetic reserved-domain addresses to local catcher; real values only for production |
| CAPTCHA_MODE | all | Deterministic stub only local/test; production real verification, fail closed |
| CONFERENCE_CONFIG_PATH | all | External startup catalog; committed synthetic local example |
| REGISTRATION_JSON_DIR | all | Writable persistent volume; never container-ephemeral storage |
| PUBLIC_BASE_URL, allowed origins, trusted proxy configuration | all | Document local ports; production explicit HTTPS origin and proxy trust |

## External services

| Service | Production | Local/test | Human production check |
|---|---|---|---|
| Captcha | Google reCAPTCHA | Deterministic substitute, no automated Google calls | Real keys/domains and rejection |
| Email | Authenticated/TLS SMTP where relay requires | Isolated SMTP catcher | Actual delivery/attachment |
| TLS | External nginx/Let's Encrypt | Localhost HTTP only | Certificates, redirects, proxy headers |
| PostgreSQL | Persistent service | Compose/Testcontainers | Backup/restore and access controls |

Do not send mail to real recipients, issue certificates or provision remote services
in the experimental run. Scanner databases/registries need operator-provided access;
outage is blocked evidence, not a clean result. List unverified production integrations
in release notes. Clean-checkout verification uses fresh isolated local state.
