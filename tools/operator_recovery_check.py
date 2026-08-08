#!/usr/bin/env python3
"""Validate Sprint 26 operator recovery evidence and release blockers."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "operator-recovery"
BACKUP_MANIFEST = ARTIFACT_DIR / "backup-manifest.disposable.json"
RESTORE_RECEIPT = ARTIFACT_DIR / "restore-receipt.disposable.json"
REDACTION_REPORT = ARTIFACT_DIR / "support-redaction-report.disposable.json"
DOMAIN_DATA_HASH_PROOF = ARTIFACT_DIR / "domain-data-hashes.disposable.json"
SCOREBOARD = ARTIFACT_DIR / "sprint-26-scoreboard.json"
LIMITATIONS = ROOT / "docs" / "operator-recovery-known-limitations.md"
EVIDENCE_REPORT = ROOT / "docs" / "evidence" / "operator-recovery-report.md"

REQUIRED_BACKUP_ARTIFACTS = {
    "postgres.sql": "postgres-consistency-dump",
    "nextcloud-data.tgz": "files-calendar-provider-data",
    "synapse-data.tgz": "matrix-media-and-local-state",
    "caddy-data.tgz": "gateway-runtime-state",
    "caddy-config.tgz": "gateway-config-state",
    "keycloak-data.tgz": "keycloak-runtime-state",
    "matrix-appservice.tgz": "matrix-appservice-runtime",
    "private-config-secrets.tgz": "private-config-secretrefs",
}
REQUIRED_REDACTION_CHECKS = {
    "tokens_and_authorization_headers",
    "cookies",
    "private_keys",
    "secret_refs",
    "provider_urls",
    "private_messages_file_contents_weaver_memory",
    "negative_fixture_detects_unsafe_content",
}
FORBIDDEN_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"authorization:\s*(bearer|basic)\s+[^<\s]",
        r"cookie:\s*[^<\s]",
        r"set-cookie:\s*[^<\s]",
        r"-----BEGIN [^-]*PRIVATE KEY-----",
        r"\b(?:ghp|gho|ghu|ghs|ghr|github_pat|glpat|xox[baprs])-[-_A-Za-z0-9]{20,}",
        r"\bsecretref://[^\s<]+",
        r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b",
        r"https?://[^\s\"]+:[^\s\"]+@",
    ]
]


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def fail(message: str) -> None:
    print(f"operator-recovery-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {rel(path)}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {rel(path)}: {error}")
    if not isinstance(value, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return value


def assert_support_safe(value: Any, label: str, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            assert_support_safe(child, label, (*path, key))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            assert_support_safe(child, label, (*path, str(index)))
    elif isinstance(value, str):
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains support-unsafe value at {'.'.join(path)}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_backup_manifest(manifest: dict[str, Any], *, fixture: bool) -> None:
    if manifest.get("schemaVersion") != "weave.compose-private-backup.v3":
        fail("BackupManifest schema mismatch")
    if manifest.get("supportSafe") is not False or manifest.get("containsSecretsOrMemberData") is not True:
        fail("BackupManifest must declare backup artifacts private")
    if not isinstance(manifest.get("candidateCommit"), str) or not re.fullmatch(r"[0-9a-f]{40}", manifest["candidateCommit"]):
        fail("BackupManifest must bind an exact candidate commit")
    if manifest.get("profile") not in {"dogfood", "prod"}:
        fail("BackupManifest profile must be dogfood or prod")
    if not isinstance(manifest.get("composeProject"), str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{1,62}", manifest["composeProject"]):
        fail("BackupManifest Compose project is invalid")
    if not isinstance(manifest.get("databaseFingerprint"), str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", manifest["databaseFingerprint"]):
        fail("BackupManifest database fingerprint is invalid")
    if not isinstance(manifest.get("candidateManifestDigest"), str) or not re.fullmatch(
        r"sha256:[0-9a-f]{64}", manifest["candidateManifestDigest"]
    ):
        fail("BackupManifest candidate manifest digest is invalid")
    if not isinstance(manifest.get("postgresDumpClientImage"), str) or not re.fullmatch(
        r"postgres@sha256:[0-9a-f]{64}",
        manifest["postgresDumpClientImage"],
    ):
        fail("BackupManifest PostgreSQL dump client image is invalid")
    postgres_databases = manifest.get("postgresDatabases")
    if (
        not isinstance(postgres_databases, list)
        or "postgres" not in postgres_databases
        or postgres_databases != sorted(set(postgres_databases))
        or any(
            not isinstance(name, str)
            or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]{0,62}", name)
            for name in postgres_databases
        )
    ):
        fail("BackupManifest PostgreSQL inventory is invalid")
    expected_database_digest = "sha256:" + hashlib.sha256(
        json.dumps(
            postgres_databases,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        ).encode("utf-8")
    ).hexdigest()
    if manifest.get("postgresDatabaseInventoryDigest") != expected_database_digest:
        fail("BackupManifest PostgreSQL inventory digest is invalid")
    if not isinstance(manifest.get("quiescedServices"), list) or not isinstance(manifest.get("runtimeInventory"), list):
        fail("BackupManifest runtime consistency boundary is missing")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        fail("BackupManifest artifacts must be a list")
    by_path: dict[str, dict[str, Any]] = {}
    for item in artifacts:
        if not isinstance(item, dict):
            fail("BackupManifest artifact entries must be objects")
        path = item.get("path")
        if not isinstance(path, str) or path not in REQUIRED_BACKUP_ARTIFACTS:
            fail("BackupManifest artifact entry missing path")
        if path in by_path:
            fail(f"duplicate backup artifact {path}")
        by_path[path] = item
        if item.get("kind") != REQUIRED_BACKUP_ARTIFACTS[path]:
            fail(f"backup artifact {path} has the wrong canonical kind")
        checksum = item.get("sha256")
        if not isinstance(checksum, str) or not re.fullmatch(r"[0-9a-f]{64}", checksum):
            fail(f"backup artifact {path} missing sha256 checksum")
        if not isinstance(item.get("bytes"), int) or item["bytes"] <= 0:
            fail(f"backup artifact {path} must include positive bytes")
    if set(by_path) != set(REQUIRED_BACKUP_ARTIFACTS):
        fail("BackupManifest artifact inventory does not match the canonical Compose v3 set")
    if not fixture:
        # In a real evidence directory the private BackupManifest checksums and sizes
        # must match the backup artifacts next to the manifest.
        base = Path(str(manifest.get("_source", ""))).parent
        for name, item in by_path.items():
            target = base / name
            if not target.exists():
                target = base / "backup-artifacts" / name
            if not target.exists() or target.stat().st_size <= 0:
                fail(f"real evidence missing non-empty backup artifact {name}")
            actual_bytes = target.stat().st_size
            if item["bytes"] != actual_bytes:
                fail(f"real evidence backup artifact {name} bytes mismatch: manifest={item['bytes']} actual={actual_bytes}")
            actual_sha256 = sha256_file(target)
            if item["sha256"] != actual_sha256:
                fail(f"real evidence backup artifact {name} sha256 mismatch")


def validate_restore_receipt(receipt: dict[str, Any], *, require_live: bool) -> None:
    if receipt.get("schemaVersion") != "weave.compose-restore-receipt.v2":
        fail("RestoreReceipt schema mismatch")
    if receipt.get("supportSafe") is not True:
        fail("RestoreReceipt must be support-safe")
    binding = receipt.get("backupBinding")
    if not isinstance(binding, dict):
        fail("RestoreReceipt must bind the private backup without exposing its path")
    for key in ("manifestSha256", "backupIdSha256"):
        if not isinstance(binding.get(key), str) or not re.fullmatch(r"[0-9a-f]{64}", binding[key]):
            fail(f"RestoreReceipt backup binding has invalid {key}")
    if not isinstance(binding.get("candidateCommit"), str) or not re.fullmatch(r"[0-9a-f]{40}", binding["candidateCommit"]):
        fail("RestoreReceipt backup binding has invalid candidateCommit")
    assert_support_safe(receipt, "RestoreReceipt")
    checks = receipt.get("checks")
    if not isinstance(checks, list) or not checks:
        fail("RestoreReceipt must list checks")
    check_map = {item.get("name"): item.get("status") for item in checks if isinstance(item, dict)}
    for name in ["backup_integrity_verified", "post_restore_operator_check", "domain_data_recovered"]:
        if name not in check_map:
            fail(f"RestoreReceipt missing check {name}")
    if require_live:
        if receipt.get("validationMode") not in {"post_restore_live", "disposable_stack_rehearsal"}:
            fail("live release evidence requires post_restore_live/disposable_stack_rehearsal validationMode")
        if receipt.get("destroyStep", {}).get("performed") is not True:
            fail("live release evidence requires destroyStep.performed=true")
        if receipt.get("provesRestoredDomainData") is not True:
            fail("live release evidence must prove restored domain data")
        if receipt.get("releaseEligible") is not True:
            fail("live release evidence must be releaseEligible=true")
        if any(check_map.get(name) != "passed" for name in ["backup_integrity_verified", "post_restore_operator_check", "domain_data_recovered"]):
            fail("live release evidence requires all restore checks passed")


def validate_domain_data_hash_proof(proof: dict[str, Any]) -> None:
    if proof.get("artifactKind") != "weave-disposable-domain-data-hash-proof-v1":
        fail("domain data hash proof kind mismatch")
    if proof.get("supportSafe") is not True:
        fail("domain data hash proof must be support-safe")
    assert_support_safe(proof, "domain data hash proof")
    if proof.get("matched") is not True:
        fail("domain data hash proof must declare matched=true")
    seed = proof.get("seedHashes")
    restored = proof.get("restoredHashes")
    if not isinstance(seed, dict) or not isinstance(restored, dict) or not seed or seed != restored:
        fail("domain data hash proof seed/restored hashes must match")
    required_fragments = ["nextcloud/", "synapse/", "keycloak/", "caddy_data/", "caddy_config/"]
    for fragment in required_fragments:
        if not any(isinstance(path, str) and path.startswith(fragment) for path in seed):
            fail(f"domain data hash proof missing {fragment} fixture data")


def validate_redaction_report(report: dict[str, Any]) -> None:
    if report.get("artifactKind") != "weave-support-bundle-redaction-report-v1":
        fail("redaction report kind mismatch")
    if report.get("issue") != 640:
        fail("redaction report must link issue #640")
    if report.get("supportSafe") is not True:
        fail("redaction report must be support-safe")
    assert_support_safe(report, "redaction report")
    if report.get("unsafeContentDetected") is not False:
        fail("redaction report must declare no unsafe content detected")
    checks = report.get("checks")
    if not isinstance(checks, list):
        fail("redaction report checks must be a list")
    check_names = {item.get("name") for item in checks if isinstance(item, dict)}
    missing = REQUIRED_REDACTION_CHECKS - check_names
    if missing:
        fail(f"redaction report missing checks: {', '.join(sorted(missing))}")
    negative = next((item for item in checks if isinstance(item, dict) and item.get("name") == "negative_fixture_detects_unsafe_content"), {})
    if negative.get("status") != "passed":
        fail("negative redaction fixture must pass")


def validate_scoreboard(scoreboard: dict[str, Any]) -> None:
    if scoreboard.get("scoreboardKind") != "weave-sprint-26-operator-recovery-scoreboard":
        fail("scoreboard kind mismatch")
    if set(scoreboard.get("issues", [])) != {639, 640, 641, 642}:
        fail("scoreboard must cover issues #639-#642")
    evidence = scoreboard.get("evidence", {})
    for expected in [BACKUP_MANIFEST, RESTORE_RECEIPT, REDACTION_REPORT, DOMAIN_DATA_HASH_PROOF, LIMITATIONS]:
        if str(expected.relative_to(ROOT)) not in evidence.values():
            fail(f"scoreboard missing evidence ref {expected.relative_to(ROOT)}")
    gate = scoreboard.get("claimGate", {})
    blockers = gate.get("releaseBlockers", [])
    if gate.get("operatorRecoveryClaimAllowed") is not True:
        fail("scoreboard must allow only the scoped disposable restore proof claim")
    if any(isinstance(item, dict) and item.get("blocksRelease") is True for item in blockers):
        fail("scoreboard must not keep a release blocker after disposable restore proof passes")


def validate_docs() -> None:
    for path in [LIMITATIONS, EVIDENCE_REPORT]:
        if not path.exists():
            fail(f"missing {rel(path)}")
    limitations = LIMITATIONS.read_text(encoding="utf-8")
    for fragment in ["History", "Attachments/media", "Provider-specific data", "E2EE archives", "Conflicts", "Weaver memory", "Release wording rule"]:
        if fragment not in limitations:
            fail(f"KnownLimitations missing {fragment!r}")
    report = EVIDENCE_REPORT.read_text(encoding="utf-8")
    for path in [BACKUP_MANIFEST, RESTORE_RECEIPT, REDACTION_REPORT, DOMAIN_DATA_HASH_PROOF, SCOREBOARD, LIMITATIONS]:
        if str(path.relative_to(ROOT)) not in report:
            fail(f"evidence report missing {path.relative_to(ROOT)}")


def load_with_source(path: Path) -> dict[str, Any]:
    data = load_json(path)
    data["_source"] = str(path)
    return data


def validate_checked_in_fixtures() -> None:
    manifest = load_with_source(BACKUP_MANIFEST)
    receipt = load_json(RESTORE_RECEIPT)
    report = load_json(REDACTION_REPORT)
    proof = load_json(DOMAIN_DATA_HASH_PROOF)
    scoreboard = load_json(SCOREBOARD)
    validate_backup_manifest(manifest, fixture=True)
    validate_restore_receipt(receipt, require_live=True)
    validate_redaction_report(report)
    validate_domain_data_hash_proof(proof)
    validate_scoreboard(scoreboard)
    validate_docs()
    print("operator-recovery-check: ok disposable_restore_proof=release_eligible")
    print("SPRINT26_OPERATOR_RECOVERY_GUARD")


def validate_evidence_dir(path: Path) -> None:
    manifest = load_with_source(path / "BackupManifest.json")
    receipt = load_json(path / "RestoreReceipt.json")
    redaction = load_json(path / "support-redaction-report.json")
    validate_backup_manifest(manifest, fixture=False)
    validate_restore_receipt(receipt, require_live=True)
    validate_redaction_report(redaction)
    print(f"operator-recovery-check: ok live_evidence_dir={path} restore_proof=release_eligible")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", type=Path, help="Validate a real Compose v3 operator evidence directory: private BackupManifest.json plus private backup artifacts, support-safe RestoreReceipt.json, and support-safe support-redaction-report.json.")
    args = parser.parse_args()
    if args.evidence_dir:
        validate_evidence_dir(args.evidence_dir)
    else:
        validate_checked_in_fixtures()


if __name__ == "__main__":
    main()
