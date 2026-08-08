#!/usr/bin/env python3
"""Bind a fresh support-safe provider-health snapshot to one dogfood candidate."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
IMMUTABLE_IMAGE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
RUN_ID = re.compile(r"^[0-9]+$")
PROVIDER_STATES = {"available", "degraded", "unavailable"}
IMAGE_COMPONENTS = {
    "backend": "server",
    "mcp": "mcp-server",
    "keycloak": "keycloak-runtime",
}
REALM_ARTIFACT_FIELDS = {"baselineDigest", "migrationBundleDigest", "containsSecrets"}


class EvidenceError(ValueError):
    """Raised when the health evidence graph is incomplete or stale."""


def load(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise EvidenceError(f"missing {label}: {path}") from error
    except json.JSONDecodeError as error:
        raise EvidenceError(f"invalid {label} JSON: {error}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    return value


def safe_run_url(value: str, run_id: str) -> str:
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or not parsed.path.rstrip("/").endswith(f"/actions/runs/{run_id}")
    ):
        raise EvidenceError("run URL must be an uncredentialed exact GitHub Actions run URL")
    return value.rstrip("/")


def observed_age_seconds(value: str) -> int:
    try:
        observed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise EvidenceError("provider health observedAtUtc must be an ISO-8601 timestamp") from error
    if observed.tzinfo is None:
        raise EvidenceError("provider health observedAtUtc must include a timezone")
    age = int((datetime.now(timezone.utc) - observed.astimezone(timezone.utc)).total_seconds())
    if age < -5:
        raise EvidenceError("provider health observedAtUtc is in the future")
    return max(age, 0)


def validate_health(value: dict[str, Any], max_age_seconds: int) -> dict[str, Any]:
    capabilities = value.get("capabilities")
    claimed_age = value.get("cachedResultAgeSeconds")
    if (
        value.get("schemaVersion") != "weave.provider-health-metrics-summary.v1"
        or value.get("supportSafe") is not True
        or value.get("providerProbeTriggered") is not False
        or value.get("rawMetricPayloadIncluded") is not False
        or value.get("source") != "loopback-actuator-cached-metrics"
        or value.get("overall") != "available"
        or not isinstance(capabilities, dict)
        or set(capabilities) != {"chat", "files", "calendar"}
        or any(capabilities[name] != "available" for name in capabilities)
        or not isinstance(claimed_age, int)
        or isinstance(claimed_age, bool)
        or claimed_age < 0
        or not isinstance(value.get("observedAtUtc"), str)
    ):
        raise EvidenceError("provider health must be available, complete, cached, and support-safe")
    actual_age = observed_age_seconds(value["observedAtUtc"])
    if actual_age > max_age_seconds or claimed_age > max_age_seconds:
        raise EvidenceError(
            f"provider health is stale ({actual_age}s actual, {claimed_age}s cached; max {max_age_seconds}s)"
        )
    return {
        "overall": "available",
        "observedAtUtc": value["observedAtUtc"],
        "cachedResultAgeSeconds": claimed_age,
        "capabilities": dict(sorted(capabilities.items())),
    }


def assemble(
    *,
    candidate: str,
    deployment_run_id: str,
    health_run_id: str,
    health_run_url: str,
    deployment: dict[str, Any],
    manifest: dict[str, Any],
    runtime_observation: dict[str, Any],
    health: dict[str, Any],
    max_age_seconds: int,
) -> dict[str, Any]:
    if not COMMIT.fullmatch(candidate):
        raise EvidenceError("candidate commit must be one full lowercase SHA-1")
    if not RUN_ID.fullmatch(deployment_run_id) or not RUN_ID.fullmatch(health_run_id):
        raise EvidenceError("deployment and health run IDs must be numeric")
    deployment_url = safe_run_url(str(deployment.get("runUrl", "")), deployment_run_id)
    normalized_health_url = safe_run_url(health_run_url, health_run_id)
    lane = manifest.get("laneCandidateCommit")
    source = manifest.get("sourceCandidateCommit")
    specification = manifest.get("specificationCommit")
    manifest_digest = manifest.get("candidateManifestDigest")
    runtime = manifest.get("runtime")
    manifest_images = manifest.get("images")
    deployment_images = deployment.get("candidateImages")
    manifest_realm_artifacts = manifest.get("realmArtifacts")
    deployment_realm_artifacts = deployment.get("realmArtifacts")
    observed_images = runtime_observation.get("images")
    if (
        manifest.get("schemaVersion") != "weave.test-stack-manifest.v3"
        or manifest.get("supportSafe") is not True
        or manifest.get("containsSecretValues") is not False
        or lane != candidate
        or not isinstance(source, str)
        or COMMIT.fullmatch(source) is None
        or not isinstance(specification, str)
        or COMMIT.fullmatch(specification) is None
        or not isinstance(manifest_digest, str)
        or DIGEST.fullmatch(manifest_digest) is None
        or not isinstance(runtime, dict)
        or runtime.get("environment") != "persistent-dogfood"
        or runtime.get("scope") != "persistent"
        or not isinstance(runtime.get("composeProject"), str)
        or not isinstance(runtime.get("generation"), str)
        or not isinstance(manifest_images, dict)
        or set(manifest_images) != set(IMAGE_COMPONENTS)
        or not isinstance(manifest_realm_artifacts, dict)
        or set(manifest_realm_artifacts) != REALM_ARTIFACT_FIELDS
        or DIGEST.fullmatch(
            str(manifest_realm_artifacts.get("baselineDigest", ""))
        ) is None
        or DIGEST.fullmatch(
            str(manifest_realm_artifacts.get("migrationBundleDigest", ""))
        ) is None
        or manifest_realm_artifacts.get("containsSecrets") is not False
    ):
        raise EvidenceError("test-stack manifest is not one complete persistent candidate")
    if (
        deployment.get("schemaVersion") != 3
        or deployment.get("supportSafe") is not True
        or deployment.get("candidateCommit") != candidate
        or deployment.get("sourceCandidateCommit") != source
        or deployment.get("candidateManifestDigest") != manifest_digest
        or not isinstance(deployment_images, dict)
        or set(deployment_images) != set(IMAGE_COMPONENTS)
        or deployment_realm_artifacts != manifest_realm_artifacts
        or not isinstance(deployment.get("deployment"), dict)
        or deployment["deployment"].get("realmArtifactsVerified") is not True
        or manifest.get("evidence", {}).get("deploymentRunUrl") != deployment_url
    ):
        raise EvidenceError("deployment evidence disagrees with the selected persistent manifest")
    if (
        runtime_observation.get("schemaVersion") != "weave.runtime-image-observation.v1"
        or runtime_observation.get("supportSafe") is not True
        or runtime_observation.get("containsSecretValues") is not False
        or runtime_observation.get("sourceCandidateCommit") != source
        or not isinstance(observed_images, dict)
        or set(observed_images) != set(IMAGE_COMPONENTS)
    ):
        raise EvidenceError("fresh runtime image observation is unsafe or incomplete")
    images: dict[str, str] = {}
    for runtime_name, canonical_name in IMAGE_COMPONENTS.items():
        item = manifest_images[runtime_name]
        observed_item = observed_images[runtime_name]
        if (
            not isinstance(item, dict)
            or item.get("matches") is not True
            or item.get("expectedImageId") != deployment_images[runtime_name]
            or item.get("observedImageId") != deployment_images[runtime_name]
            or DIGEST.fullmatch(str(deployment_images[runtime_name])) is None
            or IMMUTABLE_IMAGE.fullmatch(str(item.get("reference", ""))) is None
            or item.get("sourceCommit") != source
            or not isinstance(observed_item, dict)
            or observed_item != item
        ):
            raise EvidenceError(f"{runtime_name} image is not the deployed immutable candidate")
        images[canonical_name] = item["reference"]
    provider_health = validate_health(health, max_age_seconds)
    return {
        "schemaVersion": "weave.dogfood-provider-health-evidence.v2",
        "supportSafe": True,
        "containsSecretValues": False,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source,
        "specCorpusCommit": specification,
        "candidateManifestDigest": manifest_digest,
        "realmArtifacts": dict(manifest_realm_artifacts),
        "images": dict(sorted(images.items())),
        "deploymentRunId": deployment_run_id,
        "deploymentRunUrl": deployment_url,
        "runId": health_run_id,
        "runUrl": normalized_health_url,
        "runtime": {
            "composeProject": runtime["composeProject"],
            "generation": runtime["generation"],
            "imagesVerified": True,
        },
        "providerHealth": provider_health,
        "evidenceRefs": [
            f"{normalized_health_url}#fresh-provider-health",
            f"{deployment_url}#dogfood-deployment",
        ],
        "blockers": [],
    }


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--candidate-commit", required=True)
    result.add_argument("--deployment-run-id", required=True)
    result.add_argument("--health-run-id", required=True)
    result.add_argument("--health-run-url", required=True)
    result.add_argument("--deployment-evidence", type=Path, required=True)
    result.add_argument("--test-stack-manifest", type=Path, required=True)
    result.add_argument("--runtime-image-observation", type=Path, required=True)
    result.add_argument("--provider-health", type=Path, required=True)
    result.add_argument("--max-age-seconds", type=int, default=180)
    result.add_argument("--output", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    if args.max_age_seconds < 1:
        print("dogfood-provider-health-evidence: invalid: max age must be positive", file=sys.stderr)
        return 64
    try:
        evidence = assemble(
            candidate=args.candidate_commit.lower(),
            deployment_run_id=args.deployment_run_id,
            health_run_id=args.health_run_id,
            health_run_url=args.health_run_url,
            deployment=load(args.deployment_evidence, "deployment evidence"),
            manifest=load(args.test_stack_manifest, "test-stack manifest"),
            runtime_observation=load(args.runtime_image_observation, "runtime image observation"),
            health=load(args.provider_health, "provider health"),
            max_age_seconds=args.max_age_seconds,
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except EvidenceError as error:
        print(f"dogfood-provider-health-evidence: invalid: {error}", file=sys.stderr)
        return 2
    print(
        "DOGFOOD_PROVIDER_HEALTH_RESULT status=passed overall=available "
        f"candidate={evidence['candidateCommit']} manifest={evidence['candidateManifestDigest']} supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
