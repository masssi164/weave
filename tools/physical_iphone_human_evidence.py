#!/usr/bin/env python3
"""Validate one human-supplied physical-iPhone protocol against immutable delivery evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
JWT = re.compile(r"[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}")
STEPS = {
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
STATUSES = {"passed", "blocked", "failed", "not_run"}
FORBIDDEN_KEYS = {
    "accesstoken",
    "authorization",
    "clientsecret",
    "email",
    "password",
    "privatekey",
    "refreshtoken",
    "registrationaccesstoken",
    "testeridentity",
}


class EvidenceError(ValueError):
    pass


def load(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise EvidenceError(f"cannot read {label}: {path}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    return value


def safe_reference(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise EvidenceError(f"{label} must be a non-empty evidence reference")
    if re.fullmatch(r"(?:artifact|issue):[0-9A-Za-z][0-9A-Za-z._/-]{0,255}", value):
        return value
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise EvidenceError(f"{label} must be an uncredentialed HTTPS or bounded local reference")
    return value.rstrip("/")


def timestamp(value: object, label: str) -> datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise EvidenceError(f"{label} must be an explicit UTC timestamp")
    try:
        return datetime.fromisoformat(value.removesuffix("Z") + "+00:00")
    except ValueError as error:
        raise EvidenceError(f"{label} is not a valid timestamp") from error


def require_support_safe(value: dict[str, Any], label: str) -> None:
    if value.get("supportSafe") is not True:
        raise EvidenceError(f"{label} must declare supportSafe=true")

    def walk(node: object) -> None:
        if isinstance(node, dict):
            for key, child in node.items():
                normalized = re.sub(r"[^a-z]", "", str(key).lower())
                if normalized in FORBIDDEN_KEYS:
                    raise EvidenceError(f"{label} contains forbidden field {key!r}")
                walk(child)
        elif isinstance(node, list):
            for child in node:
                walk(child)
        elif isinstance(node, str):
            lowered = node.lower()
            if (
                EMAIL.search(node)
                or JWT.search(node)
                or "bearer " in lowered
                or "-----begin " in lowered
                or any(marker in node for marker in ("/Users/", "/home/", "/private/var/"))
            ):
                raise EvidenceError(f"{label} contains private or credential-like material")

    walk(value)


def require_commit(value: object, label: str) -> str:
    if not isinstance(value, str) or COMMIT.fullmatch(value) is None:
        raise EvidenceError(f"{label} must be a full lowercase commit")
    return value


def assemble(
    *,
    submission: dict[str, Any],
    distribution: dict[str, Any],
    candidate: str,
    source: str,
    spec: str,
    manifest_digest: str,
    deployment_run_id: str,
    distribution_run_id: str,
    run_url: str,
    require_passed: bool,
) -> dict[str, Any]:
    require_support_safe(submission, "physical submission")
    require_support_safe(distribution, "distribution evidence")
    candidate = require_commit(candidate, "candidate")
    source = require_commit(source, "source candidate")
    spec = require_commit(spec, "specification commit")
    if not DIGEST.fullmatch(manifest_digest):
        raise EvidenceError("candidate manifest digest is invalid")
    if not deployment_run_id.isdigit() or not distribution_run_id.isdigit():
        raise EvidenceError("delivery run IDs must be numeric")
    if submission.get("schemaVersion") != "weave.physical-iphone-human-submission.v1":
        raise EvidenceError("physical submission has the wrong schema")
    tester_hash = submission.get("testerRefHash")
    if not isinstance(tester_hash, str) or DIGEST.fullmatch(tester_hash) is None:
        raise EvidenceError("physical submission requires a hashed tester reference")
    for field in ("voiceOver", "sessionUpgrade", "navigation"):
        if submission.get(field) not in STATUSES:
            raise EvidenceError(f"physical submission {field} has an invalid status")
    protocol = submission.get("protocol")
    if not isinstance(protocol, dict) or protocol.get("schemaVersion") != 1:
        raise EvidenceError("physical protocol is missing or has the wrong schema")
    for field, expected in (
        ("candidateCommit", candidate),
        ("sourceCandidateCommit", source),
        ("specCorpusCommit", spec),
        ("candidateManifestDigest", manifest_digest),
    ):
        if protocol.get(field) != expected:
            raise EvidenceError(f"physical protocol {field} does not match delivery evidence")
    expected_build = {
        "commit": source,
        "version": str(distribution.get("version", "")),
        "buildNumber": str(distribution.get("buildNumber", "")),
        "bundleId": str(distribution.get("bundleId", "")),
    }
    if protocol.get("build") != expected_build or any(not value for value in expected_build.values()):
        raise EvidenceError("physical protocol build does not match distribution evidence")
    if (
        distribution.get("schemaVersion") != "weave.ios-dogfood-distribution.v2"
        or distribution.get("result") != "success"
        or distribution.get("laneCandidateCommit") != candidate
        or distribution.get("sourceCandidateCommit") != source
        or distribution.get("commit") != source
        or distribution.get("candidateManifestDigest") != manifest_digest
        or str(distribution.get("deploymentRunId")) != deployment_run_id
        or str(distribution.get("githubRunId")) != distribution_run_id
    ):
        raise EvidenceError("distribution evidence is stale or belongs to another candidate")
    started = timestamp(protocol.get("startedAtUtc"), "physical protocol startedAtUtc")
    completed = timestamp(protocol.get("completedAtUtc"), "physical protocol completedAtUtc")
    if completed < started:
        raise EvidenceError("physical protocol completion precedes its start")
    steps = protocol.get("steps")
    if not isinstance(steps, dict) or set(steps) != STEPS:
        raise EvidenceError("physical protocol must contain the exact twenty mandatory steps")
    all_steps_passed = True
    for name in sorted(STEPS):
        step = steps[name]
        if not isinstance(step, dict) or set(step) != {
            "status",
            "expectedOutcome",
            "actualOutcome",
            "observedAtUtc",
            "evidenceRefs",
        }:
            raise EvidenceError(f"physical protocol step {name} is malformed")
        status = step.get("status")
        if status not in STATUSES:
            raise EvidenceError(f"physical protocol step {name} has an invalid status")
        all_steps_passed &= status == "passed"
        for field in ("expectedOutcome", "actualOutcome"):
            if not isinstance(step.get(field), str) or not step[field].strip():
                raise EvidenceError(f"physical protocol step {name} has no {field}")
        observed = timestamp(step.get("observedAtUtc"), f"physical protocol step {name} observedAtUtc")
        if observed < started or observed > completed:
            raise EvidenceError(f"physical protocol step {name} timestamp is outside the human run")
        refs = step.get("evidenceRefs")
        if not isinstance(refs, list) or not refs:
            raise EvidenceError(f"physical protocol step {name} has no evidence reference")
        for index, reference in enumerate(refs):
            safe_reference(reference, f"physical protocol step {name} evidenceRefs[{index}]")
    deviations = protocol.get("deviations")
    if not isinstance(deviations, list) or any(
        not isinstance(value, str) or not value.strip() for value in deviations
    ):
        raise EvidenceError("physical protocol deviations must be a string list")
    passed = (
        submission.get("voiceOver") == "passed"
        and submission.get("sessionUpgrade") == "passed"
        and submission.get("navigation") == "passed"
        and protocol.get("testerConfirmed") is True
        and all_steps_passed
    )
    if require_passed and not passed:
        raise EvidenceError("physical iPhone protocol is incomplete and cannot pass readiness")
    normalized_run_url = safe_reference(run_url, "physical workflow run URL")
    blockers = [] if passed else [{
        "code": "physical-acceptance-incomplete",
        "summary": "The tester-confirmed physical iPhone protocol is incomplete.",
        "candidateCommit": candidate,
    }]
    return {
        "schemaVersion": "weave.physical-iphone-human-evidence.v1",
        "supportSafe": True,
        "candidateCommit": candidate,
        "sourceCandidateCommit": source,
        "specCorpusCommit": spec,
        "candidateManifestDigest": manifest_digest,
        "deploymentRunId": deployment_run_id,
        "distributionRunId": distribution_run_id,
        "deploymentRunUrl": distribution["deploymentRunUrl"],
        "distributionRunUrl": distribution["runUrl"],
        "liveE2eRunUrl": distribution["liveE2eRunUrl"],
        "physicalAcceptance": {
            "status": "passed" if passed else "blocked",
            "physicalIPhone": True,
            "voiceOver": submission["voiceOver"],
            "sessionUpgrade": submission["sessionUpgrade"],
            "navigation": submission["navigation"],
            "testerRefHash": tester_hash,
            "protocol": protocol,
        },
        "physicalEvidenceRef": normalized_run_url,
        "evidenceRefs": [normalized_run_url],
        "blockers": blockers,
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--submission", type=Path, required=True)
    value.add_argument("--distribution-evidence", type=Path, required=True)
    value.add_argument("--candidate-commit", required=True)
    value.add_argument("--source-candidate-commit", required=True)
    value.add_argument("--spec-commit", required=True)
    value.add_argument("--candidate-manifest-digest", required=True)
    value.add_argument("--deployment-run-id", required=True)
    value.add_argument("--distribution-run-id", required=True)
    value.add_argument("--run-url", required=True)
    value.add_argument("--output", type=Path, required=True)
    value.add_argument("--require-passed", action="store_true")
    return value


def main() -> int:
    args = parser().parse_args()
    try:
        result = assemble(
            submission=load(args.submission, "physical submission"),
            distribution=load(args.distribution_evidence, "distribution evidence"),
            candidate=args.candidate_commit,
            source=args.source_candidate_commit,
            spec=args.spec_commit,
            manifest_digest=args.candidate_manifest_digest,
            deployment_run_id=args.deployment_run_id,
            distribution_run_id=args.distribution_run_id,
            run_url=args.run_url,
            require_passed=args.require_passed,
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(
            "PHYSICAL_IPHONE_HUMAN_EVIDENCE_RESULT "
            f"status={result['physicalAcceptance']['status']} supportSafe=true"
        )
        return 0
    except EvidenceError as error:
        print(f"physical-iphone-human-evidence: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
