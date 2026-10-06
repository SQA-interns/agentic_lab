# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 4 (build); phase 3 ended 2026-10-06T12:44:45Z
- Last gate result: phase 3 passed 2026-10-06T12:44:45Z (freeze commit bf86f29; 34 frozen files; 57/57 ACs tested; freeze run backend 0/92, frontend 1/19 (bootstrap App test), e2e 0/10, all behavioural). Phase 2 passed 12:21:57Z, phase 1 11:47:32Z, phase 0 11:45:02Z.
- Next step: phase 4, `skills/build`: implement backend then frontend per story until every frozen test passes; ArchUnit rules (spec §2); compose stack for e2e.
- Waiting for the human on: nothing

## Run notes

- Node: call `~/.nvm/versions/node/v24.13.0/bin` (D-01); `verify.sh` prepends it. The shell is zsh: run loops over command strings under `bash -c`.
- Stack amendments: vitest and @vitest/coverage-v8 4.1.11 (D-07). Dependency-Check suppressions: D-08, D-09.
- Component commands (ES-05): backend build `./mvnw -B -ntp -DskipTests package`, test `./mvnw -B -ntp test`, check `./mvnw -B -ntp -DskipTests verify` (spotless, pmd, cpd, spotbugs), run `./mvnw spring-boot:run`; frontend build `npm run build`, test `npm test`, check `npm run check`, run `npm run dev`.
- `03_statistics/run-log.json` stays untracked until the phase 7 commit.
- Other runs' containers (`agenticlab-*`, ports 18080, 18025) are running; this stack uses 8088 (frontend) and 8026 (Mailpit).
- Docker is Docker Desktop (VM): containers reach host ports as `host.docker.internal`, not via `--network host`.
- Do not use `pkill -f <pattern>` inside a compound command: it matches its own shell and kills it.
