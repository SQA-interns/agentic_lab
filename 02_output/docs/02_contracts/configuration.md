# Configuration contract (environment → backend, frontend)

> Written in: phase 2 · Source: `project/00_setup/environments.md`, `secrets.env.example`, ES-01, AR-04, AR-07 · Agent: writes

Every value that differs between environments comes from an environment variable (ES-01). Secrets have no default. The column "Property" is the Spring property the variable sets; tests set the property directly.

| Variable | Property | Default (profile `default`: local and test) | Production (`SPRING_PROFILES_ACTIVE=production`) |
|---|---|---|---|
| `DATABASE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/registration` | required |
| `DATABASE_USER` | `spring.datasource.username` | `registration` | required |
| `POSTGRES_PASSWORD` (secret) | `spring.datasource.password` | none | required |
| `SMTP_HOST` / `SMTP_PORT` | `spring.mail.host` / `spring.mail.port` | `localhost` / `1025` (Mailpit) | required |
| `SMTP_TLS` | `spring.mail.properties.mail.smtp.starttls.enable` and `.required` | `false` | required |
| `SMTP_USERNAME` / `SMTP_PASSWORD` (secret) | `spring.mail.username` / `spring.mail.password` | empty | as the provider needs |
| `MAIL_FROM` | `app.mail.from` | `registration@conference.local` | recommended |
| `CONFERENCE_NAME` | `app.conference-name` | `Conference` | recommended |
| `OPTIONS_FILE` | `app.options-file` | `classpath:conference-options.json` | required (a file on a mounted volume) |
| `JSON_COPY_DIR` | `app.json-copy-dir` | `./data/json` (compose: `/data/json` on a named volume) | a path on a persistent volume |
| `RECAPTCHA_TEST_MODE` | `app.recaptcha.test-mode` | `false` | must be `false` |
| `RECAPTCHA_SITE_KEY` / `RECAPTCHA_SECRET_KEY` (secret) | `app.recaptcha.site-key` / `app.recaptcha.secret-key` | none; required unless test mode is on | required |
| `RECAPTCHA_VERIFY_URL` | `app.recaptcha.verify-url` | `https://www.google.com/recaptcha/api/siteverify` | default |
| `ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD` (secret) | `app.organizer.username` / `app.organizer.password` | none | required |
| `ORGANIZER_EMAILS` | `app.organizer.emails` | none (comma separated, at least one) | required |
| `ORGANIZER_HTTPS_ONLY` | `app.organizer.https-only` | `true` (compose local sets `false`) | must be `true` |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | empty (no CORS) | empty |
| `RATE_LIMIT_REGISTRATION_PER_MINUTE` | `app.rate-limit.registration-per-minute` | `10` | default |
| `RATE_LIMIT_EXPORT_PER_MINUTE` | `app.rate-limit.export-per-minute` | `10` | default |
| `RATE_LIMIT_READ_PER_MINUTE` | `app.rate-limit.read-per-minute` | `120` | default |
| `MAX_REQUEST_BYTES` | `app.max-request-bytes` | `16384` | default |

## Startup refusal (SR-02, AC-001-15, AC-003-03)

The backend does not start when:

- test mode is off and the site key or the secret key is empty;
- the `production` profile is active and test mode is on, or organizer HTTPS-only access is off;
- the options file is missing, unreadable, or violates `conference-options.schema.json` or id uniqueness;
- the organizer username, password or email list is empty, or an organizer address is invalid.

## reCAPTCHA test mode

When `app.recaptcha.test-mode=true`, the backend makes no call to Google and accepts exactly the token `test-mode-pass`; any other token, or none, is rejected. `/api/config` reports `recaptchaTestMode: true` and an empty site key, and the frontend then sends `test-mode-pass` instead of loading the Google widget.

## Frontend

The frontend holds no key or secret (AR-07). It reads `/api/config` at runtime; its build has no environment-specific values. In the local stack nginx forwards `/api` to the backend; the Vite dev server proxies `/api` to `BACKEND_URL` (default `http://127.0.0.1:8080`).
