# Frontend

Registration page for external participants and students (`docs/02_contracts/ui-registration-form.json`). React 19, Vite 6, TypeScript. It talks to the backend only through `/api` and contains no secret; the reCAPTCHA site key comes from `GET /api/form-config`.

## Prerequisites

- Node.js 24 with npm.
- Docker, for the end-to-end tests (Playwright container) and the image.

## Configuration

No settings at build time. In the image, nginx serves the page on port 8080 and forwards `/api/` to the host `backend` (`nginx.conf`). The development server forwards `/api` to `http://127.0.0.1:8080` (`vite.config.ts`).

## Build

```bash
npm ci
npm run build
```

Output in `dist/`. The container image: `docker compose build frontend` from `02_output`.

## Run

With the local stack running (backend README, "Run"), the page is at `http://127.0.0.1:8081`. For development against that backend:

```bash
npm run dev
```

The page is then at `http://127.0.0.1:5173`.

## Test

```bash
npm test
npm run coverage
npm run mutation
```

Unit and acceptance tests (`src/*.test.*`, `tests/acceptance/`) run in jsdom. End-to-end tests (`tests/e2e/`) need the running stack and run in the Playwright container; from the repository root:

```bash
bash 02_output/scripts/verify.sh e2e e2e
```

## Check

```bash
npm run check
```

Prettier, ESLint and the TypeScript type check. `npm run format` fixes formatting.

## Troubleshooting

- "The registration form could not be loaded": the backend is not reachable at `/api`; check `docker compose ps` in `02_output`.
- End-to-end tests fail with `getaddrinfo ENOTFOUND frontend`: start the stack first; the tests join the network `registration-tanej04_default`.
- `npm run mutation` leaves `.stryker-tmp/` after an interruption; delete it.
