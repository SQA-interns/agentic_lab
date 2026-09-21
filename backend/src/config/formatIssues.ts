/**
 * Shared formatting for configuration validation failures.
 *
 * Both the environment schema and the conference-options schema report their problems
 * the same way — one indented line per issue, naming the path and what is wrong — so an
 * operator reading a failed startup sees a consistent message whichever file is at
 * fault.
 */
import type { $ZodIssue } from 'zod/v4/core';

export function formatIssues(issues: readonly $ZodIssue[]): string {
  return issues
    .map((issue) => `  ${issue.path.join('.') || '(root)'}: ${issue.message}`)
    .join('\n');
}
