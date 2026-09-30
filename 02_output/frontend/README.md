# Frontend

> Written in: phase 7 · Source: `project/03_technical/architecture.md`, ES-05, ES-06 · Agent: writes

React registration form (Vite, TypeScript). It calls the backend only through `/api` (AR-01) and holds no secret: the conference name, reCAPTCHA test-mode flag and public site key come from `GET /api/config` (AR-07).

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2 (`engine-strict` rejects other versions).
- For end-to-end tests: the running local stack (see the root README) and Playwright's Chromium (`npx playwright install chromium` if it is not already in the user cache).

## Configuration

The frontend has no build-time settings. At runtime:

| Setting                                        | Source                                                                                                                                                 |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| conference name, reCAPTCHA site key, test mode | backend `GET /api/config`                                                                                                                              |
| backend address                                | the nginx image proxies `/api/` to `http://backend:8080` (compose service name); in production the external reverse proxy routes `/api` to the backend |
| `E2E_BASE_URL`, `E2E_MAILPIT_URL` (e2e tests)  | environment; defaults `http://127.0.0.1:8080`, `http://127.0.0.1:8025`                                                                                 |
| organizer credentials and emails (e2e tests)   | environment, or the repository-root `.env` (`ORGANIZER_*`)                                                                                             |

## Build, test, check, run (ES-05)

Run from `02_output/frontend/` after `npm ci`.

| Purpose                                                                      | Command                                                                   |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| build                                                                        | `npm run build` (type check, then `dist/`)                                |
| test (unit and frozen acceptance)                                            | `npm test`                                                                |
| check (format, lint, type check)                                             | `npm run check`                                                           |
| run (development server on 127.0.0.1:5173, proxies `/api` to 127.0.0.1:8080) | `npm run dev`                                                             |
| end-to-end (against the running stack)                                       | `npm run test:e2e`                                                        |
| coverage / mutation / duplication                                            | `npm run test:coverage` / `npm run test:mutation` / `npm run duplication` |
| format fix                                                                   | `npm run format`                                                          |

The container image (`Dockerfile`) builds with `node:24.13.0-alpine` and serves `dist/` with `nginx:1.30.5-alpine` as the non-root `nginx` user on port 8080, with security headers (`nginx.conf`).

## Troubleshooting

| Symptom                              | Cause and fix                                                                                                                                          |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `npm ci` fails with `EBADENGINE`     | use Node 24.13.0 / npm 11.6.2                                                                                                                          |
| the form shows "could not be loaded" | the backend is not reachable through `/api` (check the backend container or the dev-server proxy target)                                               |
| no reCAPTCHA widget in production    | `RECAPTCHA_SITE_KEY` missing on the backend, or the CSP blocks Google (`nginx.conf` allows `www.google.com/recaptcha` and `www.gstatic.com/recaptcha`) |
| e2e tests time out                   | the stack is not running at `E2E_BASE_URL`, or Mailpit is not at `E2E_MAILPIT_URL`                                                                     |
