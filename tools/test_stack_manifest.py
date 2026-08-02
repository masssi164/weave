#!/usr/bin/env python3
"""Assemble one support-safe manifest for the exact persistent dogfood runtime."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
COMPONENTS = {
    "backend": "server",
    "identity-ops": "identity-ops",
    "keycloak": "keycloak-runtime",
    "mcp": "mcp-server",
}


class ManifestError(ValueError):
    pass


def load(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ManifestError(f"cannot read {label}: {path}") from error
    if not isinstance(value, dict):
        raise ManifestError(f"{label} must be an object")
    return value


def safe_url(value: str) -> str:
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise ManifestError("evidence URLs must be uncredentialed HTTPS references")
    return value.rstrip("/")


def assemble(
    *,
    mapping: dict[str, Any],
    candidate: dict[str, Any],
    runtime: dict[str, Any],
    cut: dict[str, Any],
    idempotence: dict[str, Any],
    health: dict[str, Any],
    compose_project: str,
    generation: str,
    live_run_url: str,
    deployment_run_url: str,
) -> dict[str, Any]:
    lane = mapping.get("laneCandidateCommit")
    source = mapping.get("sourceCandidateCommit")
    mapped_images = mapping.get("images")
    if (
        mapping.get("schemaVersion") != "weave.candidate-source-mapping.v1"
        or mapping.get("status") != "passed"
        or mapping.get("sourceTree") != mapping.get("laneTree")
        or mapping.get("supportSafe") is not True
        or mapping.get("containsSecretValues") is not False
        or not isinstance(lane, str)
        or not COMMIT.fullmatch(lane)
        or not isinstance(source, str)
        or not COMMIT.fullmatch(source)
        or not isinstance(mapped_images, dict)
        or set(mapped_images) != set(COMPONENTS)
    ):
        raise ManifestError("candidate source mapping is unsafe or incomplete")
    manifest_raw = json.dumps(candidate, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    manifest_digest = "sha256:" + hashlib.sha256(manifest_raw).hexdigest()
    candidate_images = candidate.get("images")
    if (
        candidate.get("schemaVersion") != "weave.release.candidate-manifest.v2"
        or candidate.get("supportSafe") is not True
        or candidate.get("commit") != source
        or not COMMIT.fullmatch(str(candidate.get("specificationCommit", "")))
        or not isinstance(candidate_images, list)
    ):
        raise ManifestError("candidate manifest is unsafe or belongs to another source")
    by_component = {
        item.get("component"): item
        for item in candidate_images
        if isinstance(item, dict)
    }
    if set(by_component) != set(COMPONENTS.values()):
        raise ManifestError("candidate manifest does not contain the exact four runtime images")
    observed = runtime.get("images")
    if (
        runtime.get("schemaVersion") != "weave.runtime-image-observation.v1"
        or runtime.get("sourceCandidateCommit") != source
        or runtime.get("supportSafe") is not True
        or runtime.get("containsSecretValues") is not False
        or not isinstance(observed, dict)
        or set(observed) != set(COMPONENTS)
    ):
        raise ManifestError("runtime image observation is unsafe or incomplete")
    images: dict[str, dict[str, Any]] = {}
    for runtime_name, manifest_name in sorted(COMPONENTS.items()):
        item = observed[runtime_name]
        expected_id = mapped_images[runtime_name]
        manifest_image = by_component[manifest_name]
        if (
            not isinstance(item, dict)
            or not DIGEST.fullmatch(str(expected_id))
            or item.get("expectedImageId") != expected_id
            or item.get("observedImageId") != expected_id
            or item.get("reference") != manifest_image.get("reference")
            or item.get("sourceCommit") != source
            or item.get("matches") is not True
            or not DIGEST.fullmatch(str(item.get("observedImageId", "")))
        ):
            raise ManifestError(f"running {runtime_name} image differs from the candidate manifest")
        images[runtime_name] = {
            "reference": item["reference"],
            "expectedImageId": expected_id,
            "observedImageId": item["observedImageId"],
            "sourceCommit": source,
            "matches": True,
        }
    if (
        cut.get("schemaVersion") != "weave.fresh-start-cut-report.v1"
        or cut.get("laneCandidateCommit") != lane
        or cut.get("sourceCandidateCommit") != source
        or cut.get("candidateManifestDigest") != manifest_digest
        or cut.get("status") != "passed"
        or cut.get("legacyStateMigrated") is not False
        or cut.get("adoptionAuthorized") is not False
        or cut.get("supportSafe") is not True
        or cut.get("containsSecretValues") is not False
    ):
        raise ManifestError("Fresh Start cut report is unsafe, stale, or incomplete")
    if (
        idempotence.get("schemaVersion") != "weave.persistent-test-idempotence.v2"
        or idempotence.get("deploymentContext") != "persistent-dogfood"
        or idempotence.get("noChanges") is not True
        or idempotence.get("composeModelStable") is not True
        or idempotence.get("identitySecondPlanEmpty") is not True
        or idempotence.get("supportSafe") is not True
        or idempotence.get("containsSecretValues") is not False
    ):
        raise ManifestError("persistent dogfood convergence evidence is incomplete")
    if (
        health.get("schemaVersion") != "weave.provider-health-metrics-summary.v1"
        or health.get("overall") != "available"
        or health.get("supportSafe") is not True
        or health.get("providerProbeTriggered") is not False
        or health.get("rawMetricPayloadIncluded") is not False
    ):
        raise ManifestError("provider health evidence is not available and support-safe")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{2,63}", compose_project):
        raise ManifestError("compose project is malformed")
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,63}", generation):
        raise ManifestError("resource generation is malformed")
    return {
        "schemaVersion": "weave.test-stack-manifest.v2",
        "supportSafe": True,
        "containsSecretValues": False,
        "branch": "dogfood",
        "laneCandidateCommit": lane,
        "sourceCandidateCommit": source,
        "specificationCommit": candidate["specificationCommit"],
        "candidateManifestDigest": manifest_digest,
        "images": images,
        "runtime": {
            "environment": "persistent-dogfood",
            "scope": "persistent",
            "composeProject": compose_project,
            "generation": generation,
        },
        "deployment": {
            "freshStartStatus": "passed",
            "composeModelStable": True,
            "identitySecondPlanEmpty": True,
            "providerHealth": "available",
            "legacyStateMigrated": False,
            "adoptionAuthorized": False,
        },
        "evidence": {
            "liveE2eRunUrl": safe_url(live_run_url),
            "deploymentRunUrl": safe_url(deployment_run_url),
        },
    }


def write(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--candidate-source-mapping", type=Path, required=True)
    result.add_argument("--candidate-manifest", type=Path, required=True)
    result.add_argument("--runtime-image-evidence", type=Path, required=True)
    result.add_argument("--fresh-start-cut-report", type=Path, required=True)
    result.add_argument("--deployment-idempotence", type=Path, required=True)
    result.add_argument("--provider-health", type=Path, required=True)
    result.add_argument("--compose-project", required=True)
    result.add_argument("--generation", required=True)
    result.add_argument("--live-run-url", required=True)
    result.add_argument("--deployment-run-url", required=True)
    result.add_argument("--output", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        value = assemble(
            mapping=load(args.candidate_source_mapping, "candidate source mapping"),
            candidate=load(args.candidate_manifest, "candidate manifest"),
            runtime=load(args.runtime_image_evidence, "runtime image evidence"),
            cut=load(args.fresh_start_cut_report, "Fresh Start cut report"),
            idempotence=load(args.deployment_idempotence, "deployment idempotence"),
            health=load(args.provider_health, "provider health"),
            compose_project=args.compose_project,
            generation=args.generation,
            live_run_url=args.live_run_url,
            deployment_run_url=args.deployment_run_url,
        )
        write(args.output, value)
    except ManifestError as error:
        print(f"test-stack-manifest: invalid: {error}")
        return 2
    print(
        "TEST_STACK_MANIFEST_RESULT status=passed "
        f"lane={value['laneCandidateCommit']} source={value['sourceCandidateCommit']} supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
