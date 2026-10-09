# Frontend

React single page: type choice, registration form, reCAPTCHA, confirmation. It calls only the
backend under `/api` (AR-01) and holds no secret; the reCAPTCHA site key comes from the backend.
UI contract: `../docs/02_contracts/ui-form.json`.

## Prerequisites

Node.js 24.13.0 with npm 11.6.2 (exact, `engine-strict`). Playwright's Chromium for the
end-to-end tests: `npx playwright install chromium` (once). Java 21 and Docker for the
end-to-end tests, which start their own backend, PostgreSQL and Mailpit.

## Configuration

No build-time configuration. `API_PROXY_TARGET` sets where the dev server forwards `/api`
(default `http://127.0.0.1:8080`). In containers nginx forwards `/api` to `backend:8080` and sets
the security headers (`nginx.conf`).

## Commands (from this folder)

| Task | Command |
|---|---|
| install | `npm ci` |
| build | `npm run build` |
| test (unit and component) | `npm test` |
| end-to-end | `npm run e2e` |
| check (format, lint, type check) | `npm run check` |
| format | `npm run format` |
| run (dev server, backend on 8080) | `npm start` |
| coverage, mutation, duplication | `npm run coverage`, `npm run mutation`, `npm run duplication` |

## Troubleshooting

| Symptom | Fix |
|---|---|
| `npm ci` fails with an engine error | use Node.js 24.13.0 (e.g. `nvm use 24.13.0`) |
| e2e: browser not found | `npx playwright install chromium` |
| e2e: setup timeout | Docker must run; the first run builds the backend jar |
| form says "could not be saved" | the backend is not reachable on `/api` |
