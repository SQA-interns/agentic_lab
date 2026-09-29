# Architecture

> Owner: Architect · Read in: phases 0, 2, 4, 6, 7 · Agent: read-only

## Components

Each component gets its own folder and README in `02_output/`. Versions come only from `tech-stack.md`; reference its ids, never repeat a version here.

```yaml
components:
  - name: <unique name>
    type: <web-app | api | service | mobile-app | desktop-app | library | cli | …>
    folder: 02_output/<folder>
    platform: <platform id from tech-stack.md>
    build: <tooling id from tech-stack.md>
    bootstrap: <generator or init command, pinned to tech-stack.md versions>
    commands:          # ES-05; filled in phase 0 if left empty
      build:
      test:
      check:
      run:
    depends_on: [<component name>, …]
    deploys_as: <container image | package | store build | installer | …>
```

## Constraints

Checkable rules the code must respect (layering, allowed dependencies, patterns). Checked automatically in phase 6 where the stack allows (DoD-04).

<!-- AR-01 … -->

## Interfaces

Every interface between components or with external systems is defined as a contract in `docs/02_contracts/` in phase 2.

| Interface | Between | Style (e.g. REST, gRPC, events, file, UI) |
|---|---|---|
