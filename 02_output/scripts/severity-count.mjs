// Prints severity counts of a scanner report on one line, mapped to the severity scale of
// general/standards.md. Exit 1 when the report has a Critical or High result.
//   node severity-count.mjs <depcheck|npm-audit|semgrep> <report.json>
import { readFileSync } from 'node:fs';

const [kind, file] = process.argv.slice(2);
const counts = { critical: 0, high: 0, medium: 0, low: 0 };

function fromCvss(score) {
  if (score >= 9) return 'critical';
  if (score >= 7) return 'high';
  if (score >= 4) return 'medium';
  return 'low';
}

let report;
try {
  const text = readFileSync(file, 'utf8');
  report = JSON.parse(text.slice(text.indexOf('{')));
} catch (e) {
  console.log(`no readable report (${e.message})`);
  process.exit(1);
}

const ids = [];
if (kind === 'depcheck') {
  for (const dep of report.dependencies ?? []) {
    for (const v of dep.vulnerabilities ?? []) {
      const score = v.cvssv3?.baseScore ?? v.cvssv4?.baseScore ?? v.cvssv2?.score;
      const level = score !== undefined ? fromCvss(score) : String(v.severity).toLowerCase();
      counts[level in counts ? level : 'low'] += 1;
      if (level === 'critical' || level === 'high') ids.push(`${dep.fileName}:${v.name}`);
    }
  }
} else if (kind === 'npm-audit') {
  for (const [name, v] of Object.entries(report.vulnerabilities ?? {})) {
    const level = v.severity === 'moderate' ? 'medium' : v.severity === 'info' ? 'low' : v.severity;
    counts[level] += 1;
    if (level === 'critical' || level === 'high') ids.push(name);
  }
} else if (kind === 'semgrep') {
  // Rule-based ERROR/WARNING/INFO per standards.md; newer rules report the scale's names directly.
  const map = {
    ERROR: 'high',
    WARNING: 'medium',
    INFO: 'low',
    CRITICAL: 'critical',
    HIGH: 'high',
    MEDIUM: 'medium',
    LOW: 'low',
  };
  for (const r of report.results ?? []) {
    const level = map[r.extra?.severity] ?? 'low';
    counts[level] += 1;
    if (level === 'high') ids.push(`${r.path}:${r.start?.line} ${r.check_id}`);
  }
  if ((report.errors ?? []).length) console.log(`scan errors: ${report.errors.length};`);
} else {
  console.log(`unknown report kind: ${kind}`);
  process.exit(2);
}

const line = Object.entries(counts)
  .map(([k, n]) => `${k} ${n}`)
  .join(', ');
console.log(ids.length ? `${line}; ${ids.join(' ')}` : line);
process.exit(counts.critical + counts.high > 0 ? 1 : 0);
