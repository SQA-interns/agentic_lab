# Preflight Checklist

Complete this **before** the run starts — before `experimentStart` is
recorded. This is a human step performed against the workspace, not a
timed part of the run. If anything here fails, fix it now; a run that
discovers a missing prerequisite mid-process (as the first run in this
series did, losing 92 minutes waiting for a Docker install after
already merging) has let a setup problem become a measured part of the
experiment.

## Environment

- [ ] The container runtime named in `02_Technical/DEPLOYMENT_CONSTRAINTS.md`
      is installed and its daemon is running (e.g. `docker info` succeeds).
- [ ] The language runtime and build tool named in
      `02_Technical/TECH_STACK.md` are installed at the required major
      version, or the scaffold's own wrapper (`mvnw`, etc.) is present
      and executable without a separate install.
- [ ] Network access to every registry the scaffold depends on (Maven
      Central, the npm registry, the container image registries used
      in `05_Scaffold/docker-compose.yml`) is available from this
      machine.
- [ ] Git remote access is available if this run's branch will be
      pushed anywhere outside the local workspace.

## Human inputs

- [ ] Every row in `HUMAN_INPUTS_MANIFEST.md` is resolved — either
      provided, or explicitly marked as using a local/test-mode
      substitute for this run.

## Version sanity

- [ ] Every pinned version in `05_Scaffold/VERSION_PINS.md` was
      verified resolvable against its registry within the last 30
      days. If not, re-verify now and update that file's date before
      freezing this input package — do not let the agent discover a
      broken pin mid-run, the way two earlier runs in this series each
      independently did with the same plugin.

## Workspace

- [ ] `WORKSPACE_ROOT` contains only `01_Input_Files/` (or it is about
      to be copied in fresh), with no leftover `02_Implementation/` or
      `03_Run-Statistics/` content from a previous run.
- [ ] The baseline Git commit is clean (`git status` reports no
      changes) and is the intended starting point for this run.

Only once every box above is checked does the agent record
`experimentStart` and begin the start-of-run setup sequence in
`RUN_INSTRUCTIONS.md`.
