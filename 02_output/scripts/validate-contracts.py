#!/usr/bin/env python3
"""Validates every contract in docs/02_contracts with a parser.

Usage: validate-contracts.py [oas-3.1-meta-schema.json]  (default: the copy next to this script)
- *.openapi.yaml: against the official OpenAPI 3.1 JSON Schema; every component schema is a
  valid JSON Schema 2020-12 and every local $ref resolves.
- *.schema.json: a valid JSON Schema 2020-12; each matching *.example*.json validates against it.
- *.sql: checked separately by applying it to the pinned PostgreSQL image (see verify.sh).
Prints one line per contract and exits non-zero on any failure.
"""
import json
import pathlib
import sys

import jsonschema
import yaml

CONTRACTS = pathlib.Path(__file__).resolve().parent.parent / "docs" / "02_contracts"


def local_refs(node, found):
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/"):
            found.add(ref)
        for value in node.values():
            local_refs(value, found)
    elif isinstance(node, list):
        for value in node:
            local_refs(value, found)
    return found


def resolve(doc, ref):
    node = doc
    for part in ref[2:].split("/"):
        node = node[part]
    return node


def schema_objects(node, found):
    """Collects every Schema Object (values of "schema" keys and of components.schemas).

    The OpenAPI meta-schema reaches Schema Objects through $dynamicRef, which the installed
    jsonschema release does not follow, so each one is checked against JSON Schema 2020-12 here.
    """
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "schema" and isinstance(value, dict):
                found.append(value)
            elif key == "schemas" and isinstance(value, dict):
                found.extend(v for v in value.values() if isinstance(v, dict))
            else:
                schema_objects(value, found)
    elif isinstance(node, list):
        for value in node:
            schema_objects(value, found)
    return found


def check_openapi(path, meta):
    doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator(meta).validate(doc)
    schemas = schema_objects(doc, [])
    for schema in schemas:
        jsonschema.Draft202012Validator.check_schema(schema)
    refs = local_refs(doc, set())
    for ref in refs:
        resolve(doc, ref)
    return f"{len(doc['paths'])} paths, {len(schemas)} schema objects valid, {len(refs)} refs resolved"


def check_schema(path):
    schema = json.loads(path.read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator.check_schema(schema)
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    stem = path.name.removesuffix(".schema.json")
    examples = sorted(CONTRACTS.glob(f"{stem}.example*.json"))
    for example in examples:
        validator.validate(json.loads(example.read_text(encoding="utf-8")))
    return f"schema ok, {len(examples)} example(s) valid"


def main():
    default = pathlib.Path(__file__).resolve().parent / "oas-3.1-schema-2022-10-07.json"
    meta = json.loads(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else default).read_text(encoding="utf-8"))
    failed = 0
    for path in sorted(CONTRACTS.iterdir()):
        try:
            if path.name.endswith(".openapi.yaml"):
                result = check_openapi(path, meta)
            elif path.name.endswith(".schema.json"):
                result = check_schema(path)
            else:
                continue
            print(f"PASS {path.name}: {result}")
        except Exception as error:  # report every contract, then fail
            failed += 1
            print(f"FAIL {path.name}: {type(error).__name__}: {str(error).splitlines()[0]}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
