#!/usr/bin/env python3
"""Closed validation for candidate-bound backup rehearsal receipts."""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
POSTGRES_IMAGE_RE = re.compile(r"^postgres@sha256:[0-9a-f]{64}$")
IMMUTABLE_IMAGE_RE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
BACKUP_REF_RE = re.compile(r"^evidence:private-backup:sha256:([0-9a-f]{64})$")
NAMESPACE_RE = re.compile(r"^weave-restore-[0-9a-f]{12}$")
PROVIDER_ARCHIVES = frozenset(
    (
        "caddy-config.tgz",
        "caddy-data.tgz",
        "keycloak-data.tgz",
        "matrix-appservice.tgz",
        "nextcloud-data.tgz",
        "synapse-data.tgz",
    )
)
COMMON_FIELDS = frozenset(
    (
        "schemaVersion",
        "profile",
        "composeProject",
        "candidateCommit",
        "candidateManifestDigest",
        "backupRef",
        "databaseFingerprint",
        "postgresDumpClientImage",
        "postgresDatabaseInventoryDigest",
        "postgresDatabaseCount",
        "backupVerified",
        "isolatedRestoreVerified",
        "restoreHelperImage",
        "restoredVolumeInventories",
        "isolatedNamespace",
        "verifiedDatabaseCount",
        "verifiedServiceDatabaseCount",
        "verifiedAt",
        "cleanupVerified",
        "supportSafe",
        "containsSecretValues",
    )
)
FRESH_START_FIELDS = frozenset(
    (
        "recoveryBoundary",
        "legacyStateMigrated",
        "adoptionAuthorized",
        "privateArtifactCount",
        "restoredProviderVolumeCount",
    )
)
ADOPTION_FIELDS = frozenset(
    (
        "secretContinuityVerified",
        "secretMigrationReceiptRef",
        "resources",
    )
)


class ReceiptContractError(ValueError):
    """Raised when recovery evidence is incomplete, unsafe, or stale."""


def database_inventory_digest(names: list[str]) -> str:
    canonical = json.dumps(
        names, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(canonical).hexdigest()


def _bounded_integer(value: object, *, minimum: int = 0) -> bool:
    return (
        isinstance(value, int)
        and not isinstance(value, bool)
        and minimum <= value <= 2_147_483_647
    )


def _validate_inventory(rows: object) -> None:
    if not isinstance(rows, list) or len(rows) != len(PROVIDER_ARCHIVES):
        raise ReceiptContractError("restore receipt provider-volume inventory is incomplete")
    observed: set[str] = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {
            "artifact",
            "entryCount",
            "regularFileCount",
            "regularFileBytes",
            "inventoryDigest",
            "rootMetadata",
            "verified",
        }:
            raise ReceiptContractError(
                "restore receipt provider-volume inventory has an unsupported shape"
            )
        artifact = row.get("artifact")
        if artifact not in PROVIDER_ARCHIVES or artifact in observed:
            raise ReceiptContractError(
                "restore receipt provider-volume inventory is ambiguous"
            )
        observed.add(artifact)
        if (
            not _bounded_integer(row.get("entryCount"), minimum=1)
            or not _bounded_integer(row.get("regularFileCount"))
            or not _bounded_integer(row.get("regularFileBytes"))
            or row.get("verified") is not True
            or not isinstance(row.get("inventoryDigest"), str)
            or not DIGEST_RE.fullmatch(row["inventoryDigest"])
        ):
            raise ReceiptContractError(
                "restore receipt provider-volume inventory is invalid"
            )
        root = row.get("rootMetadata")
        if (
            not isinstance(root, dict)
            or set(root) != {"uid", "gid", "mode"}
            or not _bounded_integer(root.get("uid"))
            or not _bounded_integer(root.get("gid"))
            or not _bounded_integer(root.get("mode"))
            or root["mode"] > 0o7777
        ):
            raise ReceiptContractError(
                "restore receipt provider-volume root metadata is invalid"
            )
    if observed != PROVIDER_ARCHIVES:
        raise ReceiptContractError("restore receipt provider-volume inventory is incomplete")


def validate_receipt(
    receipt: dict[str, Any],
    *,
    purpose: str,
    candidate: str,
    candidate_manifest_digest: str,
    profile: str,
    compose_project: str,
    backup_manifest_digest: str | None = None,
    maximum_age: timedelta | None = None,
) -> dict[str, Any]:
    if purpose not in {"adoption", "fresh-start"}:
        raise ReceiptContractError("restore receipt purpose is unsupported")
    schema = (
        "weave.compose-adoption-receipt.v1"
        if purpose == "adoption"
        else "weave.fresh-start-private-backup-rehearsal.v1"
    )
    expected_fields = COMMON_FIELDS | (
        ADOPTION_FIELDS if purpose == "adoption" else FRESH_START_FIELDS
    )
    if set(receipt) != expected_fields:
        raise ReceiptContractError("restore receipt has an unsupported field set")
    if (
        receipt.get("schemaVersion") != schema
        or not COMMIT_RE.fullmatch(candidate)
        or receipt.get("candidateCommit") != candidate
        or not DIGEST_RE.fullmatch(candidate_manifest_digest)
        or receipt.get("candidateManifestDigest") != candidate_manifest_digest
        or receipt.get("profile") != profile
        or receipt.get("composeProject") != compose_project
        or receipt.get("backupVerified") is not True
        or receipt.get("isolatedRestoreVerified") is not True
        or receipt.get("cleanupVerified") is not True
        or receipt.get("supportSafe") is not True
        or receipt.get("containsSecretValues") is not False
        or not isinstance(receipt.get("databaseFingerprint"), str)
        or not DIGEST_RE.fullmatch(receipt["databaseFingerprint"])
        or not isinstance(receipt.get("postgresDumpClientImage"), str)
        or not POSTGRES_IMAGE_RE.fullmatch(receipt["postgresDumpClientImage"])
        or not isinstance(receipt.get("restoreHelperImage"), str)
        or not IMMUTABLE_IMAGE_RE.fullmatch(receipt["restoreHelperImage"])
        or not isinstance(receipt.get("postgresDatabaseInventoryDigest"), str)
        or not DIGEST_RE.fullmatch(receipt["postgresDatabaseInventoryDigest"])
        or not isinstance(receipt.get("isolatedNamespace"), str)
        or not NAMESPACE_RE.fullmatch(receipt["isolatedNamespace"])
    ):
        raise ReceiptContractError(
            "restore receipt is unsafe, stale, or not candidate-bound"
        )
    backup_ref = receipt.get("backupRef")
    match = BACKUP_REF_RE.fullmatch(backup_ref) if isinstance(backup_ref, str) else None
    if match is None or (
        backup_manifest_digest is not None
        and match.group(1) != backup_manifest_digest.removeprefix("sha256:")
    ):
        raise ReceiptContractError("restore receipt backup binding is invalid")
    database_count = receipt.get("postgresDatabaseCount")
    if (
        not _bounded_integer(database_count, minimum=1)
        or receipt.get("verifiedDatabaseCount") != database_count
        or receipt.get("verifiedServiceDatabaseCount") != database_count - 1
    ):
        raise ReceiptContractError("restore receipt database proof is inconsistent")
    _validate_inventory(receipt.get("restoredVolumeInventories"))
    try:
        verified_at = datetime.fromisoformat(
            str(receipt.get("verifiedAt", "")).replace("Z", "+00:00")
        )
    except ValueError as error:
        raise ReceiptContractError("restore receipt verification time is invalid") from error
    now = datetime.now(timezone.utc)
    if (
        verified_at.tzinfo is None
        or verified_at > now
        or (maximum_age is not None and now - verified_at > maximum_age)
    ):
        raise ReceiptContractError("restore receipt verification time is stale or future-dated")
    if purpose == "fresh-start":
        if (
            receipt.get("recoveryBoundary")
            != "private-backup-only-no-adoption"
            or receipt.get("legacyStateMigrated") is not False
            or receipt.get("adoptionAuthorized") is not False
            or receipt.get("privateArtifactCount")
            != len(PROVIDER_ARCHIVES) + 2
            or receipt.get("restoredProviderVolumeCount") != len(PROVIDER_ARCHIVES)
        ):
            raise ReceiptContractError(
                "Fresh Start receipt does not prove the no-adoption recovery boundary"
            )
    else:
        if (
            receipt.get("secretContinuityVerified") is not True
            or not isinstance(receipt.get("secretMigrationReceiptRef"), str)
            or not re.fullmatch(
                r"evidence:legacy-secret-migration:sha256:[0-9a-f]{64}",
                receipt["secretMigrationReceiptRef"],
            )
            or not isinstance(receipt.get("resources"), list)
        ):
            raise ReceiptContractError("adoption receipt continuity proof is invalid")
    return receipt


def load_fresh_start_recovery(
    receipt_path: Path,
    *,
    candidate: str,
    candidate_manifest_digest: str,
    maximum_age: timedelta = timedelta(hours=6),
) -> str:
    try:
        metadata = receipt_path.lstat()
    except FileNotFoundError as error:
        raise ReceiptContractError("Fresh Start recovery receipt is unavailable") from error
    if (
        stat.S_ISLNK(metadata.st_mode)
        or not stat.S_ISREG(metadata.st_mode)
        or metadata.st_uid != os.getuid()
        or stat.S_IMODE(metadata.st_mode) != 0o600
    ):
        raise ReceiptContractError(
            "Fresh Start recovery receipt must be owner-controlled mode-0600"
        )
    try:
        receipt_bytes = receipt_path.read_bytes()
        receipt = json.loads(receipt_bytes)
    except (OSError, json.JSONDecodeError) as error:
        raise ReceiptContractError("Fresh Start recovery receipt is unreadable") from error
    if not isinstance(receipt, dict):
        raise ReceiptContractError("Fresh Start recovery receipt must contain an object")
    manifest_path = receipt_path.parent / "BackupManifest.json"
    try:
        manifest_metadata = manifest_path.lstat()
        manifest_bytes = manifest_path.read_bytes()
        manifest = json.loads(manifest_bytes)
    except (FileNotFoundError, OSError, json.JSONDecodeError) as error:
        raise ReceiptContractError("Fresh Start private BackupManifest is unreadable") from error
    if (
        stat.S_ISLNK(manifest_metadata.st_mode)
        or not stat.S_ISREG(manifest_metadata.st_mode)
        or manifest_metadata.st_uid != os.getuid()
        or stat.S_IMODE(manifest_metadata.st_mode) != 0o600
        or not isinstance(manifest, dict)
        or manifest.get("schemaVersion") != "weave.compose-private-backup.v3"
        or manifest.get("candidateCommit") != candidate
        or manifest.get("candidateManifestDigest") != candidate_manifest_digest
        or manifest.get("supportSafe") is not False
        or manifest.get("containsSecretsOrMemberData") is not True
    ):
        raise ReceiptContractError(
            "Fresh Start private BackupManifest is unsafe or candidate-mismatched"
        )
    manifest_digest = "sha256:" + hashlib.sha256(manifest_bytes).hexdigest()
    validate_receipt(
        receipt,
        purpose="fresh-start",
        candidate=candidate,
        candidate_manifest_digest=candidate_manifest_digest,
        profile=str(manifest.get("profile", "")),
        compose_project=str(manifest.get("composeProject", "")),
        backup_manifest_digest=manifest_digest,
        maximum_age=maximum_age,
    )
    if (
        receipt.get("postgresDumpClientImage")
        != manifest.get("postgresDumpClientImage")
        or receipt.get("postgresDatabaseInventoryDigest")
        != manifest.get("postgresDatabaseInventoryDigest")
        or receipt.get("postgresDatabaseCount")
        != len(manifest.get("postgresDatabases", []))
    ):
        raise ReceiptContractError(
            "Fresh Start recovery receipt does not match its private BackupManifest"
        )
    return hashlib.sha256(receipt_bytes).hexdigest()
