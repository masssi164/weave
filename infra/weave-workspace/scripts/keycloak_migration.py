#!/usr/bin/env python3
"""Validate the exact, support-safe Keycloak post-import migration receipt."""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from compose_env import ComposeContext, ContractError


SHA256 = re.compile(r"^sha256:[0-9a-f]{64}$")
OPERATION_ID = "fgap-v2-primary-organization-post-import"
RECEIPT_NAME = f"{OPERATION_ID}.receipt.json"
RECEIPT_SCHEMA = "weave.keycloak-fgap-migration-receipt/v1"
ALLOWED_MUTATIONS = frozenset(
    {
        "create-identity-admin-subject-policy",
        "update-identity-admin-subject-policy",
        "create-primary-organization-permission",
        "update-primary-organization-permission",
        "create-users-lifecycle-permission",
        "update-users-lifecycle-permission",
    }
)
MAX_ARTIFACT_BYTES = 1024 * 1024


@dataclass(frozen=True)
class MigrationInputs:
    artifact_root: Path
    manifest_digest: str
    bundle_digest: str
    baseline_digest: str
    target_revision: str
    receipt_file: Path
    backup_proof_file: Path


def _artifact(path: Path) -> tuple[dict[str, Any], bytes]:
    if path.is_symlink() or not path.is_file():
        raise ContractError(f"Keycloak migration artifact is missing or unsafe: {path.name}")
    if path.stat().st_size > MAX_ARTIFACT_BYTES:
        raise ContractError(f"Keycloak migration artifact exceeds its size bound: {path.name}")
    payload = path.read_bytes()
    try:
        value = json.loads(payload)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ContractError(f"Keycloak migration artifact is malformed: {path.name}") from error
    if not isinstance(value, dict):
        raise ContractError(f"Keycloak migration artifact must be an object: {path.name}")
    return value, payload


def _digest(payload: bytes) -> str:
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def migration_inputs(context: ComposeContext) -> MigrationInputs:
    root = context.generated_root
    migration_root = root / "keycloak/migrations"
    manifest_file = migration_root / "manifest.json"
    bundle_file = migration_root / "fresh-start-v1.json"
    render_file = root / "render-manifest.json"
    manifest, manifest_payload = _artifact(manifest_file)
    bundle, bundle_payload = _artifact(bundle_file)
    rendered, _ = _artifact(render_file)

    bundle_digest = _digest(bundle_payload)
    manifest_digest = _digest(manifest_payload)
    baseline_digest = manifest.get("baselineArtifactDigest")
    target_revision = bundle.get("toBaselineRevision")
    if (
        manifest.get("schemaVersion")
        != "weave.keycloak-realm-migration-manifest/v1"
        or manifest.get("containsSecretValues") is not False
        or not isinstance(baseline_digest, str)
        or not SHA256.fullmatch(baseline_digest)
        or manifest.get("bundles")
        != [{"digest": bundle_digest, "path": "keycloak/migrations/fresh-start-v1.json"}]
    ):
        raise ContractError("Keycloak migration manifest does not bind the rendered bundle")
    expected_operation = {
        "blockedBy": "keycloak-26.7-imports-client-authorization-before-organizations",
        "desiredStateDigest": bundle.get("operations", [{}])[0].get("desiredStateDigest")
        if isinstance(bundle.get("operations"), list) and bundle.get("operations")
        else None,
        "desiredStatePointer": "/fineGrainedAdminPermissions",
        "id": OPERATION_ID,
        "phase": "post-realm-import",
        "status": "requires-qualified-admin-rest-executor",
        "type": "keycloak-fgap-v2",
    }
    if (
        bundle.get("apiVersion") != "weave.keycloak-realm-migration-bundle/v1"
        or bundle.get("applicability") != "after-fresh-start-realm-import"
        or bundle.get("baselineArtifactDigest") != baseline_digest
        or bundle.get("containsSecretValues") is not False
        or bundle.get("fromBaselineRevision") is not None
        or bundle.get("keycloakVersion") != "26.7.0"
        or bundle.get("operations") != [expected_operation]
        or not SHA256.fullmatch(str(expected_operation["desiredStateDigest"]))
        or bundle.get("status") != "blocked-post-import-operation"
        or not isinstance(target_revision, str)
        or not SHA256.fullmatch(target_revision)
    ):
        raise ContractError("Keycloak migration bundle is not the qualified blocked operation")
    realm_artifacts = rendered.get("realmArtifacts")
    if (
        rendered.get("schemaVersion") != "weave.compose-render.v1"
        or rendered.get("containsSecretValues") is not False
        or rendered.get("baselineRevision") != target_revision
        or not isinstance(realm_artifacts, dict)
        or realm_artifacts.get("baselineDigest") != baseline_digest
        or realm_artifacts.get("migrationBundleDigest") != bundle_digest
        or realm_artifacts.get("migrationBundlePath")
        != "keycloak/migrations/fresh-start-v1.json"
        or realm_artifacts.get("containsSecretValues") is not False
    ):
        raise ContractError("render manifest does not bind the Keycloak migration artifacts")
    return MigrationInputs(
        artifact_root=root,
        manifest_digest=manifest_digest,
        bundle_digest=bundle_digest,
        baseline_digest=baseline_digest,
        target_revision=target_revision,
        receipt_file=migration_root / RECEIPT_NAME,
        backup_proof_file=migration_root
        / "fgap-v2-primary-organization-post-import.backup-proof.json",
    )


def require_completed_migration(context: ComposeContext) -> MigrationInputs:
    if context.environment not in {"dogfood", "prod"}:
        raise ContractError(
            "the qualified Keycloak FGAP migration is limited to dogfood/prod; "
            "this environment remains fail-closed"
        )
    inputs = migration_inputs(context)
    proof, proof_payload = _artifact(inputs.backup_proof_file)
    if inputs.backup_proof_file.stat().st_mode & 0o777 not in {0o400, 0o600}:
        raise ContractError("Keycloak migration backup proof must be mode 0400 or 0600")
    created_at = proof.get("createdAt")
    try:
        parsed_created_at = datetime.fromisoformat(str(created_at).replace("Z", "+00:00"))
    except ValueError as error:
        raise ContractError("Keycloak migration backup proof timestamp is malformed") from error
    if (
        set(proof)
        != {
            "schemaVersion",
            "supportSafe",
            "status",
            "createdAt",
            "environment",
            "realm",
            "sourceBaselineRevision",
            "backupManifestSha256",
            "backupIdSha256",
            "candidateCommit",
            "composeProject",
        }
        or proof.get("schemaVersion")
        != "weave.keycloak-realm-migration-backup-proof/v1"
        or proof.get("supportSafe") is not True
        or proof.get("status") != "verified"
        or parsed_created_at.tzinfo is None
        or proof.get("environment") != context.environment
        or proof.get("realm") != "weave"
        or proof.get("sourceBaselineRevision") != inputs.target_revision
        or not SHA256.fullmatch(str(proof.get("backupManifestSha256")))
        or not SHA256.fullmatch(str(proof.get("backupIdSha256")))
        or proof.get("candidateCommit") != context.env["WEAVE_CANDIDATE_COMMIT"]
        or proof.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
    ):
        raise ContractError("Keycloak migration backup proof is stale or outside deployment scope")
    backup_proof_digest = _digest(proof_payload)
    try:
        receipt, _ = _artifact(inputs.receipt_file)
    except ContractError as error:
        raise ContractError(
            "Keycloak realm migration is pending; run the explicit one-shot "
            "keycloak-migration-apply operation before application startup"
        ) from error
    mutations = receipt.get("firstRunOperations")
    if (
        set(receipt)
        != {
            "schemaVersion",
            "status",
            "operationId",
            "keycloakVersion",
            "manifestDigest",
            "bundleDigest",
            "baselineArtifactDigest",
            "targetBaselineRevision",
            "backupProofDigest",
            "firstRunOperations",
            "firstRunMutationCount",
            "semanticReadbackVerified",
            "secondRunPlanEmpty",
            "bootstrapAuthorityRealm",
            "bootstrapAuthorityClientId",
            "bootstrapAuthorityDeleted",
            "bootstrapAuthorityNegativeReadbackVerified",
            "supportSafe",
            "containsSecretValues",
        }
        or receipt.get("schemaVersion") != RECEIPT_SCHEMA
        or receipt.get("status") != "complete"
        or receipt.get("operationId") != OPERATION_ID
        or receipt.get("keycloakVersion") != "26.7.0"
        or receipt.get("manifestDigest") != inputs.manifest_digest
        or receipt.get("bundleDigest") != inputs.bundle_digest
        or receipt.get("baselineArtifactDigest") != inputs.baseline_digest
        or receipt.get("targetBaselineRevision") != inputs.target_revision
        or receipt.get("backupProofDigest") != backup_proof_digest
        or not isinstance(mutations, list)
        or any(not isinstance(item, str) for item in mutations)
        or mutations != sorted(set(mutations))
        or not set(mutations).issubset(ALLOWED_MUTATIONS)
        or receipt.get("firstRunMutationCount") != len(mutations)
        or receipt.get("semanticReadbackVerified") is not True
        or receipt.get("secondRunPlanEmpty") is not True
        or receipt.get("bootstrapAuthorityDeleted") is not True
        or receipt.get("bootstrapAuthorityNegativeReadbackVerified") is not True
        or receipt.get("supportSafe") is not True
        or receipt.get("containsSecretValues") is not False
        or receipt.get("bootstrapAuthorityRealm") != "master"
        or receipt.get("bootstrapAuthorityClientId")
        != "weave-realm-migration-bootstrap"
    ):
        raise ContractError(
            "Keycloak realm migration receipt is stale, incomplete, or outside the qualified contract"
        )
    return inputs
