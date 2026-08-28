#!/usr/bin/env python3
"""Validate private Runner v1 schemas and HTTP contracts without network access."""

from __future__ import annotations

import json
import pathlib
import sys
from collections.abc import Iterable
from typing import Any
from urllib.parse import urlparse

import yaml

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
    "/runner/v1/certificates:rotate",
    "/runner/v1/capability-bundle",
    "/runner/v1/heartbeat",
    "/runner/v1/tasks:claim",
    "/runner/v1/tasks/{taskId}:heartbeat",
    "/runner/v1/tasks/{taskId}/artifacts/{artifactId}",
    "/runner/v1/tasks/{taskId}:complete",
    "/runner/v1/tasks/{taskId}:fail",
    "/runner/v1/observations",
)
HTTP_METHODS = {"get", "put", "post", "delete", "patch", "head", "options", "trace"}


class ContractError(AssertionError):
    pass


def fail(message: str) -> None:
    raise ContractError(message)


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
        raise ContractError(f"missing Runner contract: {path.relative_to(ROOT)}") from exc
    except json.JSONDecodeError as exc:
        raise ContractError(f"invalid JSON in {path.relative_to(ROOT)}: {exc}") from exc


def validate_json_schema(name: str, document: object) -> None:
    if not isinstance(document, dict):
        fail(f"{name} must contain a JSON object")
    if document.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        fail(f"{name} must use JSON Schema 2020-12")
    schema_id = document.get("$id")
    if not isinstance(schema_id, str):
        fail(f"{name} requires an absolute URI $id")
    parsed_id = urlparse(schema_id)
    if not parsed_id.scheme or parsed_id.scheme.lower() == "http":
        fail(f"{name} requires a non-HTTP absolute URI $id")
    if document.get("type") != "object":
        fail(f"{name} root type must be object")
    if document.get("additionalProperties") is not False:
        fail(f"{name} root must fail closed on unknown properties")

    for location, value in walk(document):
        if not isinstance(value, dict) or "$ref" not in value:
            continue
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
    """Reject executable coordinates as fields; explanatory prohibition text is allowed."""
    for location, value in walk(document):
        if not isinstance(value, dict):
            continue
        properties = value.get("properties")
        if not isinstance(properties, dict):
            continue
        leaked = FORBIDDEN_PUBLIC_KEYS.intersection(properties)
        if leaked:
            fail(f"{name} leaks private Runner fields at {location}: {sorted(leaked)}")


def validate_task_lease_contract(document: object) -> None:
    if not isinstance(document, dict):
        fail("task-lease.schema.json must contain an object")
    required = document.get("required")
    properties = document.get("properties")
    if not isinstance(required, list) or not isinstance(properties, dict):
        fail("task lease must declare required fields and properties")
    for field in ("capabilityContractDigest", "bundleDigest"):
        if field not in required or field not in properties:
            fail(f"task lease must expose {field}")
    if properties["capabilityContractDigest"] == properties["bundleDigest"]:
        fail("task contract and selected public bundle need distinct schema entries")


def load_openapi() -> dict[str, Any]:
    path = CONTRACT_ROOT / "runner-control.openapi.yaml"
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ContractError(f"missing Runner OpenAPI contract: {path.relative_to(ROOT)}") from exc
    except yaml.YAMLError as exc:
        raise ContractError(f"invalid YAML in {path.relative_to(ROOT)}: {exc}") from exc
    if not isinstance(document, dict):
        fail("Runner control API must contain a YAML object")
    return document


def resolve_parameter(document: dict[str, Any], value: object) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail("Runner OpenAPI parameter must be an object")
    reference = value.get("$ref")
    if reference is None:
        return value
    prefix = "#/components/parameters/"
    if not isinstance(reference, str) or not reference.startswith(prefix):
        fail(f"Runner OpenAPI contains unsupported parameter reference {reference!r}")
    parameters = document.get("components", {}).get("parameters", {})
    resolved = parameters.get(reference.removeprefix(prefix)) if isinstance(parameters, dict) else None
    if not isinstance(resolved, dict):
        fail(f"Runner OpenAPI cannot resolve parameter reference {reference}")
    return resolved


def require_response_headers(response: object, names: set[str], status: str) -> None:
    if not isinstance(response, dict):
        fail(f"Runner task claim response {status} must be an object")
    headers = response.get("headers")
    if not isinstance(headers, dict):
        fail(f"Runner task claim response {status} must declare response headers")
    missing = names - set(headers)
    if missing:
        fail(f"Runner task claim response {status} is missing headers {sorted(missing)}")


def validate_openapi() -> None:
    document = load_openapi()
    version = document.get("openapi")
    if not isinstance(version, str) or not version.startswith("3.1."):
        fail("Runner control API must use OpenAPI 3.1")

    servers = document.get("servers")
    if not isinstance(servers, list) or not servers:
        fail("Runner control API must declare at least one HTTPS server")
    for server in servers:
        url = server.get("url") if isinstance(server, dict) else None
        if not isinstance(url, str) or urlparse(url).scheme.lower() != "https":
            fail("Runner control API server URLs must use HTTPS")

    components = document.get("components")
    if not isinstance(components, dict):
        fail("Runner control API must declare components")
    component_schemas = components.get("schemas")
    if not isinstance(component_schemas, dict):
        fail("Runner control API must declare component schemas")

    paths = document.get("paths")
    if not isinstance(paths, dict):
        fail("Runner control API must declare paths")
    for required_path in REQUIRED_OPENAPI_PATHS:
        if required_path not in paths:
            fail(f"Runner control API is missing path {required_path}")

    security_schemes = components.get("securitySchemes", {})
    runner_mtls = security_schemes.get("RunnerMutualTls") if isinstance(security_schemes, dict) else None
    if not isinstance(runner_mtls, dict) or runner_mtls.get("type") != "mutualTLS":
        fail("Runner control API must declare RunnerMutualTls as mutualTLS")

    for path_name, path_item in paths.items():
        if not isinstance(path_item, dict):
            fail(f"Runner control path {path_name} must be an object")
        for method, operation in path_item.items():
            if method not in HTTP_METHODS:
                continue
            if not isinstance(operation, dict):
                fail(f"Runner control operation {method.upper()} {path_name} must be an object")
            if path_name == "/runner/v1/enrollments:exchange":
                continue
            security = operation.get("security")
            if not isinstance(security, list) or not any(
                isinstance(requirement, dict) and "RunnerMutualTls" in requirement
                for requirement in security
            ):
                fail(f"Runner control operation {method.upper()} {path_name} must require mutual TLS")

    claim = paths["/runner/v1/tasks:claim"].get("post")
    if not isinstance(claim, dict):
        fail("Runner task claim must be POST")
    parameters = [resolve_parameter(document, value) for value in claim.get("parameters", [])]
    if any(parameter.get("name") == "waitSeconds" for parameter in parameters):
        fail("Runner task claim must not use the legacy waitSeconds query parameter")
    prefer = [
        parameter
        for parameter in parameters
        if str(parameter.get("name", "")).lower() == "prefer"
    ]
    if len(prefer) != 1 or str(prefer[0].get("in", "")).lower() != "header":
        fail("Runner task claim must declare exactly one Prefer request header")
    schema = prefer[0].get("schema")
    if not isinstance(schema, dict) or schema.get("type") != "string":
        fail("Runner task claim Prefer header must use a bounded string schema")
    pattern = schema.get("pattern")
    if not isinstance(pattern, str) or "wait=" not in pattern:
        fail("Runner task claim Prefer header must constrain the wait preference")

    claim_schema = component_schemas.get("TaskClaimRequest")
    if not isinstance(claim_schema, dict):
        fail("Runner task claim schema is missing")
    claim_required = claim_schema.get("required")
    claim_properties = claim_schema.get("properties")
    if set(claim_required or []) != {"runnerId", "bundleDigest", "availableSlots"}:
        fail("Runner task claim must contain only Runner, public bundle, and capacity coordinates")
    if not isinstance(claim_properties, dict):
        fail("Runner task claim must declare properties")
    if "capabilities" in claim_properties:
        fail("Runner task claim must not self-advertise capability coordinates")

    responses = claim.get("responses")
    if not isinstance(responses, dict):
        fail("Runner task claim must declare responses")
    require_response_headers(
        responses.get("200"),
        {"Cache-Control", "Preference-Applied"},
        "200",
    )
    require_response_headers(
        responses.get("204"),
        {"Cache-Control", "Preference-Applied", "Retry-After"},
        "204",
    )

    for location, value in walk(document):
        if isinstance(value, dict):
            leaked = FORBIDDEN_PUBLIC_KEYS.intersection(value)
            if leaked:
                fail(f"Runner control API leaks private fields at {location}: {sorted(leaked)}")
        elif isinstance(value, str) and "http://" in value:
            fail(f"Runner control API contains insecure HTTP at {location}")


def main() -> int:
    documents: dict[str, object] = {}
    for name in JSON_CONTRACTS:
        document = load_json(name)
        validate_json_schema(name, document)
        documents[name] = document
    for name in PUBLIC_CONTRACTS:
        validate_public_boundary(name, documents[name])
    validate_task_lease_contract(documents["task-lease.schema.json"])
    validate_openapi()
    print("Private Runner v1 contracts: OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as exc:
        print(f"Private Runner v1 contracts: FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
