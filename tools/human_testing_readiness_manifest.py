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


def evaluate_manifest(manifest: dict[str, Any], *, max_provider_age_seconds: int) -> Evaluation:
    expected_spec_commit, schema = _load_canonical_contract()
    schema_required = schema.get("required", [])
    missing = [key for key in schema_required if key not in manifest]
    if missing:
        raise ManifestError("manifest is missing required fields: " + ", ".join(sorted(missing)))
    if manifest.get("schemaVersion") != 1:
        raise ManifestError("schemaVersion must be 1")

    candidate = _require_string(manifest.get("candidateCommit"), "candidateCommit")
    if not COMMIT_PATTERN.fullmatch(candidate):
        raise ManifestError("candidateCommit must be a full lowercase SHA-1")
    spec_commit = _require_string(manifest.get("specCorpusCommit"), "specCorpusCommit")
    if spec_commit != expected_spec_commit:
        raise ManifestError(
            f"specCorpusCommit must match the pinned corpus ({expected_spec_commit})"
        )
    _require_string(manifest.get("generatedAtUtc"), "generatedAtUtc")

    support_findings = list(_walk_support_safe(manifest))
    if support_findings:
        raise ManifestError("support-safe validation failed: " + "; ".join(support_findings))

    failed: list[str] = []
    blocked: list[str] = []

    builds = _require_object(manifest.get("builds"), "builds")
    for name in ("backend", "client"):
        build = _require_object(builds.get(name), f"builds.{name}")
        commit = _require_string(build.get("commit"), f"builds.{name}.commit")
        if commit != candidate:
            failed.append(f"builds.{name}.commit does not match candidate")
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
    age = provider_health.get("cachedResultAgeSeconds")
    if not isinstance(age, int) or isinstance(age, bool) or age < 0:
        raise ManifestError("providerHealth.cachedResultAgeSeconds must be a non-negative integer")
    if age > max_provider_age_seconds:
        blocked.append(
            f"provider health cache is stale ({age}s > {max_provider_age_seconds}s)"
        )
    _require_string(provider_health.get("observedAtUtc"), "providerHealth.observedAtUtc")
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
