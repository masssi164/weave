#!/usr/bin/env python3
"""Fixture tests for the Sprint 26 operator recovery gate."""

from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECK = ROOT / "tools" / "operator_recovery_check.py"
FIXTURE_DIR = ROOT / "release" / "provider-lab" / "operator-recovery"
REQUIRED_ARTIFACTS = {
    "postgres.sql": "postgres-consistency-dump",
    "nextcloud-data.tgz": "files-calendar-provider-data",
    "synapse-data.tgz": "matrix-media-and-local-state",
    "caddy-data.tgz": "gateway-runtime-state",
    "caddy-config.tgz": "gateway-config-state",
    "keycloak-data.tgz": "keycloak-runtime-state",
    "matrix-appservice.tgz": "matrix-appservice-runtime",
    "private-config-secrets.tgz": "private-config-secretrefs",
}


def run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["python3", str(CHECK), *args], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    default = run()
    assert default.returncode == 0, default.stderr
    assert "disposable_restore_proof=release_eligible" in default.stdout

    with tempfile.TemporaryDirectory() as tmp:
        evidence = Path(tmp)
        manifest = json.loads((FIXTURE_DIR / "backup-manifest.fixture.json").read_text(encoding="utf-8"))
        manifest["artifacts"] = []
        for name, kind in REQUIRED_ARTIFACTS.items():
            artifact = evidence / name
            artifact.write_text(f"fixture {name}\n", encoding="utf-8")
            manifest["artifacts"].append({
                "path": name,
                "kind": kind,
                "sha256": sha256_file(artifact),
                "bytes": artifact.stat().st_size,
            })
        write_json(evidence / "BackupManifest.json", manifest)

        receipt = json.loads((FIXTURE_DIR / "restore-receipt.fixture.json").read_text(encoding="utf-8"))
        receipt.update({
            "validationMode": "disposable_stack_rehearsal",
            "status": "passed",
            "destroyStep": {"performed": True, "scope": "approved disposable fixture state"},
            "checks": [
                {"name": "backup_integrity_verified", "status": "passed"},
                {"name": "post_restore_operator_check", "status": "passed"},
                {"name": "domain_data_recovered", "status": "passed"},
            ],
            "provesRestoredDomainData": True,
            "releaseEligible": True,
        })
        write_json(evidence / "RestoreReceipt.json", receipt)
        shutil.copyfile(FIXTURE_DIR / "support-redaction-report.fixture.json", evidence / "support-redaction-report.json")

        passing = run("--evidence-dir", str(evidence))
        assert passing.returncode == 0, passing.stderr
        assert "restore_proof=release_eligible" in passing.stdout

        manifest["artifacts"][0]["sha256"] = "0" * 64
        write_json(evidence / "BackupManifest.json", manifest)
        checksum_mismatch = run("--evidence-dir", str(evidence))
        assert checksum_mismatch.returncode != 0
        assert "sha256 mismatch" in checksum_mismatch.stderr

        first_artifact = next(iter(REQUIRED_ARTIFACTS))
        manifest["artifacts"][0]["sha256"] = sha256_file(evidence / first_artifact)
        manifest["artifacts"][0]["bytes"] += 1
        write_json(evidence / "BackupManifest.json", manifest)
        bytes_mismatch = run("--evidence-dir", str(evidence))
        assert bytes_mismatch.returncode != 0
        assert "bytes mismatch" in bytes_mismatch.stderr

        manifest["artifacts"][0]["bytes"] = (evidence / first_artifact).stat().st_size
        write_json(evidence / "BackupManifest.json", manifest)
        (evidence / "postgres.sql").unlink()
        missing_artifact = run("--evidence-dir", str(evidence))
        assert missing_artifact.returncode != 0
        assert "real evidence missing non-empty backup artifact postgres.sql" in missing_artifact.stderr

    with tempfile.TemporaryDirectory() as tmp:
        evidence = Path(tmp)
        manifest = json.loads((FIXTURE_DIR / "backup-manifest.fixture.json").read_text(encoding="utf-8"))
        manifest["artifacts"] = []
        for name, kind in REQUIRED_ARTIFACTS.items():
            artifact = evidence / name
            artifact.write_text(f"fixture {name}\n", encoding="utf-8")
            manifest["artifacts"].append({
                "path": name,
                "kind": kind,
                "sha256": sha256_file(artifact),
                "bytes": artifact.stat().st_size,
            })
        write_json(evidence / "BackupManifest.json", manifest)
        shutil.copyfile(FIXTURE_DIR / "restore-receipt.fixture.json", evidence / "RestoreReceipt.json")
        shutil.copyfile(FIXTURE_DIR / "support-redaction-report.fixture.json", evidence / "support-redaction-report.json")
        blocked = run("--evidence-dir", str(evidence))
        assert blocked.returncode != 0
        assert "live release evidence requires" in blocked.stderr

    print("operator recovery check tests passed")


if __name__ == "__main__":
    main()
