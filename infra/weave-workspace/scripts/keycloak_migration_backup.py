#!/usr/bin/env python3
"""Create the exact support-safe precondition proof for bounded Keycloak migration."""

from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from compose_env import ComposeContext, ContractError
from keycloak_migration import migration_inputs


BACKUP_PROOF_NAME = "fgap-v2-primary-organization-post-import.backup-proof.json"
FRESH_START_PROOF_SCHEMA = "weave.keycloak-realm-migration-fresh-start-proof/v1"
BACKUP_PROOF_SCHEMA = "weave.keycloak-realm-migration-backup-proof/v1"
DISPOSABLE_E2E_PROOF_SCHEMA = "weave.keycloak-realm-migration-disposable-e2e-proof/v1"
EMPTY_NAMESPACE_SCHEMA = "weave.e2e-empty-namespace-proof/v1"


def _sha256_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def _canonical_json(value: Any) -> bytes:
    """Match fresh-start.py's constrained RFC 8785 serialization exactly."""

    def serialize(item: Any) -> str:
        if item is None:
            return "null"
        if item is True:
            return "true"
        if item is False:
            return "false"
        if isinstance(item, str):
            return json.dumps(item, ensure_ascii=False, separators=(",", ":"))
        if isinstance(item, list):
            return "[" + ",".join(serialize(member) for member in item) + "]"
        if isinstance(item, dict):
            if not all(isinstance(key, str) for key in item):
                raise ContractError("Fresh Start canonical JSON keys must be strings")
            keys = sorted(
                item,
                key=lambda key: key.encode("utf-16be", errors="surrogatepass"),
            )
            return (
                "{"
                + ",".join(
                    f"{serialize(key)}:{serialize(item[key])}" for key in keys
                )
                + "}"
            )
        raise ContractError("Fresh Start canonical JSON must not contain numbers")

    return serialize(value).encode("utf-8")


def _exact_canonical_json(path: Path, schema: str) -> tuple[dict[str, object], bytes]:
    if path.is_symlink() or not path.is_file():
        raise ContractError("Fresh Start migration proof input is missing or unsafe")
    payload = path.read_bytes()
    try:
        value = json.loads(payload)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ContractError("Fresh Start migration proof input is malformed") from error
    if not isinstance(value, dict) or value.get("schemaVersion") != schema:
        raise ContractError("Fresh Start migration proof input has an unsupported schema")
    if value.get("supportSafe") is not True:
        raise ContractError("Fresh Start migration proof input is not support-safe")
    if _canonical_json(value) != payload:
        raise ContractError("Fresh Start migration proof input is not canonical JSON")
    adjacent = path.with_suffix(path.suffix + ".sha256")
    if adjacent.is_symlink() or not adjacent.is_file():
        raise ContractError("Fresh Start migration proof input has no adjacent digest")
    actual = hashlib.sha256(payload).hexdigest()
    if adjacent.read_text(encoding="ascii").split()[0] != actual:
        raise ContractError("Fresh Start migration proof input digest does not match")
    return value, payload


def _atomic_private(
    path: Path, value: dict[str, object], runtime_owner: tuple[int, int]
) -> None:
    if path.is_symlink():
        raise ContractError("Keycloak migration precondition proof target is a symlink")
    payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
        uid, gid = runtime_owner
        if path.stat().st_uid != uid or path.stat().st_gid != gid:
            try:
                os.chown(path, uid, gid)
            except PermissionError as error:
                raise ContractError(
                    "Keycloak migration precondition proof is not readable by the rootless Server migration uid/gid"
                ) from error
    finally:
        if temporary.exists():
            temporary.unlink()


def _fresh_start_proof(context: ComposeContext, candidate: str) -> dict[str, object] | None:
    plan_value = os.environ.get("WEAVE_FRESH_START_PLAN", "").strip()
    apply_value = os.environ.get("WEAVE_FRESH_START_APPLY_EVIDENCE", "").strip()
    if not plan_value and not apply_value:
        return None
    if not plan_value or not apply_value:
        raise ContractError("Fresh Start migration requires both plan and apply evidence")

    plan_path = Path(plan_value).expanduser().absolute()
    apply_path = Path(apply_value).expanduser().absolute()
    plan, plan_payload = _exact_canonical_json(plan_path, "weave.infra.fresh-start-plan.v1")
    applied, apply_payload = _exact_canonical_json(
        apply_path, "weave.infra.fresh-start-apply-evidence.v1"
    )
    plan_digest = hashlib.sha256(plan_payload).hexdigest()
    inputs = migration_inputs(context)

    if context.environment != "dogfood":
        raise ContractError("Fresh Start Keycloak cutover is qualified only for dogfood")
    if (
        plan.get("environment") != "persistent-dogfood"
        or plan.get("candidateCommit") != candidate
        or plan.get("targetGeneration") != context.env["WEAVE_RESOURCE_GENERATION"]
        or plan.get("candidateManifestDigest")
        != context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
        or applied.get("environment") != plan.get("environment")
        or applied.get("stack") != plan.get("stack")
        or applied.get("retiredGeneration") != plan.get("retiredGeneration")
        or applied.get("targetGeneration") != plan.get("targetGeneration")
        or applied.get("operationNonce") != plan.get("operationNonce")
        or applied.get("planSha256") != plan_digest
        or applied.get("status") != "removed-pending-target-recreation"
        or applied.get("exclusionsVerified") is not True
    ):
        raise ContractError("Fresh Start migration proof is outside the approved cutover scope")
    results = applied.get("results")
    if (
        not isinstance(results, list)
        or not results
        or any(not isinstance(item, dict) or item.get("status") != "removed" for item in results)
    ):
        raise ContractError("Fresh Start migration proof does not prove complete retirement")
    operation_nonce = plan.get("operationNonce")
    retired_generation = plan.get("retiredGeneration")
    if (
        not isinstance(operation_nonce, str)
        or not re.fullmatch(r"[a-z0-9][a-z0-9-]{15,63}", operation_nonce)
        or not isinstance(retired_generation, str)
        or not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,47}", retired_generation)
    ):
        raise ContractError("Fresh Start migration proof has malformed generation identity")

    return {
        "schemaVersion": FRESH_START_PROOF_SCHEMA,
        "supportSafe": True,
        "containsSecretValues": False,
        "status": "verified",
        "environment": context.environment,
        "realm": "weave",
        "sourceBaselineRevision": inputs.target_revision,
        "freshStartPlanSha256": _sha256_bytes(plan_payload),
        "freshStartApplyEvidenceSha256": _sha256_bytes(apply_payload),
        "operationNonce": operation_nonce,
        "retiredGeneration": retired_generation,
        "targetGeneration": context.env["WEAVE_RESOURCE_GENERATION"],
        "candidateCommit": candidate,
        "candidateManifestDigest": context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"],
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
    }


def _disposable_e2e_proof(context: ComposeContext, candidate: str) -> dict[str, object]:
    if context.environment != "e2e" or context.isolated_namespace is None:
        raise ContractError("disposable migration proof is qualified only for isolated E2E")
    proof_value = os.environ.get("WEAVE_E2E_EMPTY_NAMESPACE_PROOF", "").strip()
    if not proof_value:
        raise ContractError("isolated E2E migration requires an empty-namespace proof")
    proof_path = Path(proof_value).expanduser().absolute()
    if proof_path.is_symlink() or not proof_path.is_file():
        raise ContractError("isolated E2E empty-namespace proof is missing or unsafe")
    payload = proof_path.read_bytes()
    try:
        proof = json.loads(payload)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ContractError("isolated E2E empty-namespace proof is malformed") from error
    resources = proof.get("resources") if isinstance(proof, dict) else None
    run_id = os.environ.get("WEAVE_E2E_RUN_ID", "")
    inputs = migration_inputs(context)
    if (
        not isinstance(proof, dict)
        or proof.get("schemaVersion") != EMPTY_NAMESPACE_SCHEMA
        or proof.get("supportSafe") is not True
        or proof.get("containsSecretValues") is not False
        or proof.get("environment") != "e2e"
        or proof.get("deploymentContext") != "disposable"
        or proof.get("runId") != run_id
        or proof.get("namespace") != context.isolated_namespace
        or proof.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or proof.get("candidateCommit") != candidate
        or proof.get("candidateManifestDigest")
        != context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
        or proof.get("composeContainersAbsent") is not True
        or proof.get("verifiedBeforeResourceCreation") is not True
        or not isinstance(resources, list)
        or not resources
        or any(
            not isinstance(resource, dict)
            or resource.get("kind") not in {"network", "volume"}
            or not isinstance(resource.get("name"), str)
            or not resource.get("name")
            or resource.get("absent") is not True
            for resource in resources
        )
    ):
        raise ContractError("isolated E2E empty-namespace proof is stale or outside run scope")
    return {
        "schemaVersion": DISPOSABLE_E2E_PROOF_SCHEMA,
        "supportSafe": True,
        "containsSecretValues": False,
        "status": "verified",
        "environment": "e2e",
        "realm": "weave",
        "sourceBaselineRevision": inputs.target_revision,
        "emptyNamespaceProofSha256": _sha256_bytes(payload),
        "runId": run_id,
        "namespace": context.isolated_namespace,
        "candidateCommit": candidate,
        "candidateManifestDigest": context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"],
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
    }


def _persistent_backup_proof(context: ComposeContext, candidate: str) -> dict[str, object]:
    import adoption_rehearsal
    import backup_runtime

    if context.environment not in {"dogfood", "prod"}:
        raise ContractError("persistent Keycloak migration backup proof is dogfood/prod only")
    backup_dir = backup_runtime.backup(context)
    rehearsal = adoption_rehearsal.rehearse(context, backup_dir, "fresh-start")
    if (
        rehearsal.get("backupVerified") is not True
        or rehearsal.get("isolatedRestoreVerified") is not True
        or rehearsal.get("cleanupVerified") is not True
        or rehearsal.get("supportSafe") is not True
        or rehearsal.get("containsSecretValues") is not False
    ):
        raise ContractError("private pre-migration backup rehearsal is incomplete")
    manifest_file = backup_dir / "BackupManifest.json"
    if manifest_file.is_symlink() or not manifest_file.is_file():
        raise ContractError("private pre-migration BackupManifest is unavailable")
    manifest_payload = manifest_file.read_bytes()
    manifest = json.loads(manifest_payload)
    backup_id = manifest.get("backupId")
    if (
        manifest.get("schemaVersion") != "weave.compose-private-backup.v3"
        or manifest.get("candidateCommit") != candidate
        or manifest.get("profile") != context.environment
        or manifest.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or not isinstance(backup_id, str)
        or not backup_id
    ):
        raise ContractError("private pre-migration BackupManifest scope is invalid")
    inputs = migration_inputs(context)
    return {
        "schemaVersion": BACKUP_PROOF_SCHEMA,
        "supportSafe": True,
        "status": "verified",
        "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "environment": context.environment,
        "realm": "weave",
        "sourceBaselineRevision": inputs.target_revision,
        "backupManifestSha256": _sha256_bytes(manifest_payload),
        "backupIdSha256": _sha256_bytes(backup_id.encode("utf-8")),
        "candidateCommit": candidate,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
    }


def create_backup_proof(context: ComposeContext) -> Path:
    """Create the single migration precondition proof consumed by the one-shot CLI."""
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ContractError("Keycloak migration proof requires an exact candidate commit")
    if context.environment == "e2e":
        proof = _disposable_e2e_proof(context, candidate)
    else:
        proof = _fresh_start_proof(context, candidate)
        if proof is None:
            proof = _persistent_backup_proof(context, candidate)
    output = context.generated_root / "keycloak/migrations" / BACKUP_PROOF_NAME
    _atomic_private(
        output,
        proof,
        (int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"])),
    )
    return output
