# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 6 (verify); phase 5 ended 2026-10-06T16:33:18Z
- Last gate result: phase 5 passed 2026-10-06T16:33:18Z (first run 233/0 recorded; suite green; coverage backend 95.3/90.1, frontend 97.7/91.1; mutation backend 94% (D-23 scope), frontend 84.9%; survivors classified in `docs/03_test-strategy.md`). Phase 4 passed 13:36:21Z, phase 3 12:44:45Z (freeze bf86f29, re-frozen dad715c after D-21).
- Next step: phase 6, `skills/verify-release`: `verify.sh 06 all` + e2e, manifest hash checks, `scripts/runtime-demo.sh` (written, not yet run or committed), SB/SR evidence, leak-check, traceability, verification report.
- Waiting for the human on: Docker Desktop is down again (needed for backend tests, e2e, scanners in containers, runtime demo).

## Run notes

- Node: call `~/.nvm/versions/node/v24.13.0/bin` (D-01); `verify.sh` prepends it. The shell is zsh: run loops over command strings under `bash -c`.
- Stack amendments: vitest and @vitest/coverage-v8 4.1.11 (D-07). Dependency-Check suppressions: D-08, D-09.
- Component commands (ES-05): backend build `./mvnw -B -ntp -DskipTests package`, test `./mvnw -B -ntp test`, check `./mvnw -B -ntp -DskipTests verify` (spotless, pmd, cpd, spotbugs), run `./mvnw spring-boot:run`; frontend build `npm run build`, test `npm test`, check `npm run check`, run `npm run dev`.
- Local stack: start command in the `02_output/docker-compose.yml` header; 127.0.0.1:8088 frontend, 8026 Mailpit.
- `03_statistics/run-log.json` stays untracked until the phase 7 commit.
- Other runs' containers (`agenticlab-*`, ports 18080, 18025) may run; this stack uses 8088 and 8026.
- Docker is Docker Desktop (VM): containers reach host ports as `host.docker.internal`, not via `--network host`.
- Do not use `pkill -f <pattern>` inside a compound command: it matches its own shell and kills it.
- Run long jobs (mutation, full suites) with run_in_background: several sessions ended during long foreground calls.
- Process slips to report in phase 6: 4e3b1a6 has 758 lines without a reason in the message; 68b473a mixes decisions-log.md into a feat commit.
