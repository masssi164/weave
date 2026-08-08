#!/usr/bin/env python3
"""Assemble one readiness manifest from ordered candidate-lane evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from human_testing_readiness_manifest import ManifestError, evaluate_manifest


COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
DIGEST_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
IMMUTABLE_IMAGE_PATTERN = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
AUTOMATED_IMAGE_COMPONENTS = {"server", "mcp-server", "keycloak-runtime"}
DEPLOYMENT_IMAGE_COMPONENTS = {"backend", "mcp", "keycloak"}
REALM_EVIDENCE_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
    "candidateRealmDefinitionMatched",
    "environmentRealmRenderStable",
    "semanticReadbackVerified",
    "containsSecrets",
}
REALM_EVIDENCE_DIGEST_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
}


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ManifestError(f"missing {label} evidence: {path}") from error
    except json.JSONDecodeError as error:
        raise ManifestError(f"invalid {label} evidence JSON: {error}") from error
    if not isinstance(value, dict):
        raise ManifestError(f"{label} evidence must be an object")
    if value.get("supportSafe") is not True:
        raise ManifestError(f"{label} evidence must declare supportSafe=true")
    return value


def require_candidate(evidence: dict[str, Any], candidate: str, label: str) -> None:
    observed = str(evidence.get("candidateCommit", "")).lower()
    if observed != candidate:
        raise ManifestError(f"{label} evidence targets {observed or 'no commit'}, expected {candidate}")


def require_object(container: dict[str, Any], key: str, label: str) -> dict[str, Any]:
    value = container.get(key)
    if not isinstance(value, dict):
        raise ManifestError(f"{label}.{key} must be an object")
    return value


def evidence_refs(*documents: dict[str, Any]) -> list[str]:
    refs: list[str] = []
    for document in documents:
        values = document.get("evidenceRefs", [])
        if isinstance(values, list):
            refs.extend(str(value) for value in values if isinstance(value, str) and value.strip())
        for key in ("runUrl", "deploymentRunUrl", "liveE2eRunUrl", "physicalEvidenceRef"):
            value = document.get(key)
            if isinstance(value, str) and value.strip():
                refs.append(value)
    return list(dict.fromkeys(refs))


def require_automated_origins(automated: dict[str, Any]) -> dict[str, Any]:
    if automated.get("evidenceModes") != ["live-provider-backed", "fixture-ui"]:
        raise ManifestError("automated evidence must retain both live-provider-backed and fixture-ui modes")
    surfaces = require_object(automated, "surfaces", "automated")
    live_required = {"authenticationSession", "home", "chat", "files", "calendar", "profile"}
    fixture_required = {"home", "chat", "files", "calendar", "settings", "profile"}
    if set(surfaces) != live_required | fixture_required:
        raise ManifestError("automated surfaces are incomplete")
    for name, value in surfaces.items():
        if not isinstance(value, dict) or value.get("status") != "passed":
            raise ManifestError(f"automated surface {name} did not pass")
        proof_kinds = value.get("proofKinds")
        if not isinstance(proof_kinds, list) or any(not isinstance(item, str) for item in proof_kinds):
            raise ManifestError(f"automated surface {name} lost proofKinds")
        if name in live_required and "live-provider-backed" not in proof_kinds:
            raise ManifestError(f"automated surface {name} lost live-provider-backed proof")
        if name in fixture_required and "fixture-ui" not in proof_kinds:
            raise ManifestError(f"automated surface {name} lost fixture-ui proof")
    return surfaces


def require_realm_evidence(document: dict[str, Any], label: str) -> dict[str, Any]:
    evidence = document.get("realmEvidence")
    if not isinstance(evidence, dict) or set(evidence) != REALM_EVIDENCE_FIELDS:
        raise ManifestError(f"{label} realm evidence is incomplete")
    if any(
        DIGEST_PATTERN.fullmatch(str(evidence.get(field))) is None
        for field in REALM_EVIDENCE_DIGEST_FIELDS
    ):
        raise ManifestError(f"{label} realm evidence contains a malformed digest")
    if any(
        evidence.get(field) is not True
        for field in (
            "candidateRealmDefinitionMatched",
            "environmentRealmRenderStable",
            "semanticReadbackVerified",
        )
    ):
        raise ManifestError(f"{label} realm evidence is not fully verified")
    if evidence.get("containsSecrets") is not False:
        raise ManifestError(f"{label} realm evidence must be secret-free")
    return evidence


def assemble(
    *,
    candidate: str,
    automated: dict[str, Any],
    deployment: dict[str, Any],
    provider_health: dict[str, Any],
    distribution: dict[str, Any],
    physical: dict[str, Any],
) -> dict[str, Any]:
    for label, document in (
        ("automated", automated),
        ("deployment", deployment),
        ("provider health", provider_health),
        ("physical", physical),
    ):
        require_candidate(document, candidate, label)

    distribution_lane = str(distribution.get("laneCandidateCommit", "")).lower()
    if distribution_lane != candidate:
        raise ManifestError(
            f"distribution evidence targets {distribution_lane or 'no lane commit'}, expected {candidate}"
        )
    source_candidate = str(deployment.get("sourceCandidateCommit", "")).lower()
    if not COMMIT_PATTERN.fullmatch(source_candidate):
        raise ManifestError("deployment.sourceCandidateCommit must be a full lowercase commit")
    for label, document in (
        ("automated", automated),
        ("provider health", provider_health),
        ("distribution", distribution),
    ):
        observed_source = str(document.get("sourceCandidateCommit", "")).lower()
        if observed_source != source_candidate:
            raise ManifestError(
                f"{label} source candidate targets {observed_source or 'no commit'}, expected {source_candidate}"
            )
    if str(distribution.get("commit", "")).lower() != source_candidate:
        raise ManifestError("distribution build commit does not match source candidate")

    automated_manifest = str(automated.get("candidateManifestDigest", ""))
    deployment_manifest = str(deployment.get("candidateManifestDigest", ""))
    distribution_manifest = str(distribution.get("candidateManifestDigest", ""))
    health_manifest = str(provider_health.get("candidateManifestDigest", ""))
    if (
        DIGEST_PATTERN.fullmatch(automated_manifest) is None
        or automated_manifest != deployment_manifest
        or automated_manifest != health_manifest
        or automated_manifest != distribution_manifest
    ):
        raise ManifestError("automated, deployment, provider health, and distribution evidence disagree on candidate manifest")
    automated_images = automated.get("images")
    deployment_images = deployment.get("candidateImages")
    health_images = provider_health.get("images")
    if (
        not isinstance(automated_images, dict)
        or set(automated_images) != AUTOMATED_IMAGE_COMPONENTS
        or any(
            not isinstance(reference, str)
            or IMMUTABLE_IMAGE_PATTERN.fullmatch(reference) is None
            for reference in automated_images.values()
        )
        or not isinstance(deployment_images, dict)
        or set(deployment_images) != DEPLOYMENT_IMAGE_COMPONENTS
        or any(DIGEST_PATTERN.fullmatch(str(image_id)) is None for image_id in deployment_images.values())
        or health_images != automated_images
    ):
        raise ManifestError("candidate image evidence is incomplete or mutable")

    realm_evidence = require_realm_evidence(automated, "automated")
    deployment_realm_evidence = require_realm_evidence(deployment, "deployment")
    health_realm_evidence = require_realm_evidence(provider_health, "provider health")
    if deployment_realm_evidence != realm_evidence:
        raise ManifestError("deployment realm evidence disagrees with automated evidence")
    if health_realm_evidence != realm_evidence:
        raise ManifestError("provider health realm evidence disagrees with automated evidence")

    deployment_details = require_object(deployment, "deployment", "deployment")
    if deployment_details.get("realmEvidenceVerified") is not True:
        raise ManifestError("deployment did not verify semantic realm and environment render evidence")
    manifest_deployment = dict(deployment_details)
    manifest_deployment.pop("realmEvidenceVerified")
    if distribution.get("deploymentRunUrl") != deployment.get("runUrl"):
        raise ManifestError("distribution is not bound to the selected deployment run")
    if distribution.get("liveE2eRunUrl") != automated.get("liveE2eRunUrl"):
        raise ManifestError("distribution is not bound to the selected isolated live run")
    if provider_health.get("deploymentRunUrl") != deployment.get("runUrl"):
        raise ManifestError("fresh provider health is not bound to the selected deployment run")

    spec_commit = automated.get("specCorpusCommit")
    if not isinstance(spec_commit, str) or not COMMIT_PATTERN.fullmatch(spec_commit):
        raise ManifestError("automated.specCorpusCommit must be a full lowercase commit")
    if provider_health.get("specCorpusCommit") != spec_commit:
        raise ManifestError("fresh provider health targets another specification commit")

    distribution_result = str(distribution.get("result", distribution.get("status", "not_run")))
    distribution_status = {
        "success": "passed",
        "passed": "passed",
        "failure": "failed",
        "failed": "failed",
        "cancelled": "blocked",
        "skipped": "blocked",
        "blocked": "blocked",
    }.get(distribution_result, "not_run")
    client_build = {
        "commit": source_candidate,
        "version": str(distribution.get("version", "")),
        "buildNumber": str(distribution.get("buildNumber", "")),
        "bundleId": str(distribution.get("bundleId", "")),
    }
    physical_acceptance = require_object(physical, "physicalAcceptance", "physical")
    blockers: list[Any] = []
    for document in (automated, deployment, provider_health, distribution, physical):
        values = document.get("blockers", [])
        if isinstance(values, list):
            blockers.extend(values)

    surfaces = require_automated_origins(automated)
    return {
        "schemaVersion": 5,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source_candidate,
        "specCorpusCommit": spec_commit,
        "candidateManifestDigest": automated_manifest,
        "images": dict(sorted(automated_images.items())),
        "realmEvidence": dict(realm_evidence),
        "evidenceModes": automated["evidenceModes"],
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "state": "blocked",
        "humanTestingReady": False,
        "builds": {
            "backend": require_object(deployment, "backendBuild", "deployment"),
            "client": client_build,
        },
        "surfaces": surfaces,
        "collaboration": require_object(automated, "collaboration", "automated"),
        "deployment": manifest_deployment,
        "providerHealth": require_object(provider_health, "providerHealth", "provider health"),
        "distribution": {
            "status": distribution_status,
            "channel": str(distribution.get("channel", "none")),
            "buildNumber": str(distribution.get("buildNumber", "")),
        },
        "physicalAcceptance": physical_acceptance,
        "blockers": blockers,
        "evidence": evidence_refs(automated, deployment, provider_health, distribution, physical),
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--candidate-commit", required=True)
    value.add_argument("--automated-evidence", type=Path, required=True)
    value.add_argument("--deployment-evidence", type=Path, required=True)
    value.add_argument("--provider-health-evidence", type=Path, required=True)
    value.add_argument("--distribution-evidence", type=Path, required=True)
    value.add_argument("--physical-evidence", type=Path, required=True)
    value.add_argument("--output", type=Path, required=True)
    value.add_argument("--max-provider-age-seconds", type=int, default=180)
    value.add_argument("--require-ready", action="store_true")
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    candidate = args.candidate_commit.lower()
    if not COMMIT_PATTERN.fullmatch(candidate):
        print("candidate commit must be a full lowercase SHA-1", file=sys.stderr)
        return 64
    try:
        automated = load_object(args.automated_evidence, "automated")
        deployment = load_object(args.deployment_evidence, "deployment")
        provider_health = load_object(args.provider_health_evidence, "provider health")
        distribution = load_object(args.distribution_evidence, "distribution")
        physical = load_object(args.physical_evidence, "physical")
        manifest = assemble(
            candidate=candidate,
            automated=automated,
            deployment=deployment,
            provider_health=provider_health,
            distribution=distribution,
            physical=physical,
        )
        evaluation = evaluate_manifest(
            manifest,
            max_provider_age_seconds=args.max_provider_age_seconds,
        )
        manifest["state"] = evaluation.state
        manifest["humanTestingReady"] = evaluation.human_testing_ready
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        physical_acceptance = manifest["physicalAcceptance"]
        print(
            "PHYSICAL_IPHONE_VOICEOVER_RESULT "
            f"status={physical_acceptance.get('status', 'not_run')} "
            f"physicalIPhone={str(physical_acceptance.get('physicalIPhone') is True).lower()} "
            f"voiceOver={physical_acceptance.get('voiceOver', 'not_run')}"
        )
        print(
            "HUMAN_TESTING_READINESS_RESULT "
            f"state={evaluation.state} "
            f"humanTestingReady={str(evaluation.human_testing_ready).lower()}"
        )
        for reason in evaluation.failed_reasons:
            print(f"failed: {reason}")
        for reason in evaluation.blocked_reasons:
            print(f"blocked: {reason}")
        if args.require_ready and not evaluation.human_testing_ready:
            return 1
        return 0
    except ManifestError as error:
        print(f"human-testing-readiness-assemble: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
