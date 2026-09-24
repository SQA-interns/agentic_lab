# Experimental Run Metrics Specification

**Project:** Agentic Software Engineering Benchmark  
**Experiment Setup:** Single Agent — Classical SDD  
**Target:** Standardized, Deterministic, and Reproducible Run Metrics  

---

## 1. Overview & Objectives

In prior experimental runs, a significant proportion of recorded metrics were either `null` or uninformative `0` values. This document defines a refined, high-value metric set that eliminates ambiguity and ensures consistency across all compared models and agent setups.

### Guiding Principles
1. **Objectivity:** Every metric has an unambiguous measurement procedure.
2. **Deterministic Tools Over Agent Self-Grading:** Code quality, architecture, and security metrics are produced by automated tools/scripts, not subjective agent self-evaluation.
3. **No Hallucinated Data:** If an external metric (e.g. token consumption) is not exposed by the execution harness, it is recorded as `null` with an explicit reason.
4. **Reproducibility:** Two independent observers evaluating the same Git commit history and test output will arrive at the exact same numbers.

---

## 2. Master Metric Catalog

| # | Category | Metric Name | JSON Field Key | Type | Measurement Method / Tool | Output Format |
|---|---|---|---|---|---|---|
| 1 | **Efficiency** | Total Wall-Clock Time | `total_time_seconds` | `DIRECT` | `(phase_6_merge_ts - experiment_start_ts)` | Integer (seconds) |
| 2 | **Efficiency** | Time to First Working Version | `time_to_first_working_version_seconds` | `DIRECT` | `(first_happy_path_ts - experiment_start_ts)` | Integer (seconds) |
| 3 | **Efficiency** | Time Spent in Fix Loops | `time_spent_in_fix_loops_seconds` | `DIRECT` | Sum of durations of all fix cycles between verification and final merge | Integer (seconds) |
| 4 | **Efficiency** | Tokens Consumed | `tokens_consumed` | `CONDITIONAL` | Platform harness billing / token API report (`null` if unexposed) | Integer or `null` |
| 5 | **Human Effort** | Human Interventions | `human_interventions` | `DIRECT` | Count of user chat messages sent after initial prompt | Integer (count) |
| 6 | **Human Effort** | Clarifying Questions | `clarifying_questions` | `DIRECT` | Count of agent inquiries asking user for requirement decisions | Integer (count) |
| 7 | **Correctness** | Acceptance Criteria Passed (First Eval) | `acceptance_criteria_passed_first_eval` | `PROTOCOL` | Verified passing criteria count on initial Phase 5 run | Integer (count / 24) |
| 8 | **Correctness** | Acceptance Criteria Passed (Final) | `acceptance_criteria_passed_finally` | `PROTOCOL` | Final verified passing criteria count at merge | Integer (count / 24) |
| 9 | **Correctness** | First Complete Test Run Pass Rate | `first_complete_test_run_pass_rate` | `DIRECT` | `passed_tests / total_tests` from first test suite run | Float `[0.0 - 1.0]` |
| 10 | **Correctness** | Final Test Pass Rate | `final_test_pass_rate` | `DIRECT` | `passed_tests / total_tests` at final verification | Float `[0.0 - 1.0]` |
| 11 | **Verification** | Verifier Findings by Severity | `verifier_findings` | `PROTOCOL` | Count of actionable defects in verification report by severity | Object `{critical, major, minor}` |
| 12 | **Verification** | Fix Loop Count | `fix_loop_count` | `DIRECT` | Number of Verification → Fix → Verification cycles executed | Integer (count) |
| 13 | **Security** | Security Findings by Severity | `security_findings` | `AUTOMATE` | `npm audit --json` parsed by severity level | Object `{critical, high, moderate, low}` |
| 14 | **Code Quality** | Source Lines of Code (LOC) | `loc` | `AUTOMATE` | Line count of production code (`src/`, `public/`, `config/`) | Integer (lines) |
| 15 | **Code Quality** | Cyclomatic Complexity | `cyclomatic_complexity` | `AUTOMATE` | Maximum / Average complexity via ESLint / complexity-report | Object `{average, max}` |
| 16 | **Code Quality** | Code Duplication | `code_duplication` | `AUTOMATE` | Duplicated lines / tokens percentage via `jscpd` | Float (percentage `%`) |
| 17 | **Code Quality** | Test Coverage (Line) | `test_coverage_percent` | `AUTOMATE` | Test runner line coverage via `c8` / `--experimental-test-coverage` | Float (percentage `%`) |
| 18 | **Code Quality** | Lint Errors and Warnings | `lint_errors`, `lint_warnings` | `AUTOMATE` | `eslint` rule violations | Object `{errors, warnings}` |
| 19 | **Git / Process** | Commit Count | `commit_count` | `DIRECT` | `git rev-list --count HEAD ^<starting_sha>` | Integer (count) |
| 20 | **Rework** | LOC Changed After First Verification | `loc_changed_after_first_verification` | `DIRECT` | `git diff --stat <commit_of_first_verification> HEAD -- src/` | Integer (lines changed) |
| 21 | **Requirements**| Missing Requirements | `missing_requirements` | `PROTOCOL` | Requirements in `USER_STORIES.md` not covered by tests/code | Integer (count) |
| 22 | **Requirements**| Unrequested Functionality | `unrequested_functionality` | `PROTOCOL` | Endpoints, models, or libraries added outside specifications | Integer (count) |
| 23 | **Architecture** | Dependency Cycles | `dependency_cycles` | `AUTOMATE` | Count of circular module dependencies via `madge --circular` | Integer (count) |
| 24 | **Architecture** | Layer / Architecture Violations | `layer_violations` | `AUTOMATE` | Dependency boundary check (e.g. `db` importing `routes`) | Integer (count) |
| 25 | **Architecture** | External Dependency Count | `external_dependency_count` | `AUTOMATE` | Direct production dependencies in `package.json` | Integer (count) |

---

## 3. Operational Measurement Protocols

### 3.1 Efficiency & Time Metrics
* **Total Time (`total_time_seconds`):**
  $$\text{Total Time} = T_{\text{phase\_6\_merge}} - T_{\text{experiment\_start}}$$
  Measured via system ISO timestamps recorded in Git commits and run log.
* **Time to First Working Version (`time_to_first_working_version_seconds`):**
  $$\text{Time to Working} = T_{\text{first\_successful\_happy\_path}} - T_{\text{experiment\_start}}$$
  The timestamp when a registration payload is first persisted to the database and confirmed.
* **Time Spent in Fix Loops (`time_spent_in_fix_loops_seconds`):**
  Calculated as the cumulative time elapsed between any verification failure and the completion of its re-verification. If no verification findings occur, this metric is exactly `0`.

---

### 3.2 Correctness & Test Metrics
* **First Complete Test Run Pass Rate (`first_complete_test_run_pass_rate`):**
  Recorded immediately upon the completion of Phase 4 (Tests).
  $$\text{Pass Rate} = \frac{\text{Passed Tests}}{\text{Total Tests}}$$
  *Rule:* Must be captured before fixing any discovered defects.
* **Final Test Pass Rate (`final_test_pass_rate`):**
  Recorded at the conclusion of Phase 5 (Verification). Must equal `1.0` (100%) for merge readiness.

---

### 3.3 Automated Code Quality, Security & Architecture Commands

To remove subjectivity, these metrics are produced by deterministic CLI commands:

#### A. Source Lines of Code (LOC)
```bash
# Count lines in production code only (excluding node_modules, lockfiles, tests, markdown)
git ls-files -- "src/**" "public/**/*.{html,css,js}" "config/**" | xargs wc -l
```

#### B. Test Coverage
```bash
# Using Node native coverage runner or c8
npx c8 --all --src=src node --test tests/**/*.test.js
```
*Extracted Value:* `Lines %` from coverage summary.

#### C. Code Duplication
```bash
npx jscpd src/ --threshold 0 --reporters console
```
*Extracted Value:* Percentage of duplicated code tokens/lines.

#### D. Cyclomatic Complexity & Linting
```bash
npx eslint src/ --rule "complexity: ['error', 10]" --format json
```
*Extracted Value:* Count of errors, count of warnings, and max complexity per function.

#### E. Circular Dependency Cycles
```bash
npx madge --circular --warning src/
```
*Extracted Value:* Number of circular dependency loops found (target: `0`).

#### F. Security Vulnerabilities
```bash
npm audit --json
```
*Extracted Value:* Parsed count under `metadata.vulnerabilities`:
`{ "critical": 0, "high": 1, "moderate": 2, "low": 0 }`.

#### G. External Dependency Count
```bash
node -e "const p = require('./package.json'); console.log(Object.keys(p.dependencies || {}).length);"
```

#### H. Rework LOC After First Verification
```bash
# Compare the commit at the start of Phase 5 with HEAD in src/
git diff --stat <COMMIT_PHASE_5_START> HEAD -- src/
```
*Extracted Value:* Total insertions + deletions in production source code.

---

## 4. Standardized JSON Schema for `experiment/run-log.json`

For future benchmark runs, use this standardized schema:

```json
{
  "run_id": "gemini_3.8_flash_medium_single_agent_classic_sdd_run_01",
  "experiment_name": "Single Agent — Classical SDD",
  "agent_model": "gemini-3.8-flash-medium",
  "timestamps": {
    "experiment_start": "2026-09-21T15:56:00+02:00",
    "first_working_version": "2026-09-21T16:13:35+02:00",
    "experiment_end": "2026-09-21T16:36:00+02:00"
  },
  "metrics": {
    "efficiency": {
      "total_time_seconds": 2400,
      "time_to_first_working_version_seconds": 1055,
      "time_spent_in_fix_loops_seconds": 0,
      "tokens_consumed": null,
      "tokens_consumed_note": "Unexposed by IDE harness"
    },
    "human_effort": {
      "human_interventions": 0,
      "clarifying_questions": 0
    },
    "correctness": {
      "ac_passed_first_eval": 24,
      "ac_passed_final": 24,
      "test_first_pass_rate": 1.0,
      "final_test_pass_rate": 1.0,
      "total_tests_count": 30
    },
    "verification": {
      "verifier_findings": {
        "critical": 0,
        "major": 0,
        "minor": 0
      },
      "fix_loop_count": 0
    },
    "security": {
      "security_findings": {
        "critical": 0,
        "high": 1,
        "moderate": 2,
        "low": 0
      }
    },
    "code_quality": {
      "loc": 1134,
      "cyclomatic_complexity": {
        "average": 2.4,
        "max": 8
      },
      "code_duplication_percent": 0.0,
      "test_coverage_percent": 88.5,
      "lint_errors": 0,
      "lint_warnings": 0
    },
    "git_process": {
      "commit_count": 6
    },
    "rework": {
      "loc_changed_after_first_verification": 0
    },
    "requirements": {
      "missing_requirements": 0,
      "unrequested_functionality": 0
    },
    "architecture": {
      "dependency_cycles": 0,
      "layer_violations": 0,
      "external_dependency_count": 3
    }
  }
}
```

---

## 5. Implementation Recommendation for Workspace Setup

To automate this seamlessly in subsequent runs, add a standardized runner script in `package.json`:

```json
{
  "scripts": {
    "test": "node --test tests/**/*.test.js",
    "test:coverage": "c8 node --test tests/**/*.test.js",
    "lint": "eslint src/",
    "audit:security": "npm audit --json",
    "audit:cycles": "madge --circular src/",
    "audit:duplication": "jscpd src/",
    "audit:all": "npm run test:coverage && npm run lint && npm run audit:cycles && npm run audit:duplication"
  }
}
```

By packaging the audit tools directly into the project scripts, every development agent (regardless of model family) can execute `npm run audit:all` during Phase 5 (Verification) and log deterministic, comparable values without manual guesswork.
