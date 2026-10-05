# Frontend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-05, ES-06 · Agent: writes

React page with the external participant and student forms (`docs/02_contracts/registration-form.ui.json`).
It talks to the backend only through relative `/api` URLs.

## Prerequisites

- Node.js 24 with npm
- Docker, for the image and the end-to-end tests (they run in the Playwright container)

## Configuration

The frontend has no secret and no build-time setting: the conference name, the options, the consent
wording, the reCAPTCHA mode and the site key come from `GET /api/form-config` at run time. The nginx
image needs one environment variable:

| Variable      | Source                                                                          |
| ------------- | ------------------------------------------------------------------------------- |
| `BACKEND_URL` | `../docker-compose.yml` (local: `http://backend:8080`); `/api` is proxied there |

## Build

```bash
npm ci
npm run build                          # dist/
docker build -t registration-frontend .
```

## Run

Normally as the `frontend` service of `../docker-compose.yml` (http://127.0.0.1:8081). For development
against a backend on 127.0.0.1:8080 (Vite proxies `/api`):

```bash
npm run dev                            # http://127.0.0.1:5173
```

## Test

```bash
npm test                               # unit and component tests with coverage
bash ../scripts/verify.sh <phase> e2e  # end-to-end tests against the running local stack
npm run mutation                       # Stryker mutation testing
```

## Check

```bash
npm run check                          # Prettier, ESLint, TypeScript
npm run format                         # fix formatting
```

## Troubleshooting

| Symptom                                               | Cause and fix                                                                                                 |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| page shows "Something went wrong" instead of the form | `/api/form-config` is unreachable; check that the backend is healthy                                          |
| `npm ci` fails on the lock file                       | use npm 11 / Node.js 24 as in `tech-stack.md`                                                                 |
| end-to-end tests cannot connect                       | start the stack first (`../README.md`); they use the network `registration_default`                           |
| the reCAPTCHA widget does not appear                  | outside test mode the site key from the backend is empty or the CSP blocks Google; check `RECAPTCHA_SITE_KEY` |
