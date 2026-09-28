#!/usr/bin/env python3
"""INSTRUMENTATION (not application code).

Research-metrics helper for this run. Appends events to 03_Metrics/events.jsonl
and times commands, storing stdout/stderr under 03_Metrics/evidence/.

Usage:
  metrics.py event KIND "description" [--related ID ...] [--evidence PATH ...] [--extra JSON]
  metrics.py run CHECK_ID [--cwd DIR] -- COMMAND...
  metrics.py now
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
METRICS = ROOT / "03_Metrics"
EVENTS = METRICS / "events.jsonl"
EVIDENCE = METRICS / "evidence"
CHECKS = EVIDENCE / "checks.jsonl"


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def append(path: Path, record: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False) + "\n")


def cmd_event(args: argparse.Namespace) -> None:
    record = {
        "id": f"ev-{uuid.uuid4().hex[:10]}",
        "timestampUtc": now(),
        "kind": args.kind,
        "actor": "agent-1",
        "relatedIds": args.related or [],
        "description": args.description,
        "evidencePaths": args.evidence or [],
    }
    if args.extra:
        record.update(json.loads(args.extra))
    append(EVENTS, record)
    print(record["id"])


def cmd_run(args: argparse.Namespace) -> int:
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out_path = EVIDENCE / "commands" / f"{stamp}_{args.check_id}.log"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    start = now()
    t0 = time.monotonic()
    proc = subprocess.run(
        command, cwd=args.cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
    )
    elapsed = round(time.monotonic() - t0, 3)
    end = now()
    out_path.write_text(
        f"$ (cwd={args.cwd or '.'}) {' '.join(command)}\n# start={start} end={end} "
        f"exit={proc.returncode} elapsed_s={elapsed}\n\n{proc.stdout}",
        encoding="utf-8",
    )
    append(
        CHECKS,
        {
            "id": args.check_id,
            "command": " ".join(command),
            "cwd": args.cwd,
            "start": start,
            "end": end,
            "elapsedSeconds": elapsed,
            "exitCode": proc.returncode,
            "evidence": str(out_path.relative_to(ROOT)),
        },
    )
    sys.stdout.write(proc.stdout[-6000:])
    print(f"\n[metrics] {args.check_id} exit={proc.returncode} {elapsed}s -> {out_path.relative_to(ROOT)}")
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    ev = sub.add_parser("event")
    ev.add_argument("kind")
    ev.add_argument("description")
    ev.add_argument("--related", nargs="*")
    ev.add_argument("--evidence", nargs="*")
    ev.add_argument("--extra")
    rn = sub.add_parser("run")
    rn.add_argument("check_id")
    rn.add_argument("--cwd")
    sub.add_parser("now")
    argv = sys.argv[1:]
    command: list[str] = []
    if "--" in argv:
        split = argv.index("--")
        argv, command = argv[:split], argv[split + 1 :]
    args = parser.parse_args(argv)
    args.command = command
    if args.cmd == "event":
        cmd_event(args)
    elif args.cmd == "run":
        return cmd_run(args)
    else:
        print(now())
    return 0


if __name__ == "__main__":
    sys.exit(main())
