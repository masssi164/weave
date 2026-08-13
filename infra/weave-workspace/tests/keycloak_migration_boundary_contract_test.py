#!/usr/bin/env python3
"""Negative contracts for the bounded Keycloak migration authority."""

from __future__ import annotations

import base64
import hashlib
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
from keycloak_migration import OPERATION_ID, migration_inputs, require_completed_migration  # noqa: E402
from keycloak_migration_backup import _canonical_json, create_backup_proof  # noqa: E402
sys.path.insert(0, str(ROOT / "keycloak"))
import oauth_probe  # noqa: E402


def digest(payload: bytes) -> str:
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def write(path: Path, value: dict[str, object]) -> bytes:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    path.write_bytes(payload)
    return payload


def write_canonical(path: Path, value: dict[str, object]) -> bytes:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = _canonical_json(value)
    path.write_bytes(payload)
    path.with_suffix(path.suffix + ".sha256").write_text(
        f"{hashlib.sha256(payload).hexdigest()}  {path.name}\n", encoding="ascii"
    )
    return payload


def artifacts(root: Path) -> tuple[object, dict[str, object]]:
    generated = root / "generated"
    baseline_digest = "sha256:" + "1" * 64
    target_revision = "sha256:" + "2" * 64
    semantic_digest = "sha256:" + "6" * 64
    migration_definition_digest = "sha256:" + "7" * 64
    overlay_digest = "sha256:" + "8" * 64
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
            "keycloakVersion": "26.7.1",
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
            "schemaVersion": "weave.keycloak-realm-migration-manifest/v2",
            "semanticRealmSourceDigest": semantic_digest,
            "migrationDefinitionDigest": migration_definition_digest,
            "renderedRealmDigest": baseline_digest,
            "bundles": [{"digest": bundle_digest, "path": "keycloak/migrations/fresh-start-v1.json"}],
            "containsSecretValues": False,
        },
    )
    realm_identity = {
        "semanticRealmSourceDigest": semantic_digest,
        "migrationDefinitionDigest": migration_definition_digest,
        "overlayDigest": overlay_digest,
        "renderedRealmDigest": baseline_digest,
    }
    context = load_context("dev", ROOT)
    context = replace(
        context,
        environment="dogfood",
        profile="dogfood",
        env={**context.env, "WEAVE_GENERATED_ROOT": str(generated)},
    )
    render_manifest = {
        "schemaVersion": "weave.compose-render.v3",
        "baselineRevision": target_revision,
        "containsSecretValues": False,
        "realmIdentity": realm_identity,
        "deploymentArtifacts": {
            "renderedRealmPath": "keycloak/import/weave-realm.json",
            "containsSecretValues": False,
            "migrationBundleDigest": bundle_digest,
            "migrationBundlePath": "keycloak/migrations/fresh-start-v1.json",
            "environmentRenderEvidencePath": "keycloak/realm-render-evidence.json",
        },
    }
    write(generated / "render-manifest.json", render_manifest)
    write(
        generated / "keycloak/realm-render-evidence.json",
        {
            "schemaVersion": "weave.keycloak-environment-render-evidence/v1",
            "supportSafe": True,
            "containsSecretValues": False,
            "environment": "dogfood",
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
            "candidateCommit": context.env["WEAVE_CANDIDATE_COMMIT"],
            "candidateManifestDigest": context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"],
            "specificationCommit": "f" * 40,
            "realmIdentity": realm_identity,
            "semanticReadbackDigest": None,
            "semanticReadbackVerified": False,
        },
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
        "keycloakVersion": "26.7.1",
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
        json.dumps({"realm_access": {"roles": ["weaver-runtime"]}, "resource_access": {"realm-management": {"roles": ["create-client"]}}}, separators=(",", ":")).encode()
    ).rstrip(b"=").decode()
    assert oauth_probe.access_token_role_projection(f"e30.{claims}.signature") == ({"weaver-runtime"}, {"realm-management": {"create-client"}})
    try:
        oauth_probe.access_token_role_projection("malformed")
    except oauth_probe.OAuthProbeError:
        pass
    else:
        raise AssertionError("malformed OAuth role projection was accepted")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        context, receipt = artifacts(root)
        render_path = context.generated_root / "render-manifest.json"
        current_render = json.loads(render_path.read_text(encoding="utf-8"))
        legacy_render = dict(current_render)
        legacy_render["schemaVersion"] = "weave.compose-render.v2"
        legacy_render["realmArtifacts"] = legacy_render.pop("deploymentArtifacts")
        write(render_path, legacy_render)
        rejected(lambda: migration_inputs(context))
        write(render_path, current_render)

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
                return_value={"backupVerified": True, "isolatedRestoreVerified": True, "cleanupVerified": True, "supportSafe": True, "containsSecretValues": False},
            ):
                proof_file = create_backup_proof(context)
        finally:
            if previous_candidate is None:
                os.environ.pop("WEAVE_CANDIDATE_COMMIT", None)
            else:
                os.environ["WEAVE_CANDIDATE_COMMIT"] = previous_candidate
        assert stat.S_IMODE(proof_file.stat().st_mode) == 0o600
        receipt["backupProofDigest"] = digest(proof_file.read_bytes())
        rejected(lambda: require_completed_migration(context))
        inputs = migration_inputs(context)
        write(inputs.receipt_file, receipt)
        require_completed_migration(context)

        evidence = inputs.artifact_root / "keycloak/realm-render-evidence.json"
        original_evidence = json.loads(evidence.read_text(encoding="utf-8"))
        tampered = dict(original_evidence)
        tampered["realmIdentity"] = {**original_evidence["realmIdentity"], "overlayDigest": "sha256:" + "9" * 64}
        write(evidence, tampered)
        rejected(lambda: require_completed_migration(context))
        write(evidence, original_evidence)

        inputs.receipt_file.unlink()
        plan_file = root / "fresh-start-plan.json"
        apply_file = root / "fresh-start-apply.json"
        operation_nonce = "fresh-start-0123456789"
        retired_generation = "legacy-generation"
        plan = {
            "schemaVersion": "weave.infra.fresh-start-plan.v1",
            "supportSafe": True,
            "environment": "persistent-dogfood",
            "stack": "weave",
            "retiredGeneration": retired_generation,
            "targetGeneration": context.env["WEAVE_RESOURCE_GENERATION"],
            "operationNonce": operation_nonce,
            "candidateCommit": context.env["WEAVE_CANDIDATE_COMMIT"],
            "candidateManifestDigest": context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"],
        }
        plan_payload = write_canonical(plan_file, plan)
        applied = {
            "schemaVersion": "weave.infra.fresh-start-apply-evidence.v1",
            "supportSafe": True,
            "environment": "persistent-dogfood",
            "stack": "weave",
            "retiredGeneration": retired_generation,
            "targetGeneration": context.env["WEAVE_RESOURCE_GENERATION"],
            "operationNonce": operation_nonce,
            "planSha256": hashlib.sha256(plan_payload).hexdigest(),
            "status": "removed-pending-target-recreation",
            "exclusionsVerified": True,
            "results": [{"status": "removed"}],
        }
        write_canonical(apply_file, applied)
        old_values = {key: os.environ.get(key) for key in ("WEAVE_CANDIDATE_COMMIT", "WEAVE_FRESH_START_PLAN", "WEAVE_FRESH_START_APPLY_EVIDENCE")}
        os.environ.update({"WEAVE_CANDIDATE_COMMIT": context.env["WEAVE_CANDIDATE_COMMIT"], "WEAVE_FRESH_START_PLAN": str(plan_file), "WEAVE_FRESH_START_APPLY_EVIDENCE": str(apply_file)})
        try:
            proof_file = create_backup_proof(context)
        finally:
            for key, value in old_values.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value
        fresh_proof = json.loads(proof_file.read_text(encoding="utf-8"))
        assert fresh_proof["schemaVersion"] == "weave.keycloak-realm-migration-fresh-start-proof/v1"
        assert "backupManifestSha256" not in fresh_proof
        receipt["backupProofDigest"] = digest(proof_file.read_bytes())
        write(inputs.receipt_file, receipt)
        require_completed_migration(context)

        receipt["bootstrapAuthorityNegativeReadbackVerified"] = False
        write(inputs.receipt_file, receipt)
        rejected(lambda: require_completed_migration(context))
        receipt["bootstrapAuthorityNegativeReadbackVerified"] = True
        receipt["firstRunOperations"] = ["unbounded-admin-mutation"]
        write(inputs.receipt_file, receipt)
        rejected(lambda: require_completed_migration(context))

    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert "identity-ops:" not in compose
    assert "kcadm" not in compose
    assert not (ROOT / "keycloak/identity_ops.py").exists()
    assert not (ROOT / "keycloak/Dockerfile.identity-ops").exists()
    print("Keycloak migration boundary contract tests passed")


if __name__ == "__main__":
    main()
