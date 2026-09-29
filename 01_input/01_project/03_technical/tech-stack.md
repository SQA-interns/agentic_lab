# Tech stack

> Owner: Architect · Read in: phases 0, 2, 4, 6 · Agent: read-only

The single source of truth for every platform, dependency and tool version.
Build manifests and lock files in `02_output/` (e.g. `pom.xml`, `package.json`, `build.gradle`, `*.csproj`, `Podfile`) must match this file exactly; the phase 0 gate checks it. Resolvability and vulnerability results are recorded by the agent in `docs/00_preflight-report.md`.

## Rules

- Exact versions only: no ranges, wildcards, `latest` or floating tags.
- Changing any entry below is a blocking decision (`general/working-rules.md`).
- A dependency not listed here may be added only with an exact version and a non-blocking decision record; it is scanned in phase 6 like any other.
- Every dependency's licence must be compatible with the project's licence.
- `tooling` must include at least one tool for each purpose required by `general/quality/` and `general/security/`: build, format, lint, type check (if the language has one), test, coverage, mutation, static analysis, dependency scan, secret scan.

## Platforms

```yaml
platforms:
  - id: <short id, referenced from architecture.md>
    name: <language, runtime, SDK, OS or device target>
    version: <exact>
    source: <download or distribution URL>
```

## Dependencies

One entry per artifact, written the way the ecosystem writes coordinates.

```yaml
dependencies:
  - id: <coordinates, e.g. group:artifact | package name | image name>
    ecosystem: <maven | gradle | npm | pypi | nuget | cargo | go | swiftpm | cocoapods | container | …>
    version: <exact>
    scope: <runtime | test | build>
    component: <component name from architecture.md | all>
    source: <registry URL>
    purpose: <one line>
    license: <SPDX id>
```

## Tooling

```yaml
tooling:
  - id: <coordinates or tool name>
    ecosystem: <as above | standalone>
    version: <exact>
    purpose: <build | format | lint | type-check | test | coverage | mutation | static-analysis | dependency-scan | secret-scan | …>
    component: <component name | all>
    source: <registry or download URL>
```
