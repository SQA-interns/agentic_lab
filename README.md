# Conference Registration System

Online registration for a conference: two registration forms (external participant and
student), reliable storage with a JSON backup, confirmation and notification emails, and
an Excel export for organizers.

The requirements are in [`USER_STORIES.md`](USER_STORIES.md),
[`PROJECT_CONSTRAINTS.md`](PROJECT_CONSTRAINTS.md) and [`FORM_SCHEMA.md`](FORM_SCHEMA.md).
The derived artefacts are [`docs/acceptance-criteria.md`](docs/acceptance-criteria.md),
[`docs/specification.md`](docs/specification.md) and
[`docs/verification-report.md`](docs/verification-report.md).

## Architecture in one paragraph

A layered TypeScript backend (Express 5) exposes a small REST API. A registration is
validated, stored in a relational database (SQLite) and written as a JSON file inside the
same transaction, so either both artefacts exist or neither does. Only after both are
durable does the API return `201`, which is the only trigger for the frontend
confirmation. The participant confirmation and the organizer notification — with the JSON
file attached — are sent afterwards and can never fail an accepted registration. The
frontend is a two-page Vite build with no UI framework; it takes its fields, option lists
and validation rules from the backend. Details and the reasoning behind each choice are in
[`docs/specification.md`](docs/specification.md).

```
frontend (nginx :8080) ──/api──▶ backend (Express :3000) ──▶ SQLite + JSON files (/data)
                                            └──▶ SMTP
```

## Running with containers

```bash
cp .env.example .env    # fill in SMTP, organizer addresses, export credentials, secrets
docker compose up --build
```

* Registration forms: <http://localhost:8080/> and <http://localhost:8080/studentska-prijava/>
* Excel export: <http://localhost:8080/api/export/registrations.xlsx> (HTTP Basic, `EXPORT_USERNAME` / `EXPORT_PASSWORD`)

The `registration-data` volume holds the SQLite database and `registrations/*.json`, so
all registrations survive a container restart or recreation.

## Running locally without containers

```bash
npm install
cp .env.example .env     # set DATA_DIR=./data, MAIL_TRANSPORT=json for local use
npm run build --workspace backend
npm run start:local --workspace backend   # backend on :3000
npm run dev --workspace frontend          # frontend on :5173, proxies /api to :3000
```

## Configuring the conference programme

Workshops, events, meals and other activities live in
[`config/conference-options.json`](config/conference-options.json). Each option needs a
stable `id`, a `displayName` and an `active` flag; `availableTo` limits an option to one
registration variant. Changing this file changes what the forms offer and what the backend
accepts — no code change, no database change, no API change. The file is schema-validated
at startup, and an invalid programme stops the application from starting rather than being
discovered at the first registration.

## Configuration

All environment variables, their defaults and which ones are mandatory are listed in
[`.env.example`](.env.example) and documented in
[`docs/specification.md`](docs/specification.md) § 12. The backend refuses to start when a
required value is missing or invalid, naming the variable.

## Quality checks

```bash
npm run lint         # eslint, backend and frontend
npm run typecheck    # tsc --noEmit, backend and frontend
npm test             # vitest, backend and frontend
npm run test:coverage --workspace backend
npm run build        # production build of both workspaces
npm audit            # dependency advisories
```

Against a running containerized stack (`docker compose up -d` first):

```bash
npm run test:e2e:container
```

This runs the same acceptance specs plus deployment-only checks — security headers on the
served documents, the nginx proxy, export authentication — against the images that actually
ship. It needs a `.env` with `RATE_LIMIT_REGISTRATION_MAX` raised: the suite performs more
registrations from one address than the shipped production limit of 5 per 10 minutes allows,
and that limit is covered separately by the API tests.

## Repository layout

| Path | Contents |
| --- | --- |
| `backend/src/domain` | Registration model, validation rules, normalisation, error taxonomy (no dependencies) |
| `backend/src/application` | Use cases: registration, export, form tokens |
| `backend/src/infrastructure` | SQLite, JSON backup store, mail, Excel, logging |
| `backend/src/api` | Express routes and middleware |
| `frontend/src` | Page behaviour, API client, client-side validation, styles |
| `config/` | Conference programme configuration |
| `docs/` | Acceptance criteria, specification, verification report |
| `experiment/` | Experimental run log and summary |
