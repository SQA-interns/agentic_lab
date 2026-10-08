# Frontend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

React registration form (`docs/02_contracts/ui-form.json`), built with Vite and served by nginx, which adds the security headers and proxies `/api` to the backend.

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2 on `PATH` (`engine-strict` is on)
- Docker Engine for the end-to-end tests

## Configuration

The frontend has no secrets. The reCAPTCHA site key and test mode come from the backend (`GET /api/form`).

## Build, check, test, run

```sh
npm ci                     # install exactly the locked versions
npm run build              # type check and build into dist/
npm run check              # prettier, ESLint, tsc
npm test                   # unit and acceptance tests (Vitest)
npm run test:e2e           # end-to-end: builds and starts its own stack on 127.0.0.1:18081
../scripts/compose.sh up -d --build --wait frontend   # run: http://127.0.0.1:8081 (starts backend, db, mailpit)
```

Mutation testing: `npm run mutation` (about 20 minutes). Duplication: `npm run duplication`.

## Troubleshooting

- `npm ci` fails with an engine error: use Node.js 24.13.0 / npm 11.6.2.
- End-to-end setup fails on ports 18081 or 18025: free them; the tests never reuse a running stack.
- The form shows "The registration form is not available right now": the backend or the `/api` proxy is not reachable.
