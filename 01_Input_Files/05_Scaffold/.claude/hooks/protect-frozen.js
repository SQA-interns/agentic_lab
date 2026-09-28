#!/usr/bin/env node
// PreToolUse hook (Claude Code): blocks writes to the immutable
// experiment inputs, and to frozen acceptance tests once they exist.
// See CONSTITUTION.md §1 and §2 for the rules this enforces.
//
// This is a last-resort, deterministic backstop. The real rule is in
// CONSTITUTION.md; this just makes it non-optional for harnesses that
// support Claude Code-style PreToolUse hooks. A different harness
// (Gemini CLI, Codex, etc.) running this same input package needs an
// equivalent of its own — see AGENTS.md.
//
// Claude Code passes the tool call as JSON on stdin. Exit code 2
// blocks the tool call and returns stderr to the agent as the reason.

const fs = require("fs");
const path = require("path");

let raw = "";
process.stdin.on("data", (chunk) => {
  raw += chunk;
});

process.stdin.on("end", () => {
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    // Can't parse the hook payload — fail open rather than blocking
    // everything on a hook bug. This hook is a backstop, not the only
    // control (CONSTITUTION.md §1 still applies regardless).
    process.exit(0);
  }

  const filePath =
    (input && input.tool_input && (input.tool_input.file_path || input.tool_input.path)) || "";
  if (!filePath) {
    process.exit(0);
  }

  const normalized = filePath.split(path.sep).join("/");

  // Rule 1 (CONSTITUTION.md §1): 01_Input_Files/ is read-only for the
  // whole run.
  if (/(^|\/)01_Input_Files\//.test(normalized)) {
    process.stderr.write(
      `Blocked: "${filePath}" is under 01_Input_Files/, which is immutable for the whole run ` +
        `(CONSTITUTION.md §1). If this file is genuinely wrong, record it in docs/decisions-log.md ` +
        `and escalate per CONSTITUTION.md §3 — do not edit it directly.\n`,
    );
    process.exit(2);
  }

  // Rule 2 (CONSTITUTION.md §2): frozen acceptance tests, once the
  // manifest exists.
  const looksLikeAcceptanceTest =
    /(^|\/)acceptance\//.test(normalized) ||
    /(^|\/)e2e\//.test(normalized) ||
    /\.acceptance\.(test|spec)\./.test(normalized);

  if (looksLikeAcceptanceTest) {
    // The manifest's own location is fixed by write-acceptance-tests/SKILL.md.
    // We don't know IMPLEMENTATION_ROOT's exact name from inside the hook,
    // so check the conventional path plus a same-repo relative fallback.
    const candidates = [
      "02_Implementation/docs/acceptance/MANIFEST.sha256",
      path.join(process.cwd(), "02_Implementation/docs/acceptance/MANIFEST.sha256"),
    ];
    const frozen = candidates.some((p) => {
      try {
        return fs.existsSync(p);
      } catch {
        return false;
      }
    });

    if (frozen) {
      process.stderr.write(
        `Blocked: "${filePath}" matches the frozen acceptance-test convention, and the ` +
          `acceptance-test manifest already exists — acceptance tests are frozen (CONSTITUTION.md §2). ` +
          `File a test-defect request in docs/decisions-log.md and escalate; only a human resolving ` +
          `that escalation may remove or regenerate the manifest to allow this edit.\n`,
      );
      process.exit(2);
    }
  }

  process.exit(0);
});
