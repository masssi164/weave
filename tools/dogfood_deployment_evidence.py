#!/usr/bin/env python3
"""Collect cached provider health and assemble manifest-bound dogfood evidence."""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlparse
from urllib.request import urlopen


COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
DIGEST_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_ID_PATTERN = DIGEST_PATTERN
BUILD_IDENTITY_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
CAPABILITIES = ("chat", "files", "calendar")
IMAGE_COMPONENTS = frozenset(("backend", "keycloak", "mcp"))
MANIFEST_IMAGE_COMPONENTS = frozenset(("server", "keycloak-runtime", "mcp-server"))
REALM_ARTIFACT_FIELDS = frozenset(
    ("baselineDigest", "migrationBundleDigest", "containsSecrets")
)
STATE_BY_VALUE = {0: "unavailable", 1: "degraded", 2: "available"}
PROVIDER_STATES = frozenset(STATE_BY_VALUE.values())


class EvidenceError(ValueError):
    pass


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise EvidenceError(f"missing {label}: {path}") from error
    except json.JSONDecodeError as error:
        raise EvidenceError(f"invalid {label} JSON: {error}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    return value


def validate_loopback_metrics_url(base_url: str) -> str:
    parsed = urlparse(base_url)
    if (
        parsed.scheme != "http"
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or parsed.path.rstrip("/") != "/actuator/metrics"
    ):
        raise EvidenceError(
            "Actuator metrics URL must be an unauthenticated loopback HTTP /actuator/metrics endpoint"
        )
    try:
        address = ipaddress.ip_address(parsed.hostname or "")
    except ValueError as error:
        raise EvidenceError("Actuator metrics URL host must be a loopback IP address") from error
    if not address.is_loopback:
        raise EvidenceError("Actuator metrics URL host must be a loopback IP address")
    return base_url.rstrip("/")


def validate_run_url(value: str) -> str:
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise EvidenceError(
            "run URL must be an uncredentialed HTTPS evidence reference without query or fragment"
        )
    return value.rstrip("/")


def metric_value(base_url: str, name: str, capability: str) -> float:
    url = f"{base_url.rstrip('/')}/{quote(name, safe='')}?tag=capability:{quote(capability, safe='')}"
    try:
        with urlopen(url, timeout=10) as response:  # noqa: S310 - loopback is validated
            payload = json.load(response)
    except Exception as error:
        raise EvidenceError(f"cannot read cached metric {name} for {capability}: {error}") from error
    measurements = payload.get("measurements") if isinstance(payload, dict) else None
    if not isinstance(measurements, list):
        raise EvidenceError(f"metric {name} for {capability} has no measurements")
    for measurement in measurements:
        if isinstance(measurement, dict) and measurement.get("statistic") == "VALUE":
            value = measurement.get("value")
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                return float(value)
    raise EvidenceError(f"metric {name} for {capability} has no VALUE measurement")


def canonical_state(value: float, capability: str) -> str:
    rounded = round(value)
    if abs(value - rounded) > 0.001 or rounded not in STATE_BY_VALUE:
        raise EvidenceError(f"cached status for {capability} is outside the canonical state range")
    return STATE_BY_VALUE[rounded]


def collect_provider_health(base_url: str) -> dict[str, Any]:
    base_url = validate_loopback_metrics_url(base_url)
    now = datetime.now(timezone.utc)
    capabilities: dict[str, str] = {}
    details: dict[str, Any] = {}
    maximum_age = 0
    for capability in CAPABILITIES:
        status = canonical_state(
            metric_value(base_url, "weave.provider.health.status", capability), capability
        )
        age = int(metric_value(base_url, "weave.provider.health.cached.age.seconds", capability))
        failures = int(metric_value(base_url, "weave.provider.health.consecutive.failures", capability))
        backoff_epoch = int(metric_value(base_url, "weave.provider.health.backoff.until.epoch.seconds", capability))
        transitions = int(metric_value(base_url, "weave.provider.health.readiness.transitions", capability))
        if min(age, failures, backoff_epoch, transitions) < 0:
            raise EvidenceError(f"cached metrics for {capability} contain a negative value")
        maximum_age = max(maximum_age, age)
        capabilities[capability] = status
        details[capability] = {
            "cachedResultAgeSeconds": age,
            "consecutiveFailures": failures,
            "backoffUntilEpochSeconds": backoff_epoch,
            "readinessTransitions": transitions,
        }
    overall = (
        "unavailable" if "unavailable" in capabilities.values()
        else "degraded" if "degraded" in capabilities.values()
        else "available"
    )
    observed = now - timedelta(seconds=maximum_age)
    return {
        "schemaVersion": "weave.provider-health-metrics-summary.v1",
        "supportSafe": True,
        "source": "loopback-actuator-cached-metrics",
        "providerProbeTriggered": False,
        "overall": overall,
        "observedAtUtc": observed.isoformat().replace("+00:00", "Z"),
        "cachedResultAgeSeconds": maximum_age,
        "capabilities": capabilities,
        "details": details,
        "rawMetricPayloadIncluded": False,
    }


def idempotence_passed(path: Path) -> bool:
    evidence = load_object(path, "deployment idempotence evidence")
    if (
        evidence.get("schemaVersion") != "weave.persistent-test-idempotence.v3"
        or evidence.get("runtimeProfile") != "dogfood"
        or evidence.get("deploymentContext") != "persistent-dogfood"
        or not isinstance(evidence.get("noChanges"), bool)
        or evidence.get("composeModelStable") is not True
        or evidence.get("realmArtifactsUnchanged") is not True
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
    ):
        raise EvidenceError(f"deployment idempotence evidence is unsafe or malformed: {path}")
    return evidence["noChanges"]


def candidate_source_mapping(path: Path, lane_candidate: str) -> tuple[str, dict[str, str]]:
    evidence = load_object(path, "candidate source mapping")
    source = evidence.get("sourceCandidateCommit")
    images = evidence.get("images")
    if (
        evidence.get("schemaVersion") != "weave.candidate-source-mapping.v1"
        or evidence.get("status") != "passed"
        or evidence.get("laneCandidateCommit") != lane_candidate
        or evidence.get("sourceTree") != evidence.get("laneTree")
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
        or not isinstance(source, str)
        or not COMMIT_PATTERN.fullmatch(source)
        or not isinstance(images, dict)
        or set(images) != IMAGE_COMPONENTS
        or any(not isinstance(value, str) or not IMAGE_ID_PATTERN.fullmatch(value) for value in images.values())
    ):
        raise EvidenceError("candidate source mapping is unsafe, incomplete, or stale")
    return source, dict(sorted(images.items()))


def candidate_manifest_realm_artifacts(
    path: Path,
    source_candidate: str,
    expected_digest: str,
) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise EvidenceError("candidate manifest must be a regular file")
    raw = path.read_bytes()
    actual_digest = "sha256:" + hashlib.sha256(raw).hexdigest()
    manifest = load_object(path, "candidate manifest")
    images = manifest.get("images")
    realm_artifacts = manifest.get("realmArtifacts")
    if (
        actual_digest != expected_digest
        or manifest.get("schemaVersion") != "weave.release.candidate-manifest.v3"
        or manifest.get("supportSafe") is not True
        or manifest.get("commit") != source_candidate
        or not isinstance(images, list)
        or len(images) != len(MANIFEST_IMAGE_COMPONENTS)
        or any(not isinstance(item, dict) for item in images)
        or {
            item.get("component")
            for item in images
            if isinstance(item, dict)
        }
        != MANIFEST_IMAGE_COMPONENTS
        or not isinstance(realm_artifacts, dict)
        or set(realm_artifacts) != REALM_ARTIFACT_FIELDS
        or not DIGEST_PATTERN.fullmatch(
            str(realm_artifacts.get("baselineDigest", ""))
        )
        or not DIGEST_PATTERN.fullmatch(
            str(realm_artifacts.get("migrationBundleDigest", ""))
        )
        or realm_artifacts.get("containsSecrets") is not False
    ):
        raise EvidenceError(
            "candidate manifest realm artifact evidence is unsafe, incomplete, or stale"
        )
    return dict(realm_artifacts)


def validate_provider_health(value: dict[str, Any]) -> None:
    capabilities = value.get("capabilities")
    if (
        value.get("schemaVersion") != "weave.provider-health-metrics-summary.v1"
        or value.get("supportSafe") is not True
        or value.get("providerProbeTriggered") is not False
        or value.get("rawMetricPayloadIncluded") is not False
        or value.get("overall") not in PROVIDER_STATES
        or not isinstance(capabilities, dict)
        or any(capabilities.get(name) not in PROVIDER_STATES for name in ("chat", "files", "calendar"))
        or not isinstance(value.get("cachedResultAgeSeconds"), int)
        or isinstance(value.get("cachedResultAgeSeconds"), bool)
        or value["cachedResultAgeSeconds"] < 0
        or not isinstance(value.get("observedAtUtc"), str)
        or not value["observedAtUtc"].endswith("Z")
    ):
        raise EvidenceError("provider health summary is malformed or did not use cached metrics")


def validate_fresh_start_cut(
    value: dict[str, Any], lane: str, source: str, manifest_digest: str
) -> None:
    if (
        value.get("schemaVersion") != "weave.fresh-start-cut-report.v2"
        or value.get("laneCandidateCommit") != lane
        or value.get("sourceCandidateCommit") != source
        or value.get("candidateManifestDigest") != manifest_digest
        or value.get("status") != "passed"
        or value.get("schemaConverged") is not True
        or value.get("realmArtifactsVerified") is not True
        or value.get("imagesVerified") is not True
        or value.get("firstOwnerBootstrapRequired") is not True
        or value.get("ownerInvitationCreated") is not False
        or value.get("legacyStateMigrated") is not False
        or value.get("adoptionAuthorized") is not False
        or value.get("supportSafe") is not True
        or value.get("containsSecretValues") is not False
    ):
        raise EvidenceError("Fresh Start cut report is unsafe, stale, or incomplete")


def validate_persistent_comparison(value: dict[str, Any]) -> None:
    if (
        value.get("schemaVersion") != "weave.persistent-dogfood-comparison.v3"
        or value.get("status") != "passed"
        or value.get("baselineSource") != "pre-deploy"
        or value.get("preExistingRuntimeObserved") is not True
        or value.get("twoNonDestructiveInstallsPreservedState") is not True
        or value.get("identityStoreVolumePreserved") is not True
        or value.get("mailpitVolumePreserved") is not True
        or value.get("tlsIdentityPreserved") is not True
        or value.get("humanWriterAbsent") is not True
        or value.get("supportSafe") is not True
    ):
        raise EvidenceError("persistent dogfood comparison is unsafe or incomplete")


def assemble_deployment(
    *,
    candidate: str,
    source_candidate: str,
    candidate_manifest_digest: str,
    backend_version: str,
    backend_build_number: str,
    run_url: str,
    provider_health: dict[str, Any],
    candidate_images: dict[str, str],
    realm_artifacts: dict[str, Any],
    idempotency_passed: bool,
    fresh_start_cut: dict[str, Any] | None = None,
    persistent_comparison: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(candidate) or not COMMIT_PATTERN.fullmatch(source_candidate):
        raise EvidenceError("lane and source candidate commits must be full lowercase SHA-1 values")
    if not DIGEST_PATTERN.fullmatch(candidate_manifest_digest):
        raise EvidenceError("candidate manifest digest must be exact")
    if set(candidate_images) != IMAGE_COMPONENTS:
        raise EvidenceError("candidate image mapping is incomplete")
    if (
        not isinstance(realm_artifacts, dict)
        or set(realm_artifacts) != REALM_ARTIFACT_FIELDS
        or not DIGEST_PATTERN.fullmatch(
            str(realm_artifacts.get("baselineDigest", ""))
        )
        or not DIGEST_PATTERN.fullmatch(
            str(realm_artifacts.get("migrationBundleDigest", ""))
        )
        or realm_artifacts.get("containsSecrets") is not False
    ):
        raise EvidenceError("realm artifact evidence is incomplete or unsafe")
    if not BUILD_IDENTITY_PATTERN.fullmatch(backend_version) or not BUILD_IDENTITY_PATTERN.fullmatch(backend_build_number):
        raise EvidenceError("backend build identity is malformed")
    if (fresh_start_cut is None) == (persistent_comparison is None):
        raise EvidenceError("exactly one Fresh Start cut or routine persistent comparison is required")
    validate_provider_health(provider_health)
    if fresh_start_cut is not None:
        validate_fresh_start_cut(fresh_start_cut, candidate, source_candidate, candidate_manifest_digest)
        baseline_source = "fresh-start"
        fresh_status = "passed"
        owner_activation = "not-started"
        identity_storage_preserved = False
    else:
        assert persistent_comparison is not None
        validate_persistent_comparison(persistent_comparison)
        baseline_source = "pre-deploy"
        fresh_status = "not-required"
        owner_activation = "passed"
        identity_storage_preserved = True
    provider_available = provider_health["overall"] == "available"
    passed = idempotency_passed and provider_available
    blockers: list[dict[str, str]] = []
    if not idempotency_passed:
        blockers.append({"code": "persistent-dogfood-not-idempotent", "summary": "The repeated deployment reported drift.", "candidateCommit": candidate})
    if not provider_available:
        blockers.append({"code": "provider-health-not-available", "summary": "One or more current provider-backed surfaces are not available.", "candidateCommit": candidate})
    if owner_activation == "not-started":
        blockers.append({"code": "first-owner-bootstrap-required", "summary": "The bounded first-owner invitation bootstrap must run before human activation.", "candidateCommit": candidate})
    normalized_url = validate_run_url(run_url)
    return {
        "schemaVersion": 3,
        "supportSafe": True,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source_candidate,
        "candidateManifestDigest": candidate_manifest_digest,
        "realmArtifacts": dict(realm_artifacts),
        "candidateImages": dict(sorted(candidate_images.items())),
        "backendBuild": {"commit": source_candidate, "version": backend_version, "buildNumber": backend_build_number},
        "deployment": {
            "stackStatus": "passed" if passed else "blocked",
            "idempotencyStatus": "passed" if idempotency_passed else "failed",
            "baselineSource": baseline_source,
            "freshStartStatus": fresh_status,
            "ownerActivationStatus": owner_activation,
            "persistentHumanUnchanged": identity_storage_preserved,
            "legacyStateMigrated": False,
            "adoptionAuthorized": False,
            "realmArtifactsVerified": True,
        },
        "providerHealth": {
            "overall": provider_health["overall"],
            "observedAtUtc": provider_health["observedAtUtc"],
            "cachedResultAgeSeconds": provider_health["cachedResultAgeSeconds"],
            "capabilities": provider_health["capabilities"],
        },
        "runUrl": normalized_url,
        "evidenceRefs": [
            f"{normalized_url}#dogfood-deployment",
            "artifact:candidate-source-mapping.json",
            "artifact:candidate-manifest.json",
            "artifact:provider-health-summary.json",
            "artifact:persistent-test-idempotence.json",
            "artifact:fresh-start-cut-report.json" if fresh_start_cut is not None else "artifact:persistent-dogfood-comparison.json",
        ],
        "blockers": blockers,
    }


def write(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    commands = value.add_subparsers(dest="command", required=True)
    collect = commands.add_parser("collect-provider-health")
    collect.add_argument("--actuator-metrics-url", required=True)
    collect.add_argument("--output", type=Path, required=True)
    assemble = commands.add_parser("assemble")
    assemble.add_argument("--candidate-commit", required=True)
    assemble.add_argument("--candidate-manifest-digest", required=True)
    assemble.add_argument("--candidate-manifest", type=Path, required=True)
    assemble.add_argument("--backend-version", required=True)
    assemble.add_argument("--backend-build-number", required=True)
    assemble.add_argument("--run-url", required=True)
    baseline = assemble.add_mutually_exclusive_group(required=True)
    baseline.add_argument("--fresh-start-cut-report", type=Path)
    baseline.add_argument("--persistent-comparison", type=Path)
    assemble.add_argument("--provider-health", type=Path, required=True)
    assemble.add_argument("--deployment-idempotence", type=Path, action="append", required=True)
    assemble.add_argument("--candidate-source-mapping", type=Path, required=True)
    assemble.add_argument("--output", type=Path, required=True)
    assemble.add_argument("--require-passed", action="store_true")
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "collect-provider-health":
            summary = collect_provider_health(args.actuator_metrics_url)
            write(args.output, summary)
            print(f"DOGFOOD_PROVIDER_HEALTH_RESULT status={summary['overall']} supportSafe=true")
            return 0 if summary["overall"] == "available" else 1
        lane = args.candidate_commit.lower()
        source, images = candidate_source_mapping(args.candidate_source_mapping, lane)
        manifest_digest = args.candidate_manifest_digest.lower()
        realm_artifacts = candidate_manifest_realm_artifacts(
            args.candidate_manifest,
            source,
            manifest_digest,
        )
        evidence = assemble_deployment(
            candidate=lane,
            source_candidate=source,
            candidate_manifest_digest=manifest_digest,
            backend_version=args.backend_version,
            backend_build_number=args.backend_build_number,
            run_url=args.run_url,
            provider_health=load_object(args.provider_health, "provider health summary"),
            candidate_images=images,
            realm_artifacts=realm_artifacts,
            idempotency_passed=all(idempotence_passed(path) for path in args.deployment_idempotence),
            fresh_start_cut=(load_object(args.fresh_start_cut_report, "Fresh Start cut report") if args.fresh_start_cut_report else None),
            persistent_comparison=(load_object(args.persistent_comparison, "persistent dogfood comparison") if args.persistent_comparison else None),
        )
        write(args.output, evidence)
        status = evidence["deployment"]["stackStatus"]
        print(f"DOGFOOD_DEPLOYMENT_RESULT status={status} lane={lane} source={source} supportSafe=true")
        return 1 if args.require_passed and status != "passed" else 0
    except EvidenceError as error:
        print(f"dogfood-deployment-evidence: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
