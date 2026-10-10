#!/usr/bin/env python3
"""Validate the private Runner v1 contracts without network access or generated code."""

from __future__ import annotations

import json
import pathlib
import re
import sys
from collections.abc import Iterable

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACT_ROOT = ROOT / "contracts" / "runner" / "v1"

JSON_CONTRACTS = (
    "capability-bundle.schema.json",
    "public-capability-bundle.schema.json",
    "task-lease.schema.json",
    "task-result.schema.json",
    "observation.schema.json",
)

PUBLIC_CONTRACTS = (
    "public-capability-bundle.schema.json",
    "task-lease.schema.json",
    "task-result.schema.json",
    "observation.schema.json",
)

FORBIDDEN_PUBLIC_KEYS = {
    "handler",
    "handlerPath",
    "executable",
    "command",
    "arguments",
    "args",
    "environment",
    "environmentVariables",
    "secretRef",
    "credentialRef",
    "internalUrl",
    "internalEndpoint",
}

REQUIRED_OPENAPI_PATHS = (
    "/runner/v1/enrollments:exchange",
    "/runner/v1/runners/{runnerId}",
    "/runner/v1/runners/{runnerId}/heartbeat",
    "/runner/v1/runners/{runnerId}/tasks:claim",
    "/runner/v1/tasks/{taskId}/heartbeat",
    "/runner/v1/tasks/{taskId}/artifacts",
    "/runner/v1/tasks/{taskId}:complete",
    "/runner/v1/tasks/{taskId}:fail",
    "/runner/v1/observations",
)


def fail(message: str) -> None:
    raise AssertionError(message)


def walk(value: object, path: str = "$") -> Iterable[tuple[str, object]]:
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            yield from walk(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk(child, f"{path}[{index}]")


def load_json(name: str) -> object:
    path = CONTRACT_ROOT / name
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise AssertionError(f"missing Runner contract: {path.relative_to(ROOT)}") from exc
    except json.JSONDecodeError as exc:
        raise AssertionError(f"invalid JSON in {path.relative_to(ROOT)}: {exc}") from exc


def validate_json_schema(name: str, document: object) -> None:
    if not isinstance(document, dict):
        fail(f"{name} must contain a JSON object")
    if document.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        fail(f"{name} must use JSON Schema 2020-12")
    schema_id = document.get("$id")
    if not isinstance(schema_id, str) or not schema_id.startswith("https://"):
        fail(f"{name} requires an absolute HTTPS $id")
    if document.get("type") != "object":
        fail(f"{name} root type must be object")
    if document.get("additionalProperties") is not False:
        fail(f"{name} root must fail closed on unknown properties")

    for location, value in walk(document):
        if isinstance(value, dict) and "$ref" in value:
            reference = value["$ref"]
            if not isinstance(reference, str):
                fail(f"{name} has a non-string $ref at {location}")
            if reference.startswith("http://"):
                fail(f"{name} contains an insecure $ref at {location}")
            if reference.startswith("./"):
                target = (CONTRACT_ROOT / reference).resolve()
                if CONTRACT_ROOT.resolve() not in target.parents or not target.exists():
                    fail(f"{name} has an unresolved local $ref at {location}: {reference}")


def validate_public_boundary(name: str, document: object) -> None:
    for location, value in walk(document):
        if not isinstance(value, dict):
            continue
        properties = value.get("properties")
        if isinstance(properties, dict):
            leaked = FORBIDDEN_PUBLIC_KEYS.intersection(properties)
            if leaked:
                fail(f"{name} leaks private Runner fields at {location}: {sorted(leaked)}")

        # Public contract prose must not accidentally advertise local execution coordinates.
        for key in ("title", "description"):
            text = value.get(key)
            if not isinstance(text, str):
                continue
            normalized = text.lower()
            for marker in ("local executable", "handler path", "internal endpoint", "credential value"):
                if marker in normalized:
                    fail(f"{name} leaks private execution semantics in {location}.{key}")


def validate_openapi() -> None:
    path = CONTRACT_ROOT / "runner-control.openapi.yaml"
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise AssertionError(f"missing Runner OpenAPI contract: {path.relative_to(ROOT)}") from exc

    first_line = next((line.strip() for line in text.splitlines() if line.strip()), "")
    if not first_line.startswith("openapi: 3.1."):
        fail("Runner control API must use OpenAPI 3.1")
    if "http://" in text:
        fail("Runner control API must not contain insecure HTTP URLs")
    for required_path in REQUIRED_OPENAPI_PATHS:
        if required_path not in text:
            fail(f"Runner control API is missing path {required_path}")
    for forbidden in ("handlerPath", "credentialRef", "internalEndpoint", "shellCommand"):
        if re.search(rf"(?m)^\s*{re.escape(forbidden)}\s*:", text):
            fail(f"Runner control API leaks private field {forbidden}")
    if "mutualTLS" not in text and "mutualTls" not in text:
        fail("Runner control API must declare mutual TLS security")


def main() -> int:
    documents: dict[str, object] = {}
    for name in JSON_CONTRACTS:
        document = load_json(name)
        validate_json_schema(name, document)
        documents[name] = document
    for name in PUBLIC_CONTRACTS:
        validate_public_boundary(name, documents[name])
    validate_openapi()
    print("Private Runner v1 contracts: OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"Private Runner v1 contracts: FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
