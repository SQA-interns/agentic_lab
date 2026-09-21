# Conference registration system

The application provides external-participant and student registration forms, configurable conference activities, relational and JSON persistence, durable confirmation/organizer email jobs, and an authenticated Excel export.

## Local development

Python 3.12 is required. Create a virtual environment, install `requirements-dev.txt`, then run:

```sh
alembic upgrade head
uvicorn app.main:app --reload
```

Development defaults use `data/registration.db`, `data/backups`, the checked-in example conference configuration, and a local SMTP server on port 1025. API documentation is available at `/docs` outside production. Export uses `Authorization: Bearer development-export-token` only in development.

## Production deployment

1. Copy `.env.example` to `.env` and replace every example secret. Create `secrets/database_password.txt` with a strong database password and `secrets/database_url.txt` with the matching percent-encoded URL, for example `postgresql+psycopg://registration:PASSWORD@db/registration`. Do not commit these files.
2. Set the public HTTPS origin, trusted host, SMTP TLS settings, sender, and organizer recipients.
3. Run `docker compose build`, then `docker compose up -d`. The one-shot migration service finishes before web and worker start.
4. Terminate TLS at a trusted ingress that forwards only from an explicitly permitted address. The image accepts proxy headers only from loopback by default; adjust the command to the actual ingress network deliberately.
5. Check `/health/live` and `/health/ready`. Normal logs contain correlation IDs and outcomes but omit participant data.

The root filesystem is read-only in Compose. PostgreSQL and `/data/backups` are independent persistent volumes. The processes run as a non-root user.

## Conference option changes

Replace `config/conference-options.json` atomically with a valid file. Stable option and consent identifiers must never be recycled for different meanings. Active options appear on the applicable variants; inactive options cannot be submitted. An invalid replacement makes readiness and registration unavailable until corrected, while existing records retain their stored snapshots.

## Organizer export

Request `GET /api/v1/organizer/registrations.xlsx` with the configured bearer token. Serve this only through HTTPS and rotate the token by replacing `EXPORT_TOKEN` and restarting the web service.

## Email operations

The worker retries transient SMTP failures with bounded exponential delay. Inspect failed jobs in the `email_outbox` table without copying participant data into logs. After correcting the cause, requeue them with:

```sh
docker compose run --rm worker python -m app.worker --requeue-failed
```

The organizer message uses and integrity-checks the correlated JSON file from persistent storage.

## Backup and recovery

Back up both the PostgreSQL database and `registration-backups` volume as one operational set. Database rows contain the JSON filename, size, and SHA-256 checksum. During recovery, restore both resources, run migrations, verify that referenced JSON files exist and match their checksums, and only then start web/worker traffic. A registration is acknowledged only after its database transaction and atomic JSON write complete.

## Security maintenance

Before release, run the documented lint, type, dependency-audit, Bandit, test, migration, and container-build checks. Rotate the challenge, network-hash, export, database, and SMTP secrets through the deployment secret store. A challenge-secret rotation invalidates outstanding forms, so perform it during a communicated maintenance window.
