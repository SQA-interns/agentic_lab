# Research review and design audit

Prepared 2026-09-28. Researcher-only: do not feed this report to the development
system. This is a targeted primary-source review, not an exhaustive systematic
literature review. It separates empirical findings, practitioner guidance, and
our proposed experiment. No runs were conducted and no efficiency gain is claimed.

## Verdict

The four-directory structure is workable. It is an organizational choice, not
an established agent-performance optimization. Seven short, purpose-specific
input files are defensible; there is no credible evidence here that seven is
optimal, that Markdown beats equivalent structured input, or that minimizing
input words minimizes total cost.

The highest-value improvements are clearer observable contracts, executable
feedback, restart state, feasible dependencies, and a controlled evaluation.
Extra generic skills, persona prompts, duplicated specifications, exhaustive
up-front class designs, and manually narrated tool logs are poor default additions.
Their value would need demonstration in our task distribution.

## What the evidence actually says

| Source and evidence type | Finding | Limitation and consequence for us |
|---|---|---|
| [Gloaguen et al., Evaluating AGENTS.md, v1](https://arxiv.org/html/2602.11988v1), empirical study | Generated repository instructions reduced resolution in five of eight settings; average inference cost rose 20% on SWE-bench Lite and 23% on AGENTbench. Developer-written instructions performed better than generated ones, with costs also increasing. | Existing-repository issue tasks, not greenfield full-stack apps. Keep necessary constraints; do not infer that all context files are harmful or remove essential business requirements. Figures here deliberately reference v1. |
| [On the Impact of AGENTS.md Files on the Efficiency of AI Coding Agents, v1](https://arxiv.org/html/2601.20404v1), empirical preprint | Across 124 PR tasks from ten repositories, median runtime was 28.64% lower and output tokens 16.58% lower with instructions. | Full semantic correctness was explicitly outside scope; a manual sanity check covered 50 outputs. Faster cannot be equated with equally correct. |
| [Khatri, Do Context Files Help Coding Agents?](https://arxiv.org/abs/2607.27250), small preprint ablation | Reports 288 runs on 17 tasks from three repositories with two agents; no measurable correctness change from context strategy. | Narrow repository/task sample and broad equivalence bounds do not establish a universal zero effect. A useful counterweight to universal prompting claims. |
| [Anthropic, Effective harnesses for long-running agents](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents), vendor engineering report | Describes incremental features, persistent progress, Git checkpoints and end-to-end verification to address incomplete work and premature completion. | A full-stack harness example, not a controlled proof of a universally best architecture. We borrow resumability and evidence gates, not its initializer-agent structure or scaffold. |
| [Google, Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html), professional engineering guidance | Recommends self-contained changes with related tests and a working system; discusses both vertical and horizontal decomposition. | Human review guidance. Supports coherent change boundaries, not mandatory file/line/token caps or a claim that every commit must be a full user feature. |
| [GitHub, Response customization](https://docs.github.com/en/copilot/concepts/prompting/response-customization), product guidance | Recommends short, relevant instructions and scoped instructions rather than overloading repository-wide context. | Discovery and precedence are product-specific. Our explicit START_HERE launch is necessary; arbitrary Markdown names are not automatically loaded. |
| [Anthropic, Best practices for Claude Code](https://code.claude.com/docs/en/best-practices), product guidance | Emphasizes usable verification, specific context, project-specific instructions, and pruning content inferable from code. | Vendor advice is not a causal estimate for another model/harness. Permissions must be enforced by the environment. |
| [Anthropic, Effective context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents), engineering guidance | Advocates high-signal context, avoiding both vague instructions and brittle overprescription, and managing long-running state. | No optimal file count or universal instruction budget follows. Load relevant detail when needed, but expose acceptance and architectural invariants early. |
| [Fowler, Architecture Decision Record](https://martinfowler.com/bliki/ArchitectureDecisionRecord.html), architecture practice | Records decisions together with context and consequences; changed decisions can supersede earlier records. | We can preserve these elements in a compact architecture file without creating one file per small choice. |
| [Anthropic, Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents), evaluation guidance | Separates outcomes from traces and agent claims; recommends repeated trials, appropriate graders, and inspecting evaluation failures. | Self-written reports are useful observations, not independent grading. Rigid action-sequence grading can reject valid solutions. |
| [Anthropic, Infrastructure noise](https://www.anthropic.com/engineering/infrastructure-noise), vendor experiment | Reports a six-percentage-point Terminal-Bench difference between extreme resource configurations with model/harness/tasks held constant. | Benchmark-specific effect, not an expected effect size here. Runtime limits and enforcement, caches and tool availability are experimental variables. |

The apparent conflict between instruction-file papers is informative. They study
different agents, tasks, context treatments and outcomes. None establishes the
best greenfield input package for this single-agent setup. Professional guidance
is valuable design evidence, but is not a substitute for testing our hypothesis.

## Audit of our design

### Keep

- Explicit product scope, fixed stack boundaries, architectural invariants,
  and an outcome-based Definition of Done available before implementation.
- Read-only experiment inputs, with actual filesystem enforcement. A prompt
  can request immutability but cannot guarantee it.
- Single-agent baseline, separate frontend/backend for this task, no supplied
  scaffold as requested, and tests alongside implementation.
- One generated specification and one evidence-backed run summary, instead
  of overlapping plans, handoffs, ADR collections and verification reports.
- Separate failure, blocked and unmeasured states. Unavailable tokens are not
  zero; a scanner that cannot run has not found zero vulnerabilities.

### Corrected in v3

1. **Unjustified version freeze.** My v2 fixed Spring Boot at 3.5.16. The source
   specification says 3.x and its input scaffold starts at 3.4.4. v3 restores
   3.x. Exact resolved versions belong in generated manifests/lockfiles and
   must conform to input constraints. A pilot must still freeze exact versions
   if orchestration alone is the intended treatment.
2. **Milestone/slice confusion.** Fixed M0–M6 outcomes now support comparison;
   smaller work slices are chosen by the agent with explicit scope, check and
   dependency. A milestone may span many commits. Security and failure semantics
   are considered early, not bolted on after features.
3. **Weak completion protection.** PRODUCT now includes ten fixed behavior probes.
   The system must not quietly narrow them, skip failing required checks, or
   weaken assertions. Correcting a wrong test requires recorded justification.
4. **Missing resumption procedure.** Current state lives in the generated spec;
   history/evidence remains in metrics. Resume checks actual files/revision and
   smoke behavior instead of trusting a stale summary.
5. **Rollback overclaim.** Git restores code, not sent mail, database mutations,
   or filesystem effects. Recovery must account for those explicitly.
6. **Underspecified experiment environment.** Metrics now record observable
   resource limits, context/compaction, permissions, cache state and local-model
   runtime/quantization where relevant, along with missingness.
7. **Architecture without rationale.** Added brief context and tradeoffs. The
   source intentionally left some architecture choices open; our HTTP Basic,
   durable async mail, startup config and idempotency decisions define a new
   experimental condition. They are not merely formatting changes.

### Still unresolved before serious comparative trials

| Gap | Why it matters | Recommended owner/action |
|---|---|---|
| Exact dependency, tool and scanner-rule baseline | Different versions or advisory snapshots change difficulty and pass/fail. | Researcher preflights a compatible baseline, then freezes it identically for conditions. Record advisory database date/state; do not present an old snapshot as current security assurance. |
| Independent acceptance oracle | The same system can misunderstand the requirement in both code and tests. | Researcher reviews fixed cases and later supplies a stable black-box checker or spot-audit. The system can still execute it and write all metrics. |
| Consent and production policies | Conditional consent is not an approved legal notice; retention and real deployment requirements are unspecified. | Keep a synthetic fixture for the experiment. Resolve deployment policy separately; no invented legal assurances. |
| Selection cardinalities and duplicate-email policy | Allowed design freedom can produce different apps. | Freeze these only if they are meant to be invariant in the experiment; otherwise document agent choices and do not secretly grade one choice as required. |
| Scanner adjudication | Findings, actual vulnerabilities and accepted risks are different. | Predefine evidence for false-positive triage and who can authorize waivers. No agent-controlled broad suppression or silent waiver. |
| External rollout/backup recovery standard | Container persistence is narrower than disaster recovery or safe production rollback. | Define restore objective and operational checks if production readiness is studied; do not add them implicitly to this task's grading. |
| User-interface quality | Functional browser tests do not establish good appearance or accessibility. | If these matter, add fixed viewport, keyboard/label/error-state criteria and an explicit rubric before trials. Do not score unannounced aesthetics afterward. |
| Abort coverage | Killed systems may never write their final report. | Runner records launch/end/termination and retains partial outputs, even when all detailed metrics are system-written. |
| Stable machine-readable metrics contract | Prose plus a JSON skeleton permits field drift across systems. | Pilot the current record, then provide a researcher-owned schema/validator outside agent-writable metrics for study automation. Not supplied or independently validated in this draft. |

Not every gap requires a new Markdown file. Product examples fit PRODUCT;
version policy fits TECH_STACK; rationale fits ARCHITECTURE; restart and slices
fit WORKFLOW; evidence semantics fit METRICS/DoD. Generic framework tutorials
and unused skills do not belong in the core input package.

## Strict slices without brittle choreography

M0 is an enabling foundation, M1 external acceptance, M2 student acceptance,
M3 configuration/consent, M4 notifications, M5 organizer export, and M6 final
release verification. WORKFLOW is their authoritative contract. M0 precedes
M1; later work follows dependencies rather than a mandatory total order.

For example, M4 can contain a slice for durable pending-mail delivery and a
second for outage/recovery behavior. Each has a concrete check. Neither a new
class nor a thousand-line patch is inherently the right slice boundary.
The whole milestone passes only when its complete contract is evidenced.
Focused checks keep the loop short; final full verification catches interactions.

This design intentionally standardizes outcomes but leaves decomposition room.
It studies orchestration within a supplied development protocol. If planning
ability is the research question, prescribing milestones changes that question;
test agent-chosen planning as a separate condition.

## Research design I recommend

First run feasibility pilots, excluded from the main comparison. Check that the
stack can build, scanners can run, required services are available, measurements
are extractable and the criteria are attainable. Use the pilot to fix the
protocol, not to select a flattering run.

Then compare the revised single-agent configuration against the multi-agent
configuration with the same task family, acceptance criteria, total resource
budget policy and tool/environment access. Record model snapshot/settings and
all prompts. If models or tool privileges differ, describe a bundled-system
comparison rather than attributing the result only to agent count.

Use fresh workspaces, no prior implementations or memories, repeated matched
tasks and counterbalanced run order. A single conference app estimates behavior
on that task, not ecommerce or all software projects. Several app variants
and repetitions are needed to separate task sensitivity from run variability.
Determine main-study sample size from pilot variance and the effect of interest;
three repeats are a smoke pilot, not a reliable ranking.

Primary reporting should pair completion/adherence with cost and elapsed-time
distributions. Include failed, blocked and timed-out attempts. Time-to-completion
among successes alone can flatter a configuration that fails difficult tasks;
show completion probability and budget usage for all attempts alongside it.
Avoid one composite score until weights have a defensible research purpose.

To test the input design itself, change one information treatment at a time:
same requirements, concise versus expanded wording; same content, all-loaded
versus relevant-section loading; same outcomes, fixed versus autonomous slicing.
Do not remove essential requirements and call the resulting easier task a
token-efficiency improvement. Keep reporting burden constant. Measure whole-run
cost, including rework and instrumentation, not only prompt length.

The system may write every metric as requested. Have scripts capture commands,
times, exit codes and machine-readable outputs; let prose explain exceptions.
An agent's assertion that a command passed is not equivalent to preserved output.
A short post-run audit provides more credibility than increasingly elaborate
self-assessment. No private-thinking or silence-based 'rumination' score is justified.

## Package status

v3 retains exactly four root directories and seven input Markdown files. No
implementation, source scaffold, or previous run statistics are included.
The stricter workflow adds necessary protocol text; this is not a claim of a
smaller prompt or measured token savings. Start with this as an auditable
hypothesis, then remove or revise rules when controlled observations justify it.
