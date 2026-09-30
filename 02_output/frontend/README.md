# Frontend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-05, ES-06 · Agent: writes

React 19 + TypeScript single page built with Vite and served by nginx. It talks to the backend only through `/api` (AR-01) and holds no secret: the reCAPTCHA site key comes from `GET /api/config` at runtime (AR-07). UI contract: `../docs/02_contracts/ui.md`.

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2 (`node --version`, `npm --version`).
- For end-to-end tests: the local stack running (see `../README.md`) and Playwright's Chromium (`npx playwright install chromium` if it is not installed yet).

Install the exact locked dependencies:

```bash
npm ci
```

## Configuration

The build has no environment-specific values.

| Setting                                            | Source                                        | Notes                                                               |
| -------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------- |
| `BACKEND_URL`                                      | environment, dev server only                  | where `npm run dev` proxies `/api`; default `http://127.0.0.1:8080` |
| reCAPTCHA site key, test mode, conference name     | `GET /api/config` (backend configuration)     | no key in the frontend                                              |
| `E2E_BASE_URL`                                     | environment, e2e only                         | default `http://127.0.0.1:8081` (the compose frontend)              |
| `E2E_ORGANIZER_USERNAME`, `E2E_ORGANIZER_PASSWORD` | `.env` values passed as environment, e2e only | used by the export check of the NFR-01 test                         |
| `E2E_MAILPIT_URL`                                  | environment, e2e only                         | default `http://127.0.0.1:8025`                                     |

`nginx.conf` serves the files, adds the security headers (CSP allowing only Google reCAPTCHA as a third party), limits bodies to 64 KiB and forwards `/api/` to `backend:8080`.

## Build

```bash
npm run build
```

Type-checks and writes `dist/`. `docker build .` builds the non-root nginx image (`node:24.13.0-alpine` build stage, `nginx:1.30.5-alpine` runtime, port 8080).

## Run

With the local stack (recommended): `docker compose --env-file ../.env up -d --build` in `02_output/`, then http://127.0.0.1:8081.

Development server with hot reload (backend running on port 8080):

```bash
npm run dev
```

## Test

```bash
npm test
```

Vitest unit and component tests (`src/**/*.test.ts(x)`); coverage with `npm run coverage`, mutation testing with `npm run mutation` (Stryker, report in `reports/mutation/`).

End-to-end tests (frozen), against the running local stack, with the organizer login from `.env` in the environment:

```bash
E2E_ORGANIZER_USERNAME="$(sed -n 's/^ORGANIZER_USERNAME=//p' ../../.env | tr -d '\r')" E2E_ORGANIZER_PASSWORD="$(sed -n 's/^ORGANIZER_PASSWORD=//p' ../../.env | tr -d '\r')" npm run e2e
```

## Check

```bash
npm run check
```

Prettier, ESLint and `tsc --noEmit`. Format with `npm run format`; duplication report with `npm run duplication`; dependency scan with `npm audit`.

## Troubleshooting

| Symptom                                                         | Cause and fix                                                                                         |
| --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Page says "The registration form is not available right now."   | `/api/options` or `/api/config` failed: check the backend is healthy (`docker compose ps`)            |
| e2e tests fail on `fetchOptions` with a connection error        | The local stack is not running on 127.0.0.1:8081                                                      |
| e2e NFR-01 test fails with "E2E_ORGANIZER_USERNAME must be set" | Pass the organizer login as shown above                                                               |
| Registrations answer 429 during manual testing                  | Rate limit (10 per minute per client by default); wait a minute                                       |
| reCAPTCHA widget does not appear in production                  | CSP or site key: the site key must be configured in the backend and the domain registered with Google |
