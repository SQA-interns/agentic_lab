# Frontend

> Written in: phases 0, 7 · Source: `project/02_design/architecture.md` · Agent: writes

React registration form built with Vite and served by nginx. It talks to the backend only
through `/api` and holds no secret; the reCAPTCHA site key comes from the backend at run time.

## Prerequisites

- Node.js 24 with npm
- Docker, for the container image and the end-to-end tests

## Commands (ES-05)

Run in `02_output/frontend`, after `npm ci`.

| Purpose | Command         |
| ------- | --------------- |
| build   | `npm run build` |
| test    | `npm test`      |
| check   | `npm run check` |
| run     | `npm run dev`   |

- Run serves the form at http://127.0.0.1:5173/ and forwards `/api` to the local stack at
  http://127.0.0.1:8080, so start the stack first (`../README.md`, "Quick start").
- Format the sources with `npm run format`.
- Coverage report after test: `coverage/index.html`.
- Mutation tests: `npx stryker run`.
- End-to-end tests run in the Playwright container against the local stack, from the repository
  root: `bash 02_output/scripts/verify.sh 7 stack-up e2e`.

## Configuration

| Setting                                                                    | Source                          | Notes                                                                                                        |
| -------------------------------------------------------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| conference name, consent wording, anti-automation mode, reCAPTCHA site key | backend, `GET /api/form-config` | nothing is configured at build time                                                                          |
| conference options                                                         | backend, `GET /api/options`     |                                                                                                              |
| address of the backend                                                     | `nginx.conf`                    | the container forwards `/api` to the host name `backend`, port 8080; the name must resolve when nginx starts |

The frontend has no secrets.

## Container

`docker compose --env-file ../../.env -f ../docker-compose.yml up -d --build --wait` builds the
image and starts it with the rest of the local stack; the form is then at http://127.0.0.1:8080/.
The container runs nginx as a non-root user on port 8080.

## Troubleshooting

| Symptom                                                           | Cause and fix                                                                                                                   |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| "Obrazca ni bilo mogoče naložiti" on the page                     | the backend is not reachable under `/api`; start the local stack                                                                |
| `npm run dev` shows the page but `/api` fails                     | the local stack is not running on 127.0.0.1:8080                                                                                |
| nginx container exits with `host not found in upstream "backend"` | no host named `backend` on the container network; start it with the compose file or add a network alias                         |
| reCAPTCHA widget does not appear in production                    | the page's Content-Security-Policy in `nginx.conf` must allow the Google reCAPTCHA origins; see the release notes, manual tests |
| `npm ci` warns about the Node engine                              | use Node.js 24.13 as listed in `tech-stack.md`                                                                                  |
