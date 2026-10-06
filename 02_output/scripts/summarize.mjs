// Prints the key numbers of one tool report on a single line, for verify.sh.
// Usage: node summarize.mjs <kind> <file...>
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const [kind, ...files] = process.argv.slice(2);
const read = (f) => readFileSync(f, "utf8");
const json = (f) => JSON.parse(read(f));
const pct = (a, b) => (b === 0 ? "n/a" : `${((100 * a) / b).toFixed(1)}%`);
const count = (text, re) => (text.match(re) || []).length;

function severityOfCvss(score) {
  if (score >= 9) return "critical";
  if (score >= 7) return "high";
  if (score >= 4) return "medium";
  return "low";
}

const summarizers = {
  // Surefire XML reports directory
  surefire([dir]) {
    if (!existsSync(dir)) return "tests=0 (no reports)";
    let tests = 0, failed = 0, skipped = 0;
    for (const f of readdirSync(dir).filter((n) => /^TEST-.*\.xml$/.test(n))) {
      const head = read(join(dir, f)).match(/<testsuite [^>]*>/);
      if (!head) continue;
      const attr = (n) => Number((head[0].match(new RegExp(`\\b${n}="(\\d+)"`)) || [0, 0])[1]);
      tests += attr("tests");
      failed += attr("failures") + attr("errors");
      skipped += attr("skipped");
    }
    return `tests=${tests} passed=${tests - failed - skipped} failed=${failed} skipped=${skipped}`;
  },
  jacoco([csv]) {
    if (!existsSync(csv)) return "coverage=n/a";
    let lm = 0, lc = 0, bm = 0, bc = 0;
    for (const row of read(csv).trim().split(/\r?\n/).slice(1)) {
      const c = row.split(",");
      bm += +c[5]; bc += +c[6]; lm += +c[7]; lc += +c[8];
    }
    return `line=${pct(lc, lc + lm)} branch=${pct(bc, bc + bm)}`;
  },
  pit([xml]) {
    if (!existsSync(xml)) return "mutants=0 (no report)";
    const t = read(xml);
    const total = count(t, /<mutation /g);
    const killed = count(t, /<mutation [^>]*detected='true'/g);
    const survived = count(t, /status='SURVIVED'/g);
    const nocov = count(t, /status='NO_COVERAGE'/g);
    return `mutants=${total} killed=${killed} survived=${survived} no_coverage=${nocov} score=${pct(killed, total)}`;
  },
  pmd([xml]) {
    return existsSync(xml) ? `violations=${count(read(xml), /<violation /g)}` : "violations=n/a";
  },
  spotbugs([xml]) {
    if (!existsSync(xml)) return "bugs=n/a";
    const t = read(xml);
    const ranks = [...t.matchAll(/<BugInstance [^>]*rank=['"](\d+)['"]/g)].map((m) => +m[1]);
    const high = ranks.filter((r) => r <= 4).length;
    const medium = ranks.filter((r) => r >= 5 && r <= 9).length;
    return `bugs=${ranks.length} high=${high} medium=${medium} low=${ranks.length - high - medium}`;
  },
  cpd([xml]) {
    if (!existsSync(xml)) return "duplications=n/a";
    const t = read(xml);
    const lines = [...t.matchAll(/<duplication lines="(\d+)"/g)].reduce((s, m) => s + +m[1], 0);
    return `duplications=${count(t, /<duplication /g)} duplicated_lines=${lines}`;
  },
  depcheck([file]) {
    if (!existsSync(file)) return "report=missing";
    const sev = { critical: 0, high: 0, medium: 0, low: 0 };
    for (const d of json(file).dependencies || []) {
      for (const v of d.vulnerabilities || []) {
        const score = v.cvssv3?.baseScore ?? v.cvssv4?.baseScore ?? v.cvssv2?.score;
        if (score !== undefined) sev[severityOfCvss(score)]++;
        else sev[(v.severity || "low").toLowerCase().replace("moderate", "medium")] =
          (sev[(v.severity || "low").toLowerCase().replace("moderate", "medium")] || 0) + 1;
      }
    }
    return Object.entries(sev).map(([k, v]) => `${k}=${v}`).join(" ");
  },
  audit([file]) {
    const v = json(file).metadata?.vulnerabilities || {};
    return `critical=${v.critical ?? 0} high=${v.high ?? 0} medium=${v.moderate ?? 0} low=${(v.low ?? 0) + (v.info ?? 0)}`;
  },
  vitest([file, coverage]) {
    let out = "tests=n/a";
    if (existsSync(file)) {
      const r = json(file);
      out = `tests=${r.numTotalTests} passed=${r.numPassedTests} failed=${r.numFailedTests} skipped=${r.numPendingTests + r.numTodoTests}`;
    }
    if (coverage && existsSync(coverage)) {
      const t = json(coverage).total;
      out += ` line=${t.lines.pct}% branch=${t.branches.pct}%`;
    }
    return out;
  },
  stryker([file]) {
    if (!existsSync(file)) return "mutants=0 (no report)";
    const all = Object.values(json(file).files).flatMap((f) => f.mutants);
    const by = (s) => all.filter((m) => m.status === s).length;
    const detected = by("Killed") + by("Timeout");
    const undetected = by("Survived") + by("NoCoverage");
    return `mutants=${all.length} killed=${detected} survived=${by("Survived")} no_coverage=${by("NoCoverage")} score=${pct(detected, detected + undetected)}`;
  },
  jscpd([file]) {
    if (!existsSync(file)) return "duplication=n/a";
    const t = json(file).statistics.total;
    return `clones=${t.clones} duplicated_lines=${t.duplicatedLines} duplication=${t.percentage}%`;
  },
  semgrep([file]) {
    const r = json(file);
    const sev = { ERROR: 0, WARNING: 0, INFO: 0 };
    for (const x of r.results) sev[x.extra.severity] = (sev[x.extra.severity] || 0) + 1;
    return `high=${sev.ERROR} medium=${sev.WARNING} low=${sev.INFO} errors=${r.errors.length}`;
  },
  gitleaks(paths) {
    return paths.map((f) => `${f.replace(/.*_gitleaks-(\w+)\.json$/, "$1")}=${existsSync(f) ? json(f).length : "n/a"}`).join(" ");
  },
  cloc(paths) {
    return paths
      .map((f) => {
        const name = f.replace(/.*_cloc-(\w+)\.json$/, "$1");
        return `${name}=${existsSync(f) ? (json(f).SUM?.code ?? 0) : "n/a"}`;
      })
      .join(" ");
  },
  playwright([file]) {
    if (!existsSync(file)) return "tests=n/a";
    const s = json(file).stats;
    return `tests=${s.expected + s.unexpected + s.flaky + s.skipped} passed=${s.expected} failed=${s.unexpected} flaky=${s.flaky} skipped=${s.skipped}`;
  },
  redocly([file]) {
    if (!existsSync(file)) return "contracts=0";
    const t = read(file);
    const e = t.match(/(\d+) errors?/i);
    const w = t.match(/(\d+) warnings?/i);
    return `errors=${e ? e[1] : 0} warnings=${w ? w[1] : 0}`;
  },
};

const fn = summarizers[kind];
if (!fn) {
  console.log(`unknown summary kind: ${kind}`);
  process.exit(0);
}
try {
  console.log(fn(files));
} catch (e) {
  console.log(`summary failed: ${e.message}`);
}
