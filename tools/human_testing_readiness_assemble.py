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
    observed = str(evidence.get("candidateCommit", evidence.get("commit", ""))).lower()
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


def assemble(
    *,
    candidate: str,
    automated: dict[str, Any],
    deployment: dict[str, Any],
    distribution: dict[str, Any],
    physical: dict[str, Any],
) -> dict[str, Any]:
    for label, document in (
        ("automated", automated),
        ("deployment", deployment),
        ("distribution", distribution),
        ("physical", physical),
    ):
        require_candidate(document, candidate, label)

    spec_commit = automated.get("specCorpusCommit")
    if not isinstance(spec_commit, str) or not COMMIT_PATTERN.fullmatch(spec_commit):
        raise ManifestError("automated.specCorpusCommit must be a full lowercase commit")

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
        "commit": candidate,
        "version": str(distribution.get("version", "")),
        "buildNumber": str(distribution.get("buildNumber", "")),
        "bundleId": str(distribution.get("bundleId", "")),
    }
    physical_acceptance = require_object(physical, "physicalAcceptance", "physical")
    blockers: list[Any] = []
    for document in (automated, deployment, distribution, physical):
        values = document.get("blockers", [])
        if isinstance(values, list):
            blockers.extend(values)

    return {
        "schemaVersion": 1,
        "candidateCommit": candidate,
        "specCorpusCommit": spec_commit,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "state": "blocked",
        "humanTestingReady": False,
        "builds": {
            "backend": require_object(deployment, "backendBuild", "deployment"),
            "client": client_build,
        },
        "surfaces": require_object(automated, "surfaces", "automated"),
        "collaboration": require_object(automated, "collaboration", "automated"),
        "deployment": require_object(deployment, "deployment", "deployment"),
        "providerHealth": require_object(deployment, "providerHealth", "deployment"),
        "distribution": {
            "status": distribution_status,
            "channel": str(distribution.get("channel", "none")),
            "buildNumber": str(distribution.get("buildNumber", "")),
        },
        "physicalAcceptance": physical_acceptance,
        "blockers": blockers,
        "evidence": evidence_refs(automated, deployment, distribution, physical),
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--candidate-commit", required=True)
    value.add_argument("--automated-evidence", type=Path, required=True)
    value.add_argument("--deployment-evidence", type=Path, required=True)
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
        distribution = load_object(args.distribution_evidence, "distribution")
        physical = load_object(args.physical_evidence, "physical")
        manifest = assemble(
            candidate=candidate,
            automated=automated,
            deployment=deployment,
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
