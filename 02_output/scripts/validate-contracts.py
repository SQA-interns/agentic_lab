#!/usr/bin/env python3
"""Validates every JSON Schema contract against its meta-schema and its own examples."""
import glob, json, os, sys
from jsonschema import Draft202012Validator

base = sys.argv[1]
bad = 0
for path in sorted(glob.glob(os.path.join(base, "*.schema.json"))):
    schema = json.load(open(path, encoding="utf-8"))
    try:
        Draft202012Validator.check_schema(schema)
        v = Draft202012Validator(schema)
        for i, ex in enumerate(schema.get("examples", [])):
            errs = list(v.iter_errors(ex))
            for e in errs:
                print(f"{os.path.basename(path)} example {i}: {e.message}")
            bad += len(errs)
        print(f"ok {os.path.basename(path)} ({len(schema.get('examples', []))} examples)")
    except Exception as e:  # schema itself invalid
        print(f"INVALID {os.path.basename(path)}: {e}")
        bad += 1
print(f"schemas invalid={bad}")
sys.exit(1 if bad else 0)
