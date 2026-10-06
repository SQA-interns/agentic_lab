# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 5 (unit tests); phase 4 ended 2026-10-06T13:36:21Z
- Last gate result: phase 4 passed 2026-10-06T13:36:21Z (`verify.sh 04` 15/15 exit 0: backend 101/101 incl. ArchUnit, frontend 19/19; e2e 10/10 on the compose stack; commits per story in `logs/04_commits-since-freeze.log`). Phase 3 passed 12:44:45Z (freeze bf86f29, re-frozen dad715c after D-21).
- Next step: phase 5, `skills/unit-tests`: unit/integration tests for validation, security, persistence, rules, error paths; record first complete run; coverage and mutation.
- Local stack is running (`conference-registration`, 127.0.0.1:8088 frontend, 8026 Mailpit); start command in `02_output/docker-compose.yml` header.
- Phase 4 so far: backend US-001..US-008 + SR-03/SR-06 committed (last d19c6f5); `be-test` 88/92, the 4 failures are D-21. Not done: ArchUnit rules, frontend (US-001..US-004 UI), Dockerfiles/nginx/compose, e2e run. Process slips to report in phase 6: 4e3b1a6 has 758 lines without a reason in the message; 68b473a mixes decisions-log.md into a feat commit.
- D-21 resolved 2026-10-06T13:21:34Z (option 1; d319d31, dad715c).
- Waiting for the human on: nothing

## Run notes

- Node: call `~/.nvm/versions/node/v24.13.0/bin` (D-01); `verify.sh` prepends it. The shell is zsh: run loops over command strings under `bash -c`.
- Stack amendments: vitest and @vitest/coverage-v8 4.1.11 (D-07). Dependency-Check suppressions: D-08, D-09.
- Component commands (ES-05): backend build `./mvnw -B -ntp -DskipTests package`, test `./mvnw -B -ntp test`, check `./mvnw -B -ntp -DskipTests verify` (spotless, pmd, cpd, spotbugs), run `./mvnw spring-boot:run`; frontend build `npm run build`, test `npm test`, check `npm run check`, run `npm run dev`.
- `03_statistics/run-log.json` stays untracked until the phase 7 commit.
- Other runs' containers (`agenticlab-*`, ports 18080, 18025) are running; this stack uses 8088 (frontend) and 8026 (Mailpit).
- Docker is Docker Desktop (VM): containers reach host ports as `host.docker.internal`, not via `--network host`.
- Do not use `pkill -f <pattern>` inside a compound command: it matches its own shell and kills it.
