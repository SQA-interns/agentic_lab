# Frontend

> Written in: phase 7 · Source: `project/constraints.md` ("Components"), ES-05, ES-06 · Agent: writes

React registration page (`docs/02_contracts/ui-form.json`), built with Vite and served by nginx, which also proxies `/api` to the backend.

## Prerequisites

- Node.js 24.13.0 with npm 11.6.2 (`engine-strict`)
- Docker for the image and for the end-to-end tests (Playwright runs in its container)

## Configuration

The page holds no secret and no environment-specific value (AR-07): it calls `/api` on its own origin and gets the conference name, options, consents and the reCAPTCHA mode and site key from `GET /api/registration-form`. The dev server proxies `/api` to the local stack on http://127.0.0.1:8080 (`vite.config.ts`). nginx (`nginx.conf`) listens on 8080, proxies `/api/` to `backend:8080` and sets the page's security headers.

## Commands

From `02_output/frontend`:

| Purpose                                                          | Command                                                                                                                                                                     |
| ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Install (exact locked versions)                                  | `npm ci`                                                                                                                                                                    |
| Build (type check, then `dist/`)                                 | `npm run build`                                                                                                                                                             |
| Test (unit, component, acceptance)                               | `npm test`                                                                                                                                                                  |
| Test with coverage (`coverage/`)                                 | `npm run coverage`                                                                                                                                                          |
| Check (format, lint, type check)                                 | `npm run check`                                                                                                                                                             |
| Format                                                           | `npm run format`                                                                                                                                                            |
| Run (dev server on http://127.0.0.1:5173, needs the local stack) | `npm run dev`                                                                                                                                                               |
| Run (image, from `02_output`)                                    | `bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose up -d --build --wait frontend` |
| End-to-end tests (stack running, from the repository root)       | `bash 02_output/scripts/verify.sh e2e e2e`                                                                                                                                  |

## Troubleshooting

| Symptom                                                 | Cause and fix                                                                            |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `npm ci` refuses with an engine error                   | install Node.js 24.13.0 / npm 11.6.2                                                     |
| Dev server: "The registration form could not be loaded" | the local stack is not running; start it (root README)                                   |
| Page shows "I am not a robot (test mode)"               | the backend runs with `RECAPTCHA_TEST_MODE=true`; production shows Google reCAPTCHA      |
| End-to-end tests cannot reach `frontend`                | the Playwright container joins the network `registration_default`; start the stack first |
