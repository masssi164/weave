#!/usr/bin/env python3
"""Collect cached provider metrics and assemble persistent dogfood evidence.

The collector reads only Micrometer's in-memory cache gauges from a loopback
Actuator endpoint. It never invokes a provider adapter or records raw metric
responses. The assembler binds those observations to the exact candidate and
to two non-destructive deployment/idempotency observations.
"""

from __future__ import annotations

import argparse
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
IMAGE_ID_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
BUILD_IDENTITY_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
CAPABILITIES = ("files", "calendar")
IMAGE_COMPONENTS = frozenset(("backend", "keycloak", "keycloak-sanitizer", "mcp"))
STATE_BY_VALUE = {0: "unavailable", 1: "degraded", 2: "available"}
PROVIDER_STATES = frozenset(STATE_BY_VALUE.values())


class EvidenceError(ValueError):
    pass


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


def metric_value(base_url: str, name: str, capability: str) -> float:
    url = f"{base_url.rstrip('/')}/{quote(name, safe='')}?tag=capability:{quote(capability, safe='')}"
    try:
        with urlopen(url, timeout=10) as response:  # noqa: S310 - loopback is validated by the workflow
            payload = json.load(response)
    except Exception as error:  # urllib uses several transport exception types
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


def collect_provider_health(base_url: str, chat_state: str) -> dict[str, Any]:
    base_url = validate_loopback_metrics_url(base_url)
    if chat_state not in {"available", "degraded", "unavailable"}:
        raise EvidenceError("chat state must be available, degraded, or unavailable")
    now = datetime.now(timezone.utc)
    capabilities: dict[str, str] = {"chat": chat_state}
    details: dict[str, Any] = {}
    maximum_age = 0
    for capability in CAPABILITIES:
        status = canonical_state(
            metric_value(base_url, "weave.provider.health.status", capability),
            capability,
        )
        age_value = metric_value(base_url, "weave.provider.health.cached.age.seconds", capability)
        if age_value < 0:
            raise EvidenceError(f"cached provider result for {capability} has not been observed")
        age = int(age_value)
        failures = int(metric_value(
            base_url,
            "weave.provider.health.consecutive.failures",
            capability,
        ))
        backoff_epoch = int(metric_value(
            base_url,
            "weave.provider.health.backoff.until.epoch.seconds",
            capability,
        ))
        transitions = int(metric_value(
            base_url,
            "weave.provider.health.readiness.transitions",
            capability,
        ))
        if min(age, failures, backoff_epoch, transitions) < 0:
            raise EvidenceError(f"cached metrics for {capability} contain a negative counter")
        maximum_age = max(maximum_age, age)
        capabilities[capability] = status
        details[capability] = {
            "cachedResultAgeSeconds": age,
            "consecutiveFailures": failures,
            "backoffUntilEpochSeconds": backoff_epoch,
            "readinessTransitions": transitions,
        }
    overall = (
        "unavailable"
        if "unavailable" in capabilities.values()
        else "degraded"
        if "degraded" in capabilities.values()
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
    if evidence.get("supportSafe") is not True or not isinstance(evidence.get("noChanges"), bool):
        raise EvidenceError(f"deployment idempotence evidence is unsafe or malformed: {path}")
    return evidence["noChanges"]


def adoption_gate(path: Path, candidate: str) -> tuple[bool, bool]:
    evidence = load_object(path, "Compose adoption gate evidence")
    required = evidence.get("adoptionRequired")
    if (
        evidence.get("schemaVersion") != "weave.dogfood.compose-adoption-gate.v1"
        or evidence.get("candidateCommit") != candidate
        or evidence.get("status") != "passed"
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
        or not isinstance(required, bool)
    ):
        raise EvidenceError("Compose adoption gate evidence is unsafe, stale, or malformed")
    if required:
        if (
            evidence.get("backupVerified") is not True
            or evidence.get("isolatedRestoreVerified") is not True
            or evidence.get("receiptRef") != "artifact:compose-adoption-receipt.json"
        ):
            raise EvidenceError("pre-existing Compose resources lack current backup/restore adoption proof")
    elif evidence.get("persistentDatabaseVolumePresent") is not False:
        raise EvidenceError("fresh-install adoption evidence does not prove an absent persistent database volume")
    return True, required


def candidate_source_mapping(
    path: Path,
    lane_candidate: str,
) -> tuple[str, dict[str, str]]:
    evidence = load_object(path, "candidate source mapping")
    source_candidate = evidence.get("sourceCandidateCommit")
    source_tree = evidence.get("sourceTree")
    lane_tree = evidence.get("laneTree")
    images = evidence.get("images")
    if (
        evidence.get("schemaVersion") != "weave.candidate-source-mapping.v1"
        or evidence.get("status") != "passed"
        or evidence.get("laneCandidateCommit") != lane_candidate
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
        or not isinstance(source_candidate, str)
        or not COMMIT_PATTERN.fullmatch(source_candidate)
        or not isinstance(evidence.get("protectedDevHead"), str)
        or not COMMIT_PATTERN.fullmatch(evidence["protectedDevHead"])
        or not isinstance(source_tree, str)
        or not COMMIT_PATTERN.fullmatch(source_tree)
        or lane_tree != source_tree
        or not isinstance(images, dict)
        or set(images) != IMAGE_COMPONENTS
        or any(
            not isinstance(image_id, str) or not IMAGE_ID_PATTERN.fullmatch(image_id)
            for image_id in images.values()
        )
    ):
        raise EvidenceError(
            "candidate source mapping is unsafe, incomplete, stale, or not tree-identical"
        )
    return source_candidate, dict(sorted(images.items()))


def assemble_deployment(
    *,
    candidate: str,
    backend_version: str,
    backend_build_number: str,
    run_url: str,
    comparison: dict[str, Any],
    provider_health: dict[str, Any],
    source_candidate: str,
    candidate_images: dict[str, str],
    idempotency_passed: bool = True,
    adoption_verified: bool = True,
    adoption_required: bool = False,
) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(candidate):
        raise EvidenceError("candidate commit must be a full lowercase SHA-1")
    if (
        not COMMIT_PATTERN.fullmatch(source_candidate)
        or set(candidate_images) != IMAGE_COMPONENTS
        or any(not IMAGE_ID_PATTERN.fullmatch(value) for value in candidate_images.values())
    ):
        raise EvidenceError("source candidate and immutable image mapping are incomplete")
    if not BUILD_IDENTITY_PATTERN.fullmatch(backend_version) or not BUILD_IDENTITY_PATTERN.fullmatch(
        backend_build_number
    ):
        raise EvidenceError("backend version and build number must be support-safe build identifiers")
    normalized_run_url = validate_run_url(run_url)
    if comparison.get("schemaVersion") != "weave.persistent-dogfood-comparison.v2":
        raise EvidenceError("persistent comparison schemaVersion is unsupported")
    if comparison.get("supportSafe") is not True:
        raise EvidenceError("persistent comparison is not support-safe")
    baseline_source = comparison.get("baselineSource")
    pre_existing_runtime_observed = comparison.get("preExistingRuntimeObserved")
    if (
        baseline_source not in {"pre-deploy", "first-install"}
        or pre_existing_runtime_observed is not (baseline_source == "pre-deploy")
    ):
        raise EvidenceError("persistent comparison baseline source is malformed")
    persistent_unchanged = (
        comparison.get("status") == "passed"
        and comparison.get("twoNonDestructiveInstallsPreservedState") is True
    )
    if provider_health.get("schemaVersion") != "weave.provider-health-metrics-summary.v1":
        raise EvidenceError("provider health summary schemaVersion is unsupported")
    if provider_health.get("supportSafe") is not True:
        raise EvidenceError("provider health summary is not support-safe")
    provider_overall = provider_health.get("overall")
    capabilities = provider_health.get("capabilities")
    cached_age = provider_health.get("cachedResultAgeSeconds")
    observed_at = provider_health.get("observedAtUtc")
    if (
        provider_health.get("providerProbeTriggered") is not False
        or provider_health.get("rawMetricPayloadIncluded") is not False
        or provider_overall not in PROVIDER_STATES
        or not isinstance(capabilities, dict)
        or any(capabilities.get(name) not in PROVIDER_STATES for name in ("chat", "files", "calendar"))
        or not isinstance(cached_age, int)
        or isinstance(cached_age, bool)
        or cached_age < 0
        or not isinstance(observed_at, str)
        or not observed_at.endswith("Z")
    ):
        raise EvidenceError("provider health summary is malformed or did not use cached metrics")
    deployment_passed = (
        persistent_unchanged
        and idempotency_passed
        and adoption_verified
        and provider_overall == "available"
    )
    blockers: list[dict[str, str]] = []
    if not persistent_unchanged:
        blockers.append({
            "code": "persistent-dogfood-state-changed",
            "summary": "Persistent human, Mailpit, TLS, or active-session state changed across deployment.",
            "candidateCommit": candidate,
        })
    if provider_overall != "available":
        blockers.append({
            "code": "provider-health-not-available",
            "summary": "One or more current provider-backed tabs are degraded or unavailable.",
            "candidateCommit": candidate,
        })
    if not idempotency_passed:
        blockers.append({
            "code": "persistent-dogfood-not-idempotent",
            "summary": "The Compose model, desired-state reconciliation, or repeated deployment reported drift.",
            "candidateCommit": candidate,
        })
    if not adoption_verified:
        blockers.append({
            "code": "persistent-dogfood-adoption-unverified",
            "summary": "Pre-existing Compose resources lack a current private backup and isolated restore rehearsal.",
            "candidateCommit": candidate,
        })
    return {
        "schemaVersion": 1,
        "supportSafe": True,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source_candidate,
        "candidateImages": dict(sorted(candidate_images.items())),
        "backendBuild": {
            "commit": source_candidate,
            "version": backend_version,
            "buildNumber": backend_build_number,
        },
        "deployment": {
            "stackStatus": "passed" if deployment_passed else "blocked",
            "idempotencyStatus": "passed" if idempotency_passed else "failed",
            "adoptionStatus": "passed" if adoption_verified else "failed",
            "adoptionRequired": adoption_required,
            "persistentHumanUnchanged": persistent_unchanged,
            "baselineSource": baseline_source,
            "preExistingRuntimeObserved": pre_existing_runtime_observed,
        },
        "providerHealth": {
            "overall": provider_overall,
            "observedAtUtc": observed_at,
            "cachedResultAgeSeconds": cached_age,
            "capabilities": capabilities,
        },
        "runUrl": normalized_run_url,
        "evidenceRefs": [
            f"{normalized_run_url}#persistent-dogfood-two-install",
            "artifact:provider-health-summary.json",
            "artifact:persistent-dogfood-comparison.json",
            "artifact:compose-adoption-gate.json",
            "artifact:candidate-source-mapping.json",
        ],
        "blockers": blockers,
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    commands = value.add_subparsers(dest="command", required=True)
    collect = commands.add_parser("collect-provider-health")
    collect.add_argument("--actuator-metrics-url", required=True)
    collect.add_argument("--chat-state", required=True)
    collect.add_argument("--output", type=Path, required=True)
    assemble = commands.add_parser("assemble")
    assemble.add_argument("--candidate-commit", required=True)
    assemble.add_argument("--backend-version", required=True)
    assemble.add_argument("--backend-build-number", required=True)
    assemble.add_argument("--run-url", required=True)
    assemble.add_argument("--persistent-comparison", type=Path, required=True)
    assemble.add_argument("--provider-health", type=Path, required=True)
    assemble.add_argument("--deployment-idempotence", type=Path, action="append", required=True)
    assemble.add_argument("--adoption-gate", type=Path, required=True)
    assemble.add_argument("--candidate-source-mapping", type=Path, required=True)
    assemble.add_argument("--output", type=Path, required=True)
    assemble.add_argument("--require-passed", action="store_true")
    return value


def write(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "collect-provider-health":
            summary = collect_provider_health(args.actuator_metrics_url, args.chat_state)
            write(args.output, summary)
            print(
                "DOGFOOD_PROVIDER_HEALTH_RESULT "
                f"status={summary['overall']} cachedAgeSeconds={summary['cachedResultAgeSeconds']} "
                "providerProbeTriggered=false supportSafe=true"
            )
            return 0 if summary["overall"] == "available" else 1
        idempotency_results = [idempotence_passed(path) for path in args.deployment_idempotence]
        idempotency_is_green = all(idempotency_results)
        adoption_is_green, adoption_is_required = adoption_gate(
            args.adoption_gate,
            args.candidate_commit.lower(),
        )
        source_candidate, candidate_images = candidate_source_mapping(
            args.candidate_source_mapping,
            args.candidate_commit.lower(),
        )
        evidence = assemble_deployment(
            candidate=args.candidate_commit.lower(),
            backend_version=args.backend_version,
            backend_build_number=args.backend_build_number,
            run_url=args.run_url,
            comparison=load_object(args.persistent_comparison, "persistent dogfood comparison"),
            provider_health=load_object(args.provider_health, "provider health summary"),
            source_candidate=source_candidate,
            candidate_images=candidate_images,
            idempotency_passed=idempotency_is_green,
            adoption_verified=adoption_is_green,
            adoption_required=adoption_is_required,
        )
        write(args.output, evidence)
        status = evidence["deployment"]["stackStatus"]
        print(
            "DOGFOOD_DEPLOYMENT_RESULT "
            f"status={status} candidate={evidence['candidateCommit']} "
            f"persistentHumanUnchanged={str(evidence['deployment']['persistentHumanUnchanged']).lower()} "
            "supportSafe=true"
        )
        if args.require_passed and status != "passed":
            return 1
        return 0
    except EvidenceError as error:
        print(f"dogfood-deployment-evidence: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
