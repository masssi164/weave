#!/usr/bin/env python3
"""Validate and finalize the support-safe human-testing readiness manifest.

The canonical JSON Schema is owned by the pinned Weave Specification Corpus.
This implementation-side guard adds cross-field release invariants that JSON
Schema alone cannot express and intentionally uses only the Python standard
library so it runs on release runners without installing dependencies.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "specs" / "weave-specs.lock.json"
SCHEMA_RELATIVE_PATH = "contracts/jsonschema/human-testing-readiness-manifest.schema.json"
EXPECTED_SCHEMA_ID = "https://weave.test/contracts/human-testing-readiness-manifest.schema.json"
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HASH_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
EMAIL_PATTERN = re.compile(r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
VALID_STATUSES = {"passed", "blocked", "failed", "not_run"}
LOCAL_EVIDENCE_REFERENCE_PATTERN = re.compile(
    r"^(?:artifact|issue):[0-9A-Za-z][0-9A-Za-z._/-]{0,255}$"
)
EVIDENCE_FRAGMENT_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._-]{0,127}$")
REQUIRED_SURFACES = {
    "authenticationSession",
    "home",
    "chat",
    "files",
    "calendar",
    "settings",
    "profile",
}
REQUIRED_PHYSICAL_STEPS = {
    "invitationMail",
    "invitationOpen",
    "keycloakActivation",
    "appLaunch",
    "authorizationCodePkce",
    "normalSession",
    "refresh",
    "logoutRelogin",
    "filesUi",
    "calendarUi",
    "callsUi",
    "grant",
    "mcpDiscovery",
    "filesSearch",
    "fileResourceOpen",
    "revoke",
    "immediateRejection",
    "regrant",
    "postRegrantAccess",
    "identityContinuity",
}
REQUIRED_IMAGE_COMPONENTS = {"server", "mcp-server", "identity-ops", "keycloak-runtime"}
IMMUTABLE_IMAGE_PATTERN = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
REQUIRED_COLLABORATION_SCENARIOS = {
    "authenticationShell",
    "home",
    "chat",
    "files",
    "calendar",
    "settingsProfile",
    "failureContainment",
    "authorization",
}
FORBIDDEN_KEYS = {
    "accesstoken",
    "refreshtoken",
    "idtoken",
    "password",
    "credential",
    "credentials",
    "cookie",
    "cookies",
    "secret",
    "secrets",
    "rawresponse",
    "rawproviderresponse",
    "rawerror",
    "username",
    "email",
    "displayname",
    "roomid",
    "eventid",
    "filename",
}


class ManifestError(ValueError):
    """Raised when a manifest is malformed or unsafe."""


@dataclass(frozen=True)
class Evaluation:
    state: str
    failed_reasons: tuple[str, ...]
    blocked_reasons: tuple[str, ...]

    @property
    def human_testing_ready(self) -> bool:
        return self.state == "ready"


def _load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ManifestError(f"missing JSON file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ManifestError(f"invalid JSON in {path}: {error}") from error
    if not isinstance(value, dict):
        raise ManifestError(f"{path} must contain a JSON object")
    return value


def _require_object(value: Any, field: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ManifestError(f"{field} must be an object")
    return value


def _require_string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ManifestError(f"{field} must be a non-empty string")
    return value


def _require_status(value: Any, field: str) -> str:
    status = _require_string(value, field)
    if status not in VALID_STATUSES:
        raise ManifestError(f"{field} must be one of {sorted(VALID_STATUSES)}")
    return status


def _require_safe_evidence_reference(value: Any, field: str) -> str:
    reference = _require_string(value, field)
    if LOCAL_EVIDENCE_REFERENCE_PATTERN.fullmatch(reference):
        return reference
    parsed = urlparse(reference)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or (parsed.fragment and not EVIDENCE_FRAGMENT_PATTERN.fullmatch(parsed.fragment))
    ):
        raise ManifestError(
            f"{field} must be a support-safe artifact/issue reference or uncredentialed HTTPS URL"
        )
    return reference


def _normalized_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def _walk_support_safe(value: Any, path: str = "manifest") -> Iterable[str]:
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = _normalized_key(str(key))
            if normalized in FORBIDDEN_KEYS:
                yield f"{path}.{key} uses forbidden sensitive field name"
            yield from _walk_support_safe(nested, f"{path}.{key}")
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            yield from _walk_support_safe(nested, f"{path}[{index}]")
    elif isinstance(value, str):
        lowered = value.lower()
        if "bearer " in lowered or "password=" in lowered or "token=" in lowered:
            yield f"{path} contains credential-like material"
        if re.search(r"https?://[^/\s]+@", value):
            yield f"{path} contains a credential-bearing URL"
        if EMAIL_PATTERN.search(value):
            yield f"{path} contains an email address instead of a hash/reference"


def _parse_date_time(value: Any, field: str) -> datetime:
    raw = _require_string(value, field)
    try:
        parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError as error:
        raise ManifestError(f"{field} must be an RFC 3339 date-time") from error
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ManifestError(f"{field} must include a UTC offset")
    return parsed.astimezone(timezone.utc)


def _resolve_schema_reference(root_schema: dict[str, Any], reference: str) -> dict[str, Any]:
    if not reference.startswith("#/"):
        raise ManifestError(f"canonical schema uses unsupported reference: {reference}")
    current: Any = root_schema
    for segment in reference[2:].split("/"):
        key = segment.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or key not in current:
            raise ManifestError(f"canonical schema reference does not resolve: {reference}")
        current = current[key]
    if not isinstance(current, dict):
        raise ManifestError(f"canonical schema reference is not an object: {reference}")
    return current


def _matches_json_type(value: Any, expected: str) -> bool:
    return {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "boolean": isinstance(value, bool),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "null": value is None,
    }.get(expected, False)


def _validate_canonical_schema(
    value: Any,
    schema: dict[str, Any],
    root_schema: dict[str, Any],
    path: str = "manifest",
) -> None:
    """Execute the canonical schema subset used by the pinned v3 contract.

    The release runner intentionally has no ambient Python package dependency.
    Unsupported JSON Schema keywords that affect validation fail closed so a
    corpus change cannot silently weaken this implementation-side gate.
    """

    supported = {
        "$schema", "$id", "$defs", "title", "description", "$ref", "type",
        "const", "enum", "allOf", "contains", "properties", "required",
        "additionalProperties", "items", "minItems", "maxItems", "uniqueItems",
        "minProperties", "minimum", "minLength", "pattern", "format",
    }
    unsupported = set(schema) - supported
    if unsupported:
        raise ManifestError(
            "canonical schema uses unsupported validation keyword(s): "
            + ", ".join(sorted(unsupported))
        )

    reference = schema.get("$ref")
    if reference is not None:
        if not isinstance(reference, str):
            raise ManifestError(f"canonical schema reference at {path} must be a string")
        _validate_canonical_schema(
            value, _resolve_schema_reference(root_schema, reference), root_schema, path
        )

    for index, nested_schema in enumerate(schema.get("allOf", [])):
        if not isinstance(nested_schema, dict):
            raise ManifestError(f"canonical schema allOf[{index}] at {path} is invalid")
        _validate_canonical_schema(value, nested_schema, root_schema, path)

    expected_type = schema.get("type")
    if expected_type is not None:
        expected_types = [expected_type] if isinstance(expected_type, str) else expected_type
        if (
            not isinstance(expected_types, list)
            or not expected_types
            or not all(isinstance(item, str) for item in expected_types)
            or not any(_matches_json_type(value, item) for item in expected_types)
        ):
            raise ManifestError(f"canonical schema validation failed: {path} has the wrong type")

    if "const" in schema and value != schema["const"]:
        raise ManifestError(f"canonical schema validation failed: {path} has the wrong constant")
    enum = schema.get("enum")
    if enum is not None and (not isinstance(enum, list) or value not in enum):
        raise ManifestError(f"canonical schema validation failed: {path} is not an allowed value")

    if isinstance(value, dict):
        properties = schema.get("properties", {})
        if not isinstance(properties, dict):
            raise ManifestError(f"canonical schema properties at {path} must be an object")
        required = schema.get("required", [])
        if not isinstance(required, list) or not all(isinstance(item, str) for item in required):
            raise ManifestError(f"canonical schema required list at {path} is invalid")
        missing = [key for key in required if key not in value]
        if missing:
            raise ManifestError(
                f"canonical schema validation failed: {path} is missing "
                + ", ".join(sorted(missing))
            )
        minimum_properties = schema.get("minProperties")
        if minimum_properties is not None and len(value) < minimum_properties:
            raise ManifestError(f"canonical schema validation failed: {path} has too few properties")
        additional = schema.get("additionalProperties", True)
        for key, nested_value in value.items():
            nested_schema = properties.get(key)
            if nested_schema is None:
                if additional is False:
                    raise ManifestError(
                        f"canonical schema validation failed: {path}.{key} is not allowed"
                    )
                if isinstance(additional, dict):
                    nested_schema = additional
            if nested_schema is not None:
                if not isinstance(nested_schema, dict):
                    raise ManifestError(f"canonical schema property {path}.{key} is invalid")
                _validate_canonical_schema(
                    nested_value, nested_schema, root_schema, f"{path}.{key}"
                )

    if isinstance(value, list):
        minimum_items = schema.get("minItems")
        maximum_items = schema.get("maxItems")
        if minimum_items is not None and len(value) < minimum_items:
            raise ManifestError(f"canonical schema validation failed: {path} has too few items")
        if maximum_items is not None and len(value) > maximum_items:
            raise ManifestError(f"canonical schema validation failed: {path} has too many items")
        if schema.get("uniqueItems") is True:
            normalized = [json.dumps(item, sort_keys=True, separators=(",", ":")) for item in value]
            if len(normalized) != len(set(normalized)):
                raise ManifestError(f"canonical schema validation failed: {path} has duplicate items")
        item_schema = schema.get("items")
        if item_schema is not None:
            if not isinstance(item_schema, dict):
                raise ManifestError(f"canonical schema items at {path} is invalid")
            for index, nested_value in enumerate(value):
                _validate_canonical_schema(
                    nested_value, item_schema, root_schema, f"{path}[{index}]"
                )
        contains_schema = schema.get("contains")
        if contains_schema is not None:
            if not isinstance(contains_schema, dict):
                raise ManifestError(f"canonical schema contains at {path} is invalid")
            matched = False
            for nested_value in value:
                try:
                    _validate_canonical_schema(nested_value, contains_schema, root_schema, path)
                    matched = True
                    break
                except ManifestError:
                    continue
            if not matched:
                raise ManifestError(f"canonical schema validation failed: {path} contains no match")

    if isinstance(value, str):
        minimum_length = schema.get("minLength")
        if minimum_length is not None and len(value) < minimum_length:
            raise ManifestError(f"canonical schema validation failed: {path} is too short")
        pattern = schema.get("pattern")
        if pattern is not None:
            if not isinstance(pattern, str) or re.search(pattern, value) is None:
                raise ManifestError(f"canonical schema validation failed: {path} has invalid syntax")
        value_format = schema.get("format")
        if value_format == "date-time":
            _parse_date_time(value, path)
        elif value_format == "uri":
            parsed = urlparse(value)
            if not parsed.scheme:
                raise ManifestError(f"canonical schema validation failed: {path} is not a URI")
        elif value_format is not None:
            raise ManifestError(f"canonical schema uses unsupported format at {path}: {value_format}")

    minimum = schema.get("minimum")
    if minimum is not None and isinstance(value, (int, float)) and not isinstance(value, bool):
        if value < minimum:
            raise ManifestError(f"canonical schema validation failed: {path} is below minimum")


def _load_canonical_contract() -> tuple[str, dict[str, Any]]:
    lock = _load_json(LOCK_PATH)
    corpus = _require_object(lock.get("specCorpus"), "lock.specCorpus")
    expected_commit = _require_string(corpus.get("gitCommit"), "lock.specCorpus.gitCommit")
    if not COMMIT_PATTERN.fullmatch(expected_commit):
        raise ManifestError("lock.specCorpus.gitCommit must be a full lowercase SHA-1")
    local_path = _require_string(corpus.get("localPath"), "lock.specCorpus.localPath")
    schema_path = (ROOT / local_path / SCHEMA_RELATIVE_PATH).resolve()
    schema = _load_json(schema_path)
    if schema.get("$id") != EXPECTED_SCHEMA_ID:
        raise ManifestError(f"canonical readiness schema has unexpected $id: {schema.get('$id')!r}")
    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise ManifestError("canonical readiness schema must use JSON Schema draft 2020-12")
    required = schema.get("required")
    if not isinstance(required, list) or "humanTestingReady" not in required or "physicalAcceptance" not in required:
        raise ManifestError("canonical readiness schema is missing mandatory release fields")
    return expected_commit, schema


def _check_gate(status: str, field: str, failed: list[str], blocked: list[str]) -> None:
    if status == "failed":
        failed.append(f"{field}=failed")
    elif status != "passed":
        blocked.append(f"{field}={status}")


def evaluate_manifest(
    manifest: dict[str, Any],
    *,
    max_provider_age_seconds: int,
    provider_age_reference: str = "now",
) -> Evaluation:
    if provider_age_reference not in {"now", "generated-at"}:
        raise ManifestError("provider age reference must be now or generated-at")
    expected_spec_commit, schema = _load_canonical_contract()
    support_findings = list(_walk_support_safe(manifest))
    if support_findings:
        raise ManifestError("support-safe validation failed: " + "; ".join(support_findings))
    _validate_canonical_schema(manifest, schema, schema)

    candidate = _require_string(manifest.get("candidateCommit"), "candidateCommit")
    if not COMMIT_PATTERN.fullmatch(candidate):
        raise ManifestError("candidateCommit must be a full lowercase SHA-1")
    source_candidate = _require_string(
        manifest.get("sourceCandidateCommit"), "sourceCandidateCommit"
    )
    if not COMMIT_PATTERN.fullmatch(source_candidate):
        raise ManifestError("sourceCandidateCommit must be a full lowercase SHA-1")
    spec_commit = _require_string(manifest.get("specCorpusCommit"), "specCorpusCommit")
    if spec_commit != expected_spec_commit:
        raise ManifestError(
            f"specCorpusCommit must match the pinned corpus ({expected_spec_commit})"
        )
    generated_at = _parse_date_time(manifest.get("generatedAtUtc"), "generatedAtUtc")
    candidate_manifest_digest = _require_string(
        manifest.get("candidateManifestDigest"), "candidateManifestDigest"
    )
    if not HASH_PATTERN.fullmatch(candidate_manifest_digest):
        raise ManifestError("candidateManifestDigest must be a sha256 reference")
    images = _require_object(manifest.get("images"), "images")
    if set(images) != REQUIRED_IMAGE_COMPONENTS:
        raise ManifestError("images must contain the exact four runtime components")
    for component, reference in images.items():
        if not isinstance(reference, str) or not IMMUTABLE_IMAGE_PATTERN.fullmatch(reference):
            raise ManifestError(f"images.{component} must be an immutable digest reference")
    evidence_modes = manifest.get("evidenceModes")
    if evidence_modes != ["live-provider-backed", "fixture-ui"]:
        raise ManifestError("evidenceModes must contain the ordered mandatory live and fixture modes")

    failed: list[str] = []
    blocked: list[str] = []

    builds = _require_object(manifest.get("builds"), "builds")
    for name in ("backend", "client"):
        build = _require_object(builds.get(name), f"builds.{name}")
        commit = _require_string(build.get("commit"), f"builds.{name}.commit")
        if commit != source_candidate:
            failed.append(f"builds.{name}.commit does not match source candidate")
        _require_string(build.get("version"), f"builds.{name}.version")
        _require_string(build.get("buildNumber"), f"builds.{name}.buildNumber")
    client_build = _require_object(builds.get("client"), "builds.client")
    _require_string(client_build.get("bundleId"), "builds.client.bundleId")

    surfaces = _require_object(manifest.get("surfaces"), "surfaces")
    missing_surfaces = REQUIRED_SURFACES - set(surfaces)
    if missing_surfaces:
        raise ManifestError("surfaces is missing: " + ", ".join(sorted(missing_surfaces)))
    for surface in sorted(REQUIRED_SURFACES):
        gate = _require_object(surfaces.get(surface), f"surfaces.{surface}")
        status = _require_status(gate.get("status"), f"surfaces.{surface}.status")
        proof_kinds = gate.get("proofKinds")
        if (
            not isinstance(proof_kinds, list)
            or not proof_kinds
            or len(proof_kinds) != len(set(proof_kinds))
            or any(
                proof_kind not in {"live-provider-backed", "fixture-ui", "physical-human"}
                for proof_kind in proof_kinds
            )
        ):
            raise ManifestError(f"surfaces.{surface}.proofKinds is invalid")
        evidence_refs = gate.get("evidenceRefs")
        if not isinstance(evidence_refs, list) or not all(
            isinstance(ref, str) and ref.strip() for ref in evidence_refs
        ):
            raise ManifestError(f"surfaces.{surface}.evidenceRefs must be a string list")
        for index, reference in enumerate(evidence_refs):
            _require_safe_evidence_reference(
                reference, f"surfaces.{surface}.evidenceRefs[{index}]"
            )
        if status == "passed" and not evidence_refs:
            blocked.append(f"surfaces.{surface} has no evidence reference")
        _check_gate(status, f"surfaces.{surface}", failed, blocked)

    collaboration = _require_object(manifest.get("collaboration"), "collaboration")
    collaboration_status = _require_status(collaboration.get("status"), "collaboration.status")
    _check_gate(collaboration_status, "collaboration", failed, blocked)
    hashes = _require_object(collaboration.get("identityRefHashes"), "collaboration.identityRefHashes")
    hash_values: list[str] = []
    for role in ("author", "collaborator", "outsider"):
        value = _require_string(hashes.get(role), f"collaboration.identityRefHashes.{role}")
        if not HASH_PATTERN.fullmatch(value):
            raise ManifestError(f"collaboration.identityRefHashes.{role} must be a sha256 reference")
        hash_values.append(value)
    if len(set(hash_values)) != 3:
        failed.append("collaboration identities must be three distinct hashed references")
    scenarios = _require_object(collaboration.get("scenarioResults"), "collaboration.scenarioResults")
    missing_scenarios = REQUIRED_COLLABORATION_SCENARIOS - set(scenarios)
    if missing_scenarios:
        raise ManifestError(
            "collaboration.scenarioResults is missing: " + ", ".join(sorted(missing_scenarios))
        )
    for scenario in sorted(REQUIRED_COLLABORATION_SCENARIOS):
        status = _require_status(scenarios.get(scenario), f"collaboration.scenarioResults.{scenario}")
        _check_gate(status, f"collaboration.{scenario}", failed, blocked)
    cleanup_status = _require_status(collaboration.get("cleanupStatus"), "collaboration.cleanupStatus")
    _check_gate(cleanup_status, "collaboration.cleanup", failed, blocked)
    repeat_count = collaboration.get("repeatCount")
    if not isinstance(repeat_count, int) or isinstance(repeat_count, bool) or repeat_count < 0:
        raise ManifestError("collaboration.repeatCount must be a non-negative integer")
    if repeat_count < 2:
        blocked.append("collaboration suite has not passed twice")

    deployment = _require_object(manifest.get("deployment"), "deployment")
    for field in ("stackStatus", "idempotencyStatus"):
        status = _require_status(deployment.get(field), f"deployment.{field}")
        _check_gate(status, f"deployment.{field}", failed, blocked)
    if deployment.get("persistentHumanUnchanged") is not True:
        failed.append("persistent human dogfood identity was not proven unchanged")

    provider_health = _require_object(manifest.get("providerHealth"), "providerHealth")
    overall = _require_string(provider_health.get("overall"), "providerHealth.overall")
    if overall not in {"available", "degraded", "unavailable"}:
        raise ManifestError("providerHealth.overall must be available, degraded, or unavailable")
    if overall == "unavailable":
        failed.append("providerHealth.overall=unavailable")
    elif overall != "available":
        blocked.append("providerHealth.overall=degraded")
    declared_age = provider_health.get("cachedResultAgeSeconds")
    if not isinstance(declared_age, int) or isinstance(declared_age, bool) or declared_age < 0:
        raise ManifestError("providerHealth.cachedResultAgeSeconds must be a non-negative integer")
    observed_at = _parse_date_time(provider_health.get("observedAtUtc"), "providerHealth.observedAtUtc")
    reference_time = (
        generated_at if provider_age_reference == "generated-at" else datetime.now(timezone.utc)
    )
    age = int((reference_time - observed_at).total_seconds())
    if age < -60:
        raise ManifestError("providerHealth.observedAtUtc must not be after its age reference")
    age = max(0, age)
    if age > max_provider_age_seconds:
        blocked.append(
            f"provider health cache is stale ({age}s > {max_provider_age_seconds}s)"
        )
    if declared_age > max_provider_age_seconds:
        blocked.append(
            "provider health declared cache age is stale "
            f"({declared_age}s > {max_provider_age_seconds}s)"
        )
    capabilities = _require_object(provider_health.get("capabilities"), "providerHealth.capabilities")
    for capability in ("chat", "files", "calendar"):
        state = _require_string(capabilities.get(capability), f"providerHealth.capabilities.{capability}")
        if state == "unavailable":
            failed.append(f"providerHealth.capabilities.{capability}=unavailable")
        elif state != "available":
            blocked.append(f"providerHealth.capabilities.{capability}={state}")

    distribution = _require_object(manifest.get("distribution"), "distribution")
    distribution_status = _require_status(distribution.get("status"), "distribution.status")
    _check_gate(distribution_status, "distribution", failed, blocked)
    channel = _require_string(distribution.get("channel"), "distribution.channel")
    if channel not in {"testflight", "stable-signing-fallback", "none"}:
        raise ManifestError("distribution.channel is invalid")
    if channel == "none":
        blocked.append("distribution channel is none")
    distribution_build = _require_string(distribution.get("buildNumber"), "distribution.buildNumber")
    if distribution_build != client_build.get("buildNumber"):
        failed.append("distribution build number does not match client build")

    physical = _require_object(manifest.get("physicalAcceptance"), "physicalAcceptance")
    physical_status = _require_status(physical.get("status"), "physicalAcceptance.status")
    _check_gate(physical_status, "physicalAcceptance", failed, blocked)
    if physical.get("physicalIPhone") is not True:
        blocked.append("physical iPhone evidence is missing; simulator evidence is insufficient")
    for field in ("voiceOver", "sessionUpgrade", "navigation"):
        status = _require_status(physical.get(field), f"physicalAcceptance.{field}")
        _check_gate(status, f"physicalAcceptance.{field}", failed, blocked)
    tester_hash = _require_string(physical.get("testerRefHash"), "physicalAcceptance.testerRefHash")
    if not HASH_PATTERN.fullmatch(tester_hash):
        raise ManifestError("physicalAcceptance.testerRefHash must be a sha256 reference")
    protocol = _require_object(physical.get("protocol"), "physicalAcceptance.protocol")
    if protocol.get("schemaVersion") != 1:
        raise ManifestError("physicalAcceptance.protocol.schemaVersion must be 1")
    for field, expected in (
        ("candidateCommit", candidate),
        ("sourceCandidateCommit", source_candidate),
        ("specCorpusCommit", spec_commit),
        ("candidateManifestDigest", candidate_manifest_digest),
    ):
        if protocol.get(field) != expected:
            raise ManifestError(f"physicalAcceptance.protocol.{field} does not match readiness identity")
    protocol_build = _require_object(protocol.get("build"), "physicalAcceptance.protocol.build")
    if protocol_build != client_build:
        raise ManifestError("physicalAcceptance.protocol.build does not match the installed client build")
    _require_string(protocol.get("startedAtUtc"), "physicalAcceptance.protocol.startedAtUtc")
    _require_string(protocol.get("completedAtUtc"), "physicalAcceptance.protocol.completedAtUtc")
    if protocol.get("testerConfirmed") is not True:
        blocked.append("physical tester confirmation is missing")
    physical_steps = _require_object(protocol.get("steps"), "physicalAcceptance.protocol.steps")
    if set(physical_steps) != REQUIRED_PHYSICAL_STEPS:
        raise ManifestError("physicalAcceptance.protocol.steps must contain the exact physical protocol")
    for step_name in sorted(REQUIRED_PHYSICAL_STEPS):
        step = _require_object(
            physical_steps.get(step_name), f"physicalAcceptance.protocol.steps.{step_name}"
        )
        step_status = _require_status(
            step.get("status"), f"physicalAcceptance.protocol.steps.{step_name}.status"
        )
        _check_gate(step_status, f"physicalAcceptance.protocol.{step_name}", failed, blocked)
        _require_string(
            step.get("expectedOutcome"),
            f"physicalAcceptance.protocol.steps.{step_name}.expectedOutcome",
        )
        _require_string(
            step.get("actualOutcome"),
            f"physicalAcceptance.protocol.steps.{step_name}.actualOutcome",
        )
        _require_string(
            step.get("observedAtUtc"),
            f"physicalAcceptance.protocol.steps.{step_name}.observedAtUtc",
        )
        step_refs = step.get("evidenceRefs")
        if not isinstance(step_refs, list) or not step_refs:
            raise ManifestError(
                f"physicalAcceptance.protocol.steps.{step_name}.evidenceRefs must not be empty"
            )
        for index, reference in enumerate(step_refs):
            _require_safe_evidence_reference(
                reference,
                f"physicalAcceptance.protocol.steps.{step_name}.evidenceRefs[{index}]",
            )
    deviations = protocol.get("deviations")
    if not isinstance(deviations, list) or any(
        not isinstance(deviation, str) or not deviation.strip() for deviation in deviations
    ):
        raise ManifestError("physicalAcceptance.protocol.deviations must be a string list")

    blockers = manifest.get("blockers")
    if not isinstance(blockers, list):
        raise ManifestError("blockers must be a list")
    for index, blocker_value in enumerate(blockers):
        blocker = _require_object(blocker_value, f"blockers[{index}]")
        _require_string(blocker.get("code"), f"blockers[{index}].code")
        _require_string(blocker.get("summary"), f"blockers[{index}].summary")
        blocker_commit = _require_string(
            blocker.get("candidateCommit"), f"blockers[{index}].candidateCommit"
        )
        if blocker_commit != candidate:
            raise ManifestError(f"blockers[{index}].candidateCommit does not match candidate")
        if blocker.get("code") == "environment-approval-waiting":
            for field in ("environment", "runUrl", "requiredApprover"):
                _require_string(blocker.get(field), f"blockers[{index}].{field}")
            _require_safe_evidence_reference(
                blocker.get("runUrl"), f"blockers[{index}].runUrl"
            )
        blocked.append(f"blocker:{blocker.get('code')}")

    evidence = manifest.get("evidence")
    if not isinstance(evidence, list) or not all(
        isinstance(ref, str) and ref.strip() for ref in evidence
    ):
        raise ManifestError("evidence must be a string list")
    for index, reference in enumerate(evidence):
        _require_safe_evidence_reference(reference, f"evidence[{index}]")
    if not evidence:
        blocked.append("manifest has no evidence references")

    if failed:
        state = "failed"
    elif blocked:
        state = "blocked"
    else:
        state = "ready"
    return Evaluation(state, tuple(failed), tuple(blocked))


def _render_summary(manifest: dict[str, Any], evaluation: Evaluation) -> str:
    lines = [
        f"human-testing-readiness: {evaluation.state}",
        f"candidate={manifest.get('candidateCommit', 'unknown')}",
        f"sourceCandidate={manifest.get('sourceCandidateCommit', 'unknown')}",
        f"specCorpus={manifest.get('specCorpusCommit', 'unknown')}",
        f"humanTestingReady={str(evaluation.human_testing_ready).lower()}",
    ]
    lines.extend(f"failed: {reason}" for reason in evaluation.failed_reasons)
    lines.extend(f"blocked: {reason}" for reason in evaluation.blocked_reasons)
    physical = manifest.get("physicalAcceptance")
    if isinstance(physical, dict):
        lines.append(
            "PHYSICAL_IPHONE_VOICEOVER_RESULT "
            f"status={physical.get('status', 'not_run')} "
            f"physicalIPhone={str(physical.get('physicalIPhone') is True).lower()} "
            f"voiceOver={physical.get('voiceOver', 'not_run')}"
        )
    lines.append(
        "HUMAN_TESTING_READINESS_RESULT "
        f"state={evaluation.state} "
        f"humanTestingReady={str(evaluation.human_testing_ready).lower()}"
    )
    return "\n".join(lines) + "\n"


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("validate", "finalize"):
        sub = subparsers.add_parser(command)
        sub.add_argument("--manifest", type=Path, required=True)
        sub.add_argument("--max-provider-age-seconds", type=int, default=180)
        sub.add_argument(
            "--provider-age-reference",
            choices=("now", "generated-at"),
            default="now",
            help=(
                "use now for the protected live assembly check; use generated-at only when "
                "revalidating an immutable artifact whose live freshness was already proven"
            ),
        )
        if command == "validate":
            sub.add_argument("--require-ready", action="store_true")
        else:
            sub.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    if args.max_provider_age_seconds < 60:
        print("max provider age must be at least the canonical 60-second probe interval", file=sys.stderr)
        return 64
    try:
        manifest = _load_json(args.manifest)
        evaluation = evaluate_manifest(
            manifest,
            max_provider_age_seconds=args.max_provider_age_seconds,
            provider_age_reference=args.provider_age_reference,
        )
        if args.command == "finalize":
            finalized = copy.deepcopy(manifest)
            finalized["state"] = evaluation.state
            finalized["humanTestingReady"] = evaluation.human_testing_ready
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(
                json.dumps(finalized, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        else:
            if manifest.get("state") != evaluation.state:
                raise ManifestError(
                    f"declared state {manifest.get('state')!r} does not match {evaluation.state!r}"
                )
            if manifest.get("humanTestingReady") is not evaluation.human_testing_ready:
                raise ManifestError("declared humanTestingReady does not match evaluated gates")
        print(_render_summary(manifest, evaluation), end="")
        if args.command == "validate" and args.require_ready and not evaluation.human_testing_ready:
            return 1
        return 0
    except ManifestError as error:
        print(f"human-testing-readiness: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
