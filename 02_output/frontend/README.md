# Frontend (registration web app)

React 19 + TypeScript single-page app built with Vite 6. It provides the external and student registration forms, the confirmation page, and the organizer download page. In the container, nginx 1.28.0 serves the build as a non-root user and forwards `/api/` to the backend. All validation that matters happens on the backend; the checks here only help the user.

## Prerequisites

| Tool                | Version                                                                   |
| ------------------- | ------------------------------------------------------------------------- |
| Node.js             | 22.23.3 (`engine-strict` is enabled)                                      |
| npm                 | 10.9.9                                                                    |
| Playwright Chromium | installed with `npx playwright install chromium` (for the e2e tests only) |

## Commands (ES-05)

Run from `02_output/frontend` after `npm ci`.

| Purpose                          | Command                                                                   |
| -------------------------------- | ------------------------------------------------------------------------- |
| Build                            | `npm run build` → `dist/`                                                 |
| Test (unit)                      | `npm test`; with coverage: `npm run test:coverage` → `coverage/`          |
| Test (end-to-end, frozen)        | `npm run test:e2e` (needs the Compose stack, see below)                   |
| Check (format, lint, type check) | `npm run check`                                                           |
| Format                           | `npm run format`                                                          |
| Run (development)                | `BACKEND_URL=http://localhost:8080 npm run dev` → http://localhost:5173   |
| Run (container)                  | from `02_output`: `docker compose up -d --build` → http://127.0.0.1:18080 |
| Contract validation              | `npm run contracts` (validates `../docs/02_contracts`)                    |
| Mutation                         | `npm run mutation` → `reports/mutation/`                                  |
| Duplication                      | `npm run duplication`                                                     |

## Configuration

| Setting                  | Kind                      | Source                                                             | Default                  |
| ------------------------ | ------------------------- | ------------------------------------------------------------------ | ------------------------ |
| `BACKEND_URL`            | setting (dev server only) | shell                                                              | `http://localhost:8080`  |
| `E2E_BASE_URL`           | setting (e2e)             | shell                                                              | `http://127.0.0.1:18080` |
| `E2E_MAILPIT_URL`        | setting (e2e)             | shell                                                              | `http://127.0.0.1:18025` |
| `E2E_ORGANIZER_USERNAME` | setting (e2e)             | shell                                                              | `organizer`              |
| `E2E_ORGANIZER_PASSWORD` | **secret** (e2e)          | shell, or the git-ignored file `../.local/organizer-demo-password` | none                     |

The catalog, captcha mode and site key come from `GET /api/catalog` at runtime. The build has no secrets. The container's security headers (CSP, framing, content-type options, referrer) are set in `nginx/nginx.conf`.

## Pages

`/` start page · `/register/external` · `/register/student` · `/organizer` (link to the export; the browser's own login dialog asks for the organizer credentials).

## Troubleshooting

| Symptom                                | Cause / fix                                                                                                 |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `npm ci` fails with an engine error    | use Node 22.23.3 / npm 10.9.9                                                                               |
| "Registration unavailable" page        | the backend is not reachable through `/api`                                                                 |
| e2e tests time out in the global setup | start the stack first (`docker compose up -d --build` in `02_output`) and wait until the backend is healthy |
| e2e asks for a password                | set `E2E_ORGANIZER_PASSWORD`, or create `02_output/.local/organizer-demo-password`                          |
| Playwright cannot find a browser       | `npx playwright install chromium`                                                                           |
