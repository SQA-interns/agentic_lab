# Implementation Notes (Phase 4)

Record of added dependencies and implementation decisions that are not
already fixed by `docs/specification.md`.

## Added dependencies

| Dependency | Scope | Why |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-security` (version managed by Spring Boot 3.4.4) | runtime | Organizer HTTP Basic access control, security headers, stateless filter chain (TECH_STACK permits Spring Security for these). |
| `org.apache.poi:poi-ooxml` 5.5.1 | runtime (added as test scope in Phase 3 for the acceptance suite, promoted to compile scope here) | Writing the `.xlsx` export (US-008); the acceptance tests read it back with the same library. |

Frontend: no new npm dependencies (React, Vite, TypeScript from the
scaffold only).

Test-only configuration added in Phase 3: `backend/src/test/resources/docker-java.properties`
(decision D-7).

## Decisions

1. **Where settings live.** `application.yml` contains no key that
   Spring Boot already binds from an environment variable
   (`SPRING_DATASOURCE_*`, `SPRING_MAIL_HOST/PORT`, `SERVER_PORT`,
   `APP_OPTIONS_FILE`, `APP_BACKUP_DIR`, `APP_ORGANIZER_*`) and no
   `app.recaptcha.test-mode`: a value in `application.yml` outranks
   properties passed programmatically (e.g. `SpringApplicationBuilder`
   defaults, which the restart acceptance test uses), so it would shadow
   them. Local-development defaults for those keys (database URL, SMTP
   `localhost:1025`, options file, backup directory, and
   `app.recaptcha.test-mode=${RECAPTCHA_TEST_MODE:false}`) are set as
   default properties in `Application.main`. Database and organizer
   credentials have **no** default anywhere in source; the scaffold's
   `conference`/`conference` datasource defaults were removed.
2. **Transaction and backup.** The database insert and the JSON backup
   write happen inside one `TransactionTemplate` callback; if anything
   fails (including the commit), the backup file is deleted and the
   client gets `500 REGISTRATION_NOT_SAVED`. Emails are sent after the
   commit and their failures are only logged (D-3).
3. **Options file change detection** compares the file's last-modified
   time and size on every catalog read (a `stat`, not a read), and
   re-reads only on change. The sync runs in its own transaction and is
   `synchronized` per application instance.
4. **Rate limiting and body-size limiting** are servlet filters placed
   inside the Spring Security chain (after the header writer), so 413
   and 429 responses also carry the security headers.
5. **Problem Details.** Spring Boot's built-in problem-details advice
   is not enabled; `web.ApiExceptionHandler` produces every error body
   so each carries the contract `code`.
6. **Compose project name** is `conference-registration-testrun-latest`:
   this machine already had images and volumes from earlier runs under
   the default and the `conference-registration` project names (one
   with a different database password). They were left untouched.
7. **Local secrets.** `02_Implementation/.env` (git-ignored) holds a
   generated organizer password and database password for the local
   compose stack (HUMAN_INPUTS_MANIFEST row 3, "generated locally").
   `.env.example` documents the variables.
