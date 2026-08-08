#!/usr/bin/env python3
"""Negative contracts for the bounded Keycloak migration authority."""

from __future__ import annotations

import hashlib
import base64
import json
import os
import stat
import sys
import tempfile
from dataclasses import replace
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import ContractError, load_context  # noqa: E402
from keycloak_migration import (  # noqa: E402
    OPERATION_ID,
    migration_inputs,
    require_completed_migration,
)
from keycloak_migration_backup import create_backup_proof  # noqa: E402
sys.path.insert(0, str(ROOT / "keycloak"))
import oauth_probe  # noqa: E402


def digest(payload: bytes) -> str:
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def write(path: Path, value: dict[str, object]) -> bytes:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    path.write_bytes(payload)
    return payload


def artifacts(root: Path) -> tuple[object, dict[str, object]]:
    generated = root / "generated"
    baseline_digest = "sha256:" + "1" * 64
    target_revision = "sha256:" + "2" * 64
    operation = {
        "blockedBy": "keycloak-26.7-imports-client-authorization-before-organizations",
        "desiredStateDigest": "sha256:" + "3" * 64,
        "desiredStatePointer": "/fineGrainedAdminPermissions",
        "id": OPERATION_ID,
        "phase": "post-realm-import",
        "status": "requires-qualified-admin-rest-executor",
        "type": "keycloak-fgap-v2",
    }
    bundle_payload = write(
        generated / "keycloak/migrations/fresh-start-v1.json",
        {
            "apiVersion": "weave.keycloak-realm-migration-bundle/v1",
            "applicability": "after-fresh-start-realm-import",
            "baselineArtifactDigest": baseline_digest,
            "containsSecretValues": False,
            "fromBaselineRevision": None,
            "keycloakVersion": "26.7.0",
            "operations": [operation],
            "reason": "public test fixture",
            "status": "blocked-post-import-operation",
            "toBaselineRevision": target_revision,
        },
    )
    bundle_digest = digest(bundle_payload)
    manifest_payload = write(
        generated / "keycloak/migrations/manifest.json",
        {
            "schemaVersion": "weave.keycloak-realm-migration-manifest/v1",
            "baselineArtifactDigest": baseline_digest,
            "bundles": [
                {
                    "digest": bundle_digest,
                    "path": "keycloak/migrations/fresh-start-v1.json",
                }
            ],
            "containsSecretValues": False,
        },
    )
    write(
        generated / "render-manifest.json",
        {
            "schemaVersion": "weave.compose-render.v1",
            "baselineRevision": target_revision,
            "containsSecretValues": False,
            "realmArtifacts": {
                "baselineDigest": baseline_digest,
                "containsSecretValues": False,
                "migrationBundleDigest": bundle_digest,
                "migrationBundlePath": "keycloak/migrations/fresh-start-v1.json",
            },
        },
    )
    context = load_context("dev", ROOT)
    context = replace(
        context,
        environment="dogfood",
        profile="dogfood",
        env={**context.env, "WEAVE_GENERATED_ROOT": str(generated)},
    )
    inputs = migration_inputs(context)
    backup_proof_payload = write(
        inputs.backup_proof_file,
        {
            "schemaVersion": "weave.keycloak-realm-migration-backup-proof/v1",
            "supportSafe": True,
            "status": "verified",
            "createdAt": "2026-08-08T12:00:00Z",
            "environment": "dogfood",
            "realm": "weave",
            "sourceBaselineRevision": target_revision,
            "backupManifestSha256": "sha256:" + "4" * 64,
            "backupIdSha256": "sha256:" + "5" * 64,
            "candidateCommit": context.env["WEAVE_CANDIDATE_COMMIT"],
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        },
    )
    receipt = {
        "schemaVersion": "weave.keycloak-fgap-migration-receipt/v1",
        "status": "complete",
        "operationId": OPERATION_ID,
        "keycloakVersion": "26.7.0",
        "manifestDigest": digest(manifest_payload),
        "bundleDigest": bundle_digest,
        "baselineArtifactDigest": baseline_digest,
        "targetBaselineRevision": target_revision,
        "backupProofDigest": digest(backup_proof_payload),
        "firstRunOperations": ["create-identity-admin-subject-policy"],
        "firstRunMutationCount": 1,
        "semanticReadbackVerified": True,
        "secondRunPlanEmpty": True,
        "bootstrapAuthorityDeleted": True,
        "bootstrapAuthorityNegativeReadbackVerified": True,
        "supportSafe": True,
        "containsSecretValues": False,
        "bootstrapAuthorityRealm": "master",
        "bootstrapAuthorityClientId": "weave-realm-migration-bootstrap",
    }
    return context, receipt


def rejected(action) -> None:
    try:
        action()
    except ContractError:
        return
    raise AssertionError("unsafe migration evidence was accepted")


def main() -> None:
    claims = base64.urlsafe_b64encode(
        json.dumps(
            {
                "realm_access": {"roles": ["weaver-runtime"]},
                "resource_access": {
                    "realm-management": {"roles": ["create-client"]}
                },
            },
            separators=(",", ":"),
        ).encode()
    ).rstrip(b"=").decode()
    assert oauth_probe.access_token_role_projection(f"e30.{claims}.signature") == (
        {"weaver-runtime"},
        {"realm-management": {"create-client"}},
    )
    try:
        oauth_probe.access_token_role_projection("malformed")
    except oauth_probe.OAuthProbeError:
        pass
    else:
        raise AssertionError("malformed OAuth role projection was accepted")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        context, receipt = artifacts(root)
        backup_dir = root / "private-backup"
        backup_dir.mkdir(mode=0o700)
        backup_manifest = {
            "schemaVersion": "weave.compose-private-backup.v3",
            "backupId": "private-backup-id-not-for-proof",
            "candidateCommit": context.env["WEAVE_CANDIDATE_COMMIT"],
            "profile": "dogfood",
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        }
        write(backup_dir / "BackupManifest.json", backup_manifest)
        previous_candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT")
        os.environ["WEAVE_CANDIDATE_COMMIT"] = context.env["WEAVE_CANDIDATE_COMMIT"]
        try:
            with mock.patch("backup_runtime.backup", return_value=backup_dir), mock.patch(
                "adoption_rehearsal.rehearse",
                return_value={
                    "backupVerified": True,
                    "isolatedRestoreVerified": True,
                    "cleanupVerified": True,
                    "supportSafe": True,
                    "containsSecretValues": False,
                },
            ):
                proof_file = create_backup_proof(context)
        finally:
            if previous_candidate is None:
                os.environ.pop("WEAVE_CANDIDATE_COMMIT", None)
            else:
                os.environ["WEAVE_CANDIDATE_COMMIT"] = previous_candidate
        proof = json.loads(proof_file.read_text(encoding="utf-8"))
        assert set(proof) == {
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
        assert stat.S_IMODE(proof_file.stat().st_mode) == 0o600
        receipt["backupProofDigest"] = digest(proof_file.read_bytes())
        rejected(lambda: require_completed_migration(context))
        inputs = migration_inputs(context)
        write(inputs.receipt_file, receipt)
        require_completed_migration(context)

        receipt["bootstrapAuthorityNegativeReadbackVerified"] = False
        write(inputs.receipt_file, receipt)
        rejected(lambda: require_completed_migration(context))
        receipt["bootstrapAuthorityNegativeReadbackVerified"] = True

        receipt["firstRunOperations"] = ["unbounded-admin-mutation"]
        write(inputs.receipt_file, receipt)
        rejected(lambda: require_completed_migration(context))

        os.chmod(inputs.receipt_file, 0o644)
        assert stat.S_IMODE(inputs.receipt_file.stat().st_mode) == 0o644

    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    normal_keycloak = compose.split("\n  keycloak:\n", 1)[1].split(
        "\n  keycloak-realm-migration-bootstrap:\n", 1
    )[0]
    bootstrap = compose.split(
        "\n  keycloak-realm-migration-bootstrap:\n", 1
    )[1].split("\n  keycloak-realm-migration:\n", 1)[0]
    migration = compose.split("\n  keycloak-realm-migration:\n", 1)[1].split(
        "\n  mailpit:\n", 1
    )[0]
    assert "keycloak-realm-migration-bootstrap-secret" not in normal_keycloak
    assert "keycloak-realm-migration-bootstrap-secret" in bootstrap
    assert "keycloak-realm-migration-bootstrap-secret" in migration
    assert ":/run/weave-generated:ro" in migration
    assert "/keycloak/migrations:/run/weave-generated/keycloak/migrations" in migration
    for private_key in (
        "keycloak-weave-backend-jwk",
        "keycloak-weave-identity-admin-jwk",
        "keycloak-weave-mcp-server-jwk",
        "weave-agent-runtime-admin",
    ):
        assert private_key not in bootstrap
        assert private_key not in migration
    assert "identity-ops:" not in compose
    assert "kcadm" not in compose
    assert not (ROOT / "keycloak/identity_ops.py").exists()
    assert not (ROOT / "keycloak/Dockerfile.identity-ops").exists()

    launcher = (ROOT / "scripts/run-keycloak.sh").read_text(encoding="utf-8")
    assert "--secret" not in launcher
    assert "temporary migration authority reached normal Keycloak startup" in launcher
    print("Keycloak migration boundary contract tests passed")


if __name__ == "__main__":
    main()
