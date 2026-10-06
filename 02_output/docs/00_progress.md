# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 1 (requirements); phase 0 ended 2026-10-06T11:45:02Z
- Last gate result: phase 0 passed 2026-10-06T11:45:02Z (`docs/00_preflight-report.md`; `verify.sh 00 all` 19/19 exit 0)
- Next step: phase 1, `skills/derive-acceptance-criteria` from `project/requirements.md` → `docs/01_acceptance-criteria.md`
- Waiting for the human on: nothing

## Run notes

- Node: call `~/.nvm/versions/node/v24.13.0/bin` (D-01); `verify.sh` prepends it. The shell is zsh: run loops over command strings under `bash -c`.
- Stack amendments: vitest and @vitest/coverage-v8 4.1.11 (D-07). Dependency-Check suppressions: D-08, D-09.
- Component commands (ES-05): backend build `./mvnw -B -ntp -DskipTests package`, test `./mvnw -B -ntp test`, check `./mvnw -B -ntp -DskipTests verify` (spotless, pmd, cpd, spotbugs), run `./mvnw spring-boot:run`; frontend build `npm run build`, test `npm test`, check `npm run check`, run `npm run dev`.
- `03_statistics/run-log.json` stays untracked until the phase 7 commit.
- Other runs' containers (`agenticlab-*`, ports 18080, 18025) are running; pick other host ports for this stack.
