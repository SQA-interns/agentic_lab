#!/usr/bin/env python3
"""Summarizes scanner outputs with native severities/counts (verification tooling).

Writes 03_Metrics/evidence/security/summary.json. Exit 1 if any Critical/High finding remains
or a scanner output is missing (missing output = blocked, never zero findings).
"""
from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

IMPL = Path(__file__).resolve().parents[1]
SEC = IMPL.parent / "03_Metrics" / "evidence" / "security"


def dependency_check() -> dict:
    path = IMPL / "backend" / "target" / "dependency-check-report.json"
    if not path.exists():
        return {"status": "blocked", "reason": f"missing {path.name}"}
    data = json.loads(path.read_text())
    severities: Counter[str] = Counter()
    findings = []
    for dep in data.get("dependencies", []):
        for vuln in dep.get("vulnerabilities", []) or []:
            sev = (vuln.get("severity") or "UNKNOWN").upper()
            severities[sev] += 1
            findings.append({"dependency": dep.get("fileName"), "id": vuln.get("name"),
                             "severity": sev})
    info = data.get("scanInfo", {})
    return {
        "status": "ok",
        "engineVersion": info.get("engineVersion"),
        "dataSources": info.get("dataSource"),
        "dependenciesScanned": len(data.get("dependencies", [])),
        "unit": "vulnerability x dependency",
        "bySeverity": dict(severities),
        "findings": findings,
        "criticalOrHigh": severities.get("CRITICAL", 0) + severities.get("HIGH", 0),
    }


def npm_audit() -> dict:
    path = SEC / "npm-audit.json"
    if not path.exists():
        return {"status": "blocked", "reason": "missing npm-audit.json"}
    data = json.loads(path.read_text())
    vulns = data.get("metadata", {}).get("vulnerabilities", {})
    return {"status": "ok", "unit": "vulnerable package", "bySeverity": vulns,
            "dependencies": data.get("metadata", {}).get("dependencies"),
            "criticalOrHigh": vulns.get("critical", 0) + vulns.get("high", 0)}


def semgrep() -> dict:
    path = SEC / "semgrep.json"
    if not path.exists():
        return {"status": "blocked", "reason": "missing semgrep.json"}
    data = json.loads(path.read_text())
    severities = Counter(r.get("extra", {}).get("severity", "UNKNOWN") for r in data["results"])
    return {
        "status": "ok" if not data.get("errors") else "ok-with-errors",
        "version": data.get("version"),
        "unit": "finding",
        "bySeverity": dict(severities),
        "findings": [{"rule": r["check_id"], "path": r["path"], "line": r["start"]["line"],
                      "severity": r["extra"].get("severity"),
                      "impact": r["extra"].get("metadata", {}).get("impact")}
                     for r in data["results"]],
        "errors": [e.get("message", "")[:200] for e in data.get("errors", [])],
        "rulesRun": len(data.get("paths", {}).get("scanned", [])) and None,
        # Semgrep native severities: ERROR (high), WARNING (medium), INFO (low)
        "criticalOrHigh": severities.get("ERROR", 0),
    }


def main() -> int:
    summary = {"dependencyCheck": dependency_check(), "npmAudit": npm_audit(),
               "semgrep": semgrep()}
    SEC.mkdir(parents=True, exist_ok=True)
    (SEC / "summary.json").write_text(json.dumps(summary, indent=2))
    failed = False
    for name, result in summary.items():
        status = result.get("status")
        high = result.get("criticalOrHigh")
        print(f"{name}: status={status} criticalOrHigh={high} bySeverity={result.get('bySeverity')}")
        if status == "blocked" or (high or 0) > 0:
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
