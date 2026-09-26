# Frozen tooling scaffold

This directory is part of `INPUT_ROOT` and is read-only.

It is a **tooling scaffold**, not an application.

At run start, copy its contents to `IMPLEMENTATION_ROOT`:

`<WORKSPACE_ROOT>/02_Implementation/`

Do not implement inside `05_Scaffold/`.

Included:

- Maven / Spring Boot bootstrap and analysis plugins;
- React / Vite / TypeScript bootstrap and frontend tools;
- pinned Docker Compose services;
- Semgrep configuration stub.

Not included:

- domain controllers, services, repositories or entities;
- REST endpoints;
- database tables or Flyway domain migrations;
- registration forms;
- feature tests;
- ArchUnit rules that prescribe a layered architecture.
