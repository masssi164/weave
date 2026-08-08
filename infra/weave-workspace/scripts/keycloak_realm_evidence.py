#!/usr/bin/env python3
"""Finalize candidate-bound Keycloak realm evidence after semantic provider readback."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
RECEIPT_SCHEMA = "weave.keycloak-fgap-migration-receipt/v1"
OPERATION_ID = "fgap-v2-primary-organization-post-import"


class EvidenceError(ValueError):
    pass


def load(path: Path, label: str) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise EvidenceError(f"{label} must be a regular non-symlink file")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise EvidenceError(f"cannot read {label}: {path}") from error
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    return value


def digest(value: dict[str, Any]) -> str:
    payload = json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def identity(manifest: dict[str, Any], label: str) -> dict[str, Any]:
    value = manifest.get("realmIdentity")
    if (
        manifest.get("schemaVersion") != "weave.compose-render.v2"
        or manifest.get("containsSecretValues") is not False
        or not isinstance(value, dict)
        or set(value)
        != {
            "semanticRealmSourceDigest",
            "migrationDefinitionDigest",
            "overlayDigest",
            "renderedRealmDigest",
        }
        or any(not DIGEST.fullmatch(str(value.get(key, ""))) for key in value)
    ):
        raise EvidenceError(f"{label} does not contain one exact realm identity")
    artifacts = manifest.get("realmArtifacts")
    if (
        not isinstance(artifacts, dict)
        or artifacts.get("renderedRealmPath") != "keycloak/import/weave-realm.json"
        or artifacts.get("migrationBundlePath") != "keycloak/migrations/fresh-start-v1.json"
        or artifacts.get("environmentRenderEvidencePath")
        != "keycloak/realm-render-evidence.json"
        or artifacts.get("containsSecretValues") is not False
    ):
        raise EvidenceError(f"{label} realm render artifacts are incomplete")
    return value


def finalize(
    candidate: dict[str, Any],
    first_render: dict[str, Any],
    current_render: dict[str, Any],
    render_evidence: dict[str, Any],
    receipt: dict[str, Any],
) -> dict[str, Any]:
    definition = candidate.get("realmDefinition")
    if (
        candidate.get("schemaVersion") != "weave.release.candidate-manifest.v4"
        or candidate.get("supportSafe") is not True
        or not COMMIT.fullmatch(str(candidate.get("commit", "")))
        or not isinstance(definition, dict)
        or set(definition)
        != {
            "semanticRealmSourceDigest",
            "migrationDefinitionDigest",
            "containsSecrets",
        }
        or not DIGEST.fullmatch(str(definition.get("semanticRealmSourceDigest", "")))
        or not DIGEST.fullmatch(str(definition.get("migrationDefinitionDigest", "")))
        or definition.get("containsSecrets") is not False
    ):
        raise EvidenceError("candidate manifest does not contain one semantic realm definition")
    first_identity = identity(first_render, "first render manifest")
    current_identity = identity(current_render, "current render manifest")
    if first_identity != current_identity:
        raise EvidenceError("same-environment realm render changed between convergent installs")
    if (
        current_identity["semanticRealmSourceDigest"]
        != definition["semanticRealmSourceDigest"]
        or current_identity["migrationDefinitionDigest"]
        != definition["migrationDefinitionDigest"]
    ):
        raise EvidenceError("environment render does not derive from the candidate realm definition")
    if (
        render_evidence.get("schemaVersion")
        != "weave.keycloak-environment-render-evidence/v1"
        or render_evidence.get("supportSafe") is not True
        or render_evidence.get("containsSecretValues") is not False
        or render_evidence.get("candidateCommit") != candidate["commit"]
        or render_evidence.get("realmIdentity") != current_identity
        or render_evidence.get("semanticReadbackDigest") is not None
        or render_evidence.get("semanticReadbackVerified") is not False
    ):
        raise EvidenceError("pre-runtime environment render evidence is stale or overclaims readback")
    if (
        receipt.get("schemaVersion") != RECEIPT_SCHEMA
        or receipt.get("status") != "complete"
        or receipt.get("operationId") != OPERATION_ID
        or receipt.get("semanticReadbackVerified") is not True
        or receipt.get("secondRunPlanEmpty") is not True
        or receipt.get("bootstrapAuthorityDeleted") is not True
        or receipt.get("bootstrapAuthorityNegativeReadbackVerified") is not True
        or receipt.get("supportSafe") is not True
        or receipt.get("containsSecretValues") is not False
        or receipt.get("baselineArtifactDigest")
        != current_identity["renderedRealmDigest"]
    ):
        raise EvidenceError("migration receipt does not prove semantic provider convergence")
    readback_claim = {
        "schemaVersion": "weave.keycloak-semantic-readback/v1",
        "semanticRealmSourceDigest": current_identity["semanticRealmSourceDigest"],
        "migrationDefinitionDigest": current_identity["migrationDefinitionDigest"],
        "overlayDigest": current_identity["overlayDigest"],
        "renderedRealmDigest": current_identity["renderedRealmDigest"],
        "targetBaselineRevision": receipt.get("targetBaselineRevision"),
        "operationId": OPERATION_ID,
        "firstRunOperations": receipt.get("firstRunOperations"),
        "firstRunMutationCount": receipt.get("firstRunMutationCount"),
        "secondRunPlanEmpty": True,
        "bootstrapAuthorityDeleted": True,
        "bootstrapAuthorityNegativeReadbackVerified": True,
        "semanticReadbackVerified": True,
    }
    return {
        "semanticRealmSourceDigest": current_identity["semanticRealmSourceDigest"],
        "migrationDefinitionDigest": current_identity["migrationDefinitionDigest"],
        "overlayDigest": current_identity["overlayDigest"],
        "renderedRealmDigest": current_identity["renderedRealmDigest"],
        "semanticReadbackDigest": digest(readback_claim),
        "candidateRealmDefinitionMatched": True,
        "environmentRealmRenderStable": True,
        "semanticReadbackVerified": True,
        "containsSecrets": False,
    }


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--candidate-manifest", type=Path, required=True)
    value.add_argument("--first-render-manifest", type=Path, required=True)
    value.add_argument("--current-render-manifest", type=Path, required=True)
    value.add_argument("--render-evidence", type=Path, required=True)
    value.add_argument("--migration-receipt", type=Path, required=True)
    value.add_argument("--output", type=Path, required=True)
    return value


def main() -> int:
    args = parser().parse_args()
    try:
        evidence = finalize(
            load(args.candidate_manifest, "candidate manifest"),
            load(args.first_render_manifest, "first render manifest"),
            load(args.current_render_manifest, "current render manifest"),
            load(args.render_evidence, "render evidence"),
            load(args.migration_receipt, "migration receipt"),
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except EvidenceError as error:
        print(f"keycloak-realm-evidence: invalid: {error}", file=sys.stderr)
        return 2
    print(
        "KEYCLOAK_REALM_EVIDENCE_RESULT status=passed "
        f"semanticReadbackDigest={evidence['semanticReadbackDigest']} supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
