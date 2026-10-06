# Frontend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

React single-page registration form (`docs/02_contracts/registration-form.schema.json`); talks to the backend only through relative `/api` paths. In the container, nginx serves the static files and proxies `/api/` to the backend.

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2 (`tech-stack.md`).
- For end-to-end tests: Docker Engine and Playwright's Chromium (`npx playwright install chromium`).

## Configuration

No build-time configuration and no secrets (AR-07): the reCAPTCHA site key, test mode, options and consent text come from `GET /api/options`. The dev server proxies `/api` to `API_PROXY_TARGET` (default `http://127.0.0.1:8080`).

## Commands

| Purpose                                                        | Command (in `02_output/frontend`)                    |
| -------------------------------------------------------------- | ---------------------------------------------------- |
| Install                                                        | `npm ci`                                             |
| Build (type check + bundle into `dist/`)                       | `npm run build`                                      |
| Test (unit, component, acceptance; coverage in `coverage/`)    | `npm run test:coverage`                              |
| End-to-end (starts its own stack `regtest-e2e` on 18090/18025) | `npm run test:e2e`                                   |
| Check (format, lint, type check)                               | `npm run check`                                      |
| Mutation / duplication                                         | `npm run mutation` / `npm run duplication`           |
| Run (dev server against the compose stack)                     | `API_PROXY_TARGET=http://127.0.0.1:8090 npm run dev` |

The dev server listens on http://127.0.0.1:5173.

## Troubleshooting

| Symptom                             | Fix                                                                      |
| ----------------------------------- | ------------------------------------------------------------------------ |
| `npm ci` fails with an engine error | use Node 24.13.0 / npm 11.6.2 (`engine-strict`)                          |
| Form shows "could not be loaded"    | backend not reachable: start the compose stack or set `API_PROXY_TARGET` |
| e2e: port 18090 or 18025 in use     | stop the process using it; e2e never reuses a running server (KP-08)     |
| e2e: browser missing                | `npx playwright install chromium`                                        |
