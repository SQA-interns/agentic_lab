# Version Pins — Verified Resolvable

Every pinned tool/plugin version in this scaffold, and the date each
was last confirmed to actually resolve against its registry. Re-verify
and update this file before freezing a new copy of the scaffold — see
`03_Process/PREFLIGHT_CHECKLIST.md`.

## Backend (Maven Central)

| Artifact | Version | Verified | Note |
| --- | --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-parent` | 3.4.4 | 2026-09-29 | |
| `com.diffplug.spotless:spotless-maven-plugin` | 2.44.3 | 2026-09-29 | |
| `com.github.spotbugs:spotbugs-maven-plugin` | 4.10.4.1 | 2026-09-29 | Corrected from `10.12.15`, which does not exist on Maven Central (HTTP 404) — see History below. |
| `org.apache.maven.plugins:maven-pmd-plugin` | 3.26.0 | 2026-09-29 | |
| `org.jacoco:jacoco-maven-plugin` | 0.8.12 | 2026-09-29 | |
| `org.owasp:dependency-check-maven` | 12.1.0 | 2026-09-29 | Needs an NVD API key or a local NVD cache to run fully; see `HUMAN_INPUTS_MANIFEST.md`. |
| `com.tngtech.archunit:archunit-junit5` | 1.3.2 | 2026-09-29 | |

## Frontend (npm)

See `frontend/package.json` / `package-lock.json` for the exact pinned
set. Before freezing, run `npm install --dry-run` (or check each
package against https://registry.npmjs.org) and confirm every version
still resolves.

## Containers

See `docker-compose.yml` for pinned image tags (database, base images,
etc.). Before freezing, confirm each tag still exists on its registry.

## History

- **2026-09-29** — `spotbugs-maven-plugin` corrected from `10.12.15`
  (nonexistent) to `4.10.4.1` (the actual latest release). Two
  consecutive runs in this experiment series
  (`opus5.5_single_agent_classic_sdd_optimized_input`,
  `opus5.5_tanej_claude_desktop`) each independently hit this pin,
  discovered it was broken mid-run, and silently repinned it
  themselves without escalating. This file — and the preflight check
  that reads it — exist so a third occurrence doesn't happen, and so
  that if it does, it's caught before the run starts rather than
  during it.
