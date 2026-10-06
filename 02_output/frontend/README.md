# Frontend

> Written in: phase 7 · Source: ES-05, ES-06, `docs/02_contracts/ui-registration-form.json` · Agent: writes

React 19 single-page registration form (external participant or student), built with Vite and served by unprivileged nginx, which also proxies `/api` to the backend.

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2
- Docker, for the image and the end-to-end tests (Playwright runs in its container)

## Configuration

The frontend holds no secret. Everything it shows comes from `GET /api/form-config`, including the public reCAPTCHA site key.

| Setting | Where | Notes |
|---|---|---|
| `BACKEND_UPSTREAM` | nginx container environment | backend host:port for `/api` (default `backend:8080`) |
| `BACKEND_URL` | `npm run dev` environment | backend for the dev-server proxy (default `http://127.0.0.1:8080`) |
| `E2E_BASE_URL`, `MAILPIT_URL` | end-to-end run | defaults in `../scripts/verify.sh` |

User-visible texts are in `src/texts.ts`.

## Build

```sh
npm ci
npm run build
```

Output in `dist/`; `docker build -t registration-frontend .` builds the nginx image.

## Run

```sh
npm run dev
```

Dev server on http://127.0.0.1:5173 with `/api` proxied to `BACKEND_URL`; for the complete stack see `../README.md`.

## Test

```sh
npm test
```

Unit, component and frozen acceptance tests (`src/acceptance/`). Coverage: `npm run coverage`. Mutation: `npm run mutation`. End-to-end against the running stack, from the repository root: `02_output/scripts/verify.sh e2e e2e`.

## Check

```sh
npm run check
```

Prettier, ESLint and `tsc --noEmit`. Duplication: `npm run duplication`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| page shows "The registration form could not be loaded" | backend not reachable under `/api` |
| `npm ci` fails with an engine error | wrong Node/npm version; use Node 24.13.0 |
| reCAPTCHA widget missing | backend in live mode with a site key that Google rejects for this host |
