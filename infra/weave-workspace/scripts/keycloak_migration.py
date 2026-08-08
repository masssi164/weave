#!/usr/bin/env python3
"""Validate the exact, support-safe Keycloak post-import migration receipt."""

from __future__ import annotations

import hashlib
import json
import os
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
BACKUP_PROOF_SCHEMA = "weave.keycloak-realm-migration-backup-proof/v1"
FRESH_START_PROOF_SCHEMA = "weave.keycloak-realm-migration-fresh-start-proof/v1"
DISPOSABLE_E2E_PROOF_SCHEMA = "weave.keycloak-realm-migration-disposable-e2e-proof/v1"
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
    semantic_realm_source_digest: str
    migration_definition_digest: str
    overlay_digest: str
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
    evidence_file = root / "keycloak/realm-render-evidence.json"
    manifest, manifest_payload = _artifact(manifest_file)
    bundle, bundle_payload = _artifact(bundle_file)
    rendered, _ = _artifact(render_file)
    evidence, _ = _artifact(evidence_file)

    bundle_digest = _digest(bundle_payload)
    manifest_digest = _digest(manifest_payload)
    semantic_digest = manifest.get("semanticRealmSourceDigest")
    migration_definition_digest = manifest.get("migrationDefinitionDigest")
    baseline_digest = manifest.get("renderedRealmDigest")
    target_revision = bundle.get("toBaselineRevision")
    if (
        manifest.get("schemaVersion") != "weave.keycloak-realm-migration-manifest/v2"
        or manifest.get("containsSecretValues") is not False
        or not isinstance(semantic_digest, str)
        or not SHA256.fullmatch(semantic_digest)
        or not isinstance(migration_definition_digest, str)
        or not SHA256.fullmatch(migration_definition_digest)
        or not isinstance(baseline_digest, str)
        or not SHA256.fullmatch(baseline_digest)
        or manifest.get("bundles")
        != [{"digest": bundle_digest, "path": "keycloak/migrations/fresh-start-v1.json"}]
    ):
        raise ContractError("Keycloak migration manifest does not bind semantic and rendered realm identities")
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
        or bundle.get("keycloakVersion") != "26.7.1"
        or bundle.get("operations") != [expected_operation]
        or not SHA256.fullmatch(str(expected_operation["desiredStateDigest"]))
        or bundle.get("status") != "blocked-post-import-operation"
        or not isinstance(target_revision, str)
        or not SHA256.fullmatch(target_revision)
    ):
        raise ContractError("Keycloak migration bundle is not the qualified blocked operation")
    realm_identity = rendered.get("realmIdentity")
    deployment_artifacts = rendered.get("deploymentArtifacts")
    evidence_identity = evidence.get("realmIdentity")
    overlay_digest = realm_identity.get("overlayDigest") if isinstance(realm_identity, dict) else None
    if (
        rendered.get("schemaVersion") != "weave.compose-render.v2"
        or rendered.get("containsSecretValues") is not False
        or rendered.get("baselineRevision") != target_revision
        or not isinstance(realm_identity, dict)
        or realm_identity.get("semanticRealmSourceDigest") != semantic_digest
        or realm_identity.get("migrationDefinitionDigest") != migration_definition_digest
        or realm_identity.get("renderedRealmDigest") != baseline_digest
        or not isinstance(overlay_digest, str)
        or not SHA256.fullmatch(overlay_digest)
        or not isinstance(deployment_artifacts, dict)
        or deployment_artifacts.get("migrationBundleDigest") != bundle_digest
        or deployment_artifacts.get("migrationBundlePath") != "keycloak/migrations/fresh-start-v1.json"
        or deployment_artifacts.get("renderedRealmPath") != "keycloak/import/weave-realm.json"
        or deployment_artifacts.get("environmentRenderEvidencePath") != "keycloak/realm-render-evidence.json"
        or deployment_artifacts.get("containsSecretValues") is not False
        or evidence.get("schemaVersion") != "weave.keycloak-environment-render-evidence/v1"
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
        or evidence.get("environment") != context.environment
        or evidence.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or evidence.get("candidateCommit") != context.env["WEAVE_CANDIDATE_COMMIT"]
        or evidence.get("candidateManifestDigest") != context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
        or evidence_identity != realm_identity
    ):
        raise ContractError("environment realm render evidence is stale or ambiguously derived")
    return MigrationInputs(
        artifact_root=root,
        manifest_digest=manifest_digest,
        bundle_digest=bundle_digest,
        baseline_digest=baseline_digest,
        semantic_realm_source_digest=semantic_digest,
        migration_definition_digest=migration_definition_digest,
        overlay_digest=overlay_digest,
        target_revision=target_revision,
        receipt_file=migration_root / RECEIPT_NAME,
        backup_proof_file=migration_root / "fgap-v2-primary-organization-post-import.backup-proof.json",
    )


def _precondition_proof_digest(context: ComposeContext, inputs: MigrationInputs) -> str:
    proof, proof_payload = _artifact(inputs.backup_proof_file)
    if inputs.backup_proof_file.stat().st_mode & 0o777 not in {0o400, 0o600}:
        raise ContractError("Keycloak migration precondition proof must be mode 0400 or 0600")
    common = (
        proof.get("supportSafe") is True
        and proof.get("status") == "verified"
        and proof.get("environment") == context.environment
        and proof.get("realm") == "weave"
        and proof.get("sourceBaselineRevision") == inputs.target_revision
        and proof.get("candidateCommit") == context.env["WEAVE_CANDIDATE_COMMIT"]
        and proof.get("composeProject") == context.env["WEAVE_COMPOSE_PROJECT"]
    )
    if not common:
        raise ContractError("Keycloak migration precondition proof is stale or outside deployment scope")
    if proof.get("schemaVersion") == BACKUP_PROOF_SCHEMA:
        created_at = proof.get("createdAt")
        try:
            parsed_created_at = datetime.fromisoformat(str(created_at).replace("Z", "+00:00"))
        except ValueError as error:
            raise ContractError("Keycloak migration backup proof timestamp is malformed") from error
        if (
            context.environment not in {"dogfood", "prod"}
            or set(proof)
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
            or parsed_created_at.tzinfo is None
            or not SHA256.fullmatch(str(proof.get("backupManifestSha256")))
            or not SHA256.fullmatch(str(proof.get("backupIdSha256")))
        ):
            raise ContractError("Keycloak migration backup proof is stale or outside deployment scope")
        return _digest(proof_payload)
    if proof.get("schemaVersion") == FRESH_START_PROOF_SCHEMA:
        if (
            context.environment != "dogfood"
            or set(proof)
            != {
                "schemaVersion",
                "supportSafe",
                "containsSecretValues",
                "status",
                "environment",
                "realm",
                "sourceBaselineRevision",
                "freshStartPlanSha256",
                "freshStartApplyEvidenceSha256",
                "operationNonce",
                "retiredGeneration",
                "targetGeneration",
                "candidateCommit",
                "candidateManifestDigest",
                "composeProject",
            }
            or proof.get("containsSecretValues") is not False
            or not SHA256.fullmatch(str(proof.get("freshStartPlanSha256")))
            or not SHA256.fullmatch(str(proof.get("freshStartApplyEvidenceSha256")))
            or not re.fullmatch(r"[a-z0-9][a-z0-9-]{15,63}", str(proof.get("operationNonce", "")))
            or not isinstance(proof.get("retiredGeneration"), str)
            or not proof.get("retiredGeneration")
            or proof.get("targetGeneration") != context.env["WEAVE_RESOURCE_GENERATION"]
            or proof.get("candidateManifestDigest") != context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
        ):
            raise ContractError("Keycloak Fresh Start proof is stale or outside cutover scope")
        return _digest(proof_payload)
    if proof.get("schemaVersion") == DISPOSABLE_E2E_PROOF_SCHEMA:
        if (
            context.environment != "e2e"
            or context.isolated_namespace is None
            or set(proof)
            != {
                "schemaVersion",
                "supportSafe",
                "containsSecretValues",
                "status",
                "environment",
                "realm",
                "sourceBaselineRevision",
                "emptyNamespaceProofSha256",
                "runId",
                "namespace",
                "candidateCommit",
                "candidateManifestDigest",
                "composeProject",
            }
            or proof.get("containsSecretValues") is not False
            or not SHA256.fullmatch(str(proof.get("emptyNamespaceProofSha256")))
            or proof.get("runId") != os.environ.get("WEAVE_E2E_RUN_ID")
            or proof.get("namespace") != context.isolated_namespace
            or proof.get("candidateManifestDigest")
            != context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
        ):
            raise ContractError("Keycloak disposable E2E proof is stale or outside run scope")
        return _digest(proof_payload)
    raise ContractError("Keycloak migration precondition proof has an unsupported schema")


def require_completed_migration(context: ComposeContext) -> MigrationInputs:
    if context.environment not in {"dogfood", "prod", "e2e"}:
        raise ContractError(
            "the qualified Keycloak FGAP migration is limited to protected persistent environments and isolated E2E"
        )
    inputs = migration_inputs(context)
    precondition_proof_digest = _precondition_proof_digest(context, inputs)
    try:
        receipt, _ = _artifact(inputs.receipt_file)
    except ContractError as error:
        raise ContractError(
            "Keycloak realm migration is pending; run the explicit one-shot keycloak-migration-apply operation before application startup"
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
        or receipt.get("keycloakVersion") != "26.7.1"
        or receipt.get("manifestDigest") != inputs.manifest_digest
        or receipt.get("bundleDigest") != inputs.bundle_digest
        or receipt.get("baselineArtifactDigest") != inputs.baseline_digest
        or receipt.get("targetBaselineRevision") != inputs.target_revision
        or receipt.get("backupProofDigest") != precondition_proof_digest
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
        or receipt.get("bootstrapAuthorityClientId") != "weave-realm-migration-bootstrap"
    ):
        raise ContractError("Keycloak realm migration receipt is stale, incomplete, or outside the qualified contract")
    return inputs
