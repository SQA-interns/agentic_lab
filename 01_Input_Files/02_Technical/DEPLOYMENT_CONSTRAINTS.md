# Deployment Constraints

## Local development and test

Local development may use HTTP.

The environment may include:

- frontend;
- backend;
- PostgreSQL;
- local SMTP service.

## Production

Frontend and backend are deployed as containers.

Containers expose HTTP internally.

An external nginx reverse proxy provides:

- HTTPS termination;
- Let's Encrypt certificate handling;
- frontend routing;
- forwarding of `/api` requests to the backend.

TLS termination therefore does not need to occur inside the application
containers.

## Persistence

Persistent data must survive container recreation.

This includes:

- relational database data;
- raw JSON registration backups.

## Configuration

Environment-specific values and secrets must not be hard-coded into
application source code.