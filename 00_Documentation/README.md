# Researcher guide — v3, not read by the development system

Root directories remain exactly `00_Documentation`, `01_Input_files`,
`02_Implementation`, `03_Metrics`. Enforce read-only inputs and no access to
documentation with runner permissions. Grant the system write access to both
output directories. Do not assume Markdown enforces permissions.

Launch from project root with:

> Read `01_Input_files/START_HERE.md` and complete the conference registration
> task using the single-agent specification-driven workflow. Maintain metrics
> as instructed and finish with an evidence-backed run summary.

Supply a stable configuration label and budgets through the runner; keep the
bootstrap identical between repetitions. No root AGENTS.md is required.
Git metadata must be runner-managed outside the four directories; grant access
to that metadata if system commits are enabled. Otherwise snapshots/checkpoint
hashes suffice. Implementation and metrics start empty.

## Changes inherited from v2

- Definition of Done now has its own discoverable file and is read before design.
- The system writes its own research records, including the detailed summary.
  No separate observer is necessary to complete a run. Sources and missingness
  distinguish measured values from self-reported interpretations.
- Configuration comparison centers on completed behavior, rule adherence,
  elapsed time, resource use, rework and human effort. Avoidable token waste
  cannot be directly inferred from private thinking or elapsed silence.
- One specification contains acceptance criteria, design, decisions and plan;
  one run summary contains verification and analysis. Removed duplicate reports
  and elaborate per-call manual logging to reduce measurement overhead.
- This baseline is explicitly one agent, spec-first, with incremental testing.
  It differs from the source's classical code-first/test-later phase sequence.

## Comparison protocol

Compare whole configurations. First keep the same model snapshot/settings,
task, product/architecture/DoD, dependency environment, budgets, tools and
logging burden while varying orchestration. If model/tool access also changes,
name it a bundled configuration comparison; do not attribute the difference
solely to agent count. Save the exact input/runner revisions for each condition.

Repeat the same tasks under each condition with fresh outputs/context and
counterbalanced order. Three repetitions are a smoke pilot, not convincing
evidence of superiority. Report per-task success and uncertainty, elapsed-time
and resource distributions, and all failures/timeouts. A terminated run may
not write a summary: record termination from the runner and retain its partial
logs; missing summaries must not make failed attempts disappear.

System-written metrics are an automation trade-off, not independent ground
truth. Spot-check evidence and compare self-verdicts with a stable external
acceptance suite when possible. Agent-derived AC counts are not comparable
quality denominators: keep the eight source stories and DoD gates fixed and
review substantive coverage. Distinguish input-conflict/environment failures
from implementation failures, while retaining their costs in total results.

Preflight Docker/JDK/Node and required scanner availability. Establish dependency/scanner baseline feasibility before comparative trials; a
changed stack/security rule must create a new input revision for all conditions.
We have not run these scans or proven the dependency set secure.

## Provenance

Product source: SQA-interns/agentic_lab, branch
`opus5.5_single_agent_classic_sdd_run`, commit
`8ca67074f887c29441d880d1ced8f9b62afa3616`, input directory.
Reporting reference: optimized-input branch summary at commit
`c4201034ac33de8c6c9af77a9cabcc7b6d8ea866`.
The concrete architecture and revised process are this experiment's choices;
no implementation or measured results have been copied.

## v3 audit and strict slices

Read RESEARCH_REVIEW.md for evidence, limitations, source-fidelity corrections,
and unresolved experimental choices. WORKFLOW now distinguishes fixed M0–M6
milestones from agent-chosen smaller slices, defines dependencies and pass evidence,
and adds resume/rollback rules. PRODUCT has ten fixed behavior probes. METRICS
records milestone regressions and environment/context configuration. DoD now
explicitly prohibits weakening correct tests and distinguishes self-verification.

Correction: v2 froze Spring Boot at 3.5.16 without a source-input basis. Source
TECH_STACK says 3.x; source scaffold starts at 3.4.4. v3 restores 3.x and requires
an exact generated dependency realization. React 19, Vite 6 and Node 22 derive
from the input scaffold's manifest/Dockerfile, not a previous implementation.
No scaffold code, implementation, or statistics were copied.

This is a pilot-ready protocol draft, not yet a frozen comparative benchmark.
To isolate orchestration, select compatible exact dependency/tool/rule versions
in a preflight pilot and freeze them in TECH_STACK before all measured trials.
That does not require a scaffold or duplicate manifest. If each system chooses
versions instead, report that as part of the configuration's task, not a controlled
constant. Never mix v2 and v3 results as if only the agent configuration changed.

No app was implemented or tested while preparing this package. The output
directories are deliberately empty. Input integrity/links/layout were checked;
this does not validate dependency feasibility or app correctness.
