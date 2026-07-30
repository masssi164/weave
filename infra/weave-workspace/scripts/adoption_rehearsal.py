#!/usr/bin/env python3
"""Prove a private stack backup in an isolated namespace without hidden adoption."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import subprocess
import tarfile
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

from backup_runtime import VOLUME_ARTIFACTS, backup
from compose_env import ContractError, load_context
from legacy_secret_migration import migrate
from compose_env import canonical_json


def _digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            value.update(block)
    return value.hexdigest()


def _run(*arguments: str, input_file: Path | None = None) -> subprocess.CompletedProcess[bytes]:
    stream = input_file.open("rb") if input_file else None
    try:
        return subprocess.run(
            list(arguments),
            stdin=stream,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    finally:
        if stream:
            stream.close()


def _labels(namespace: str) -> list[str]:
    return [
        "--label",
        "com.massimotter.weave.managed=true",
        "--label",
        f"com.massimotter.weave.namespace={namespace}",
        "--label",
        "com.massimotter.weave.scope=isolated-restore-rehearsal",
    ]


def _member_inventory(archive: tarfile.TarFile, label: str) -> dict[str, object]:
    rows: list[dict[str, object]] = []
    regular_files = 0
    regular_bytes = 0
    for member in archive:
        candidate = PurePosixPath(member.name)
        link = PurePosixPath(member.linkname) if member.issym() or member.islnk() else None
        unsafe_link = link is not None and (link.is_absolute() or ".." in link.parts)
        if candidate.is_absolute() or ".." in candidate.parts or unsafe_link:
            raise ContractError(f"unsafe member in backup archive: {label}")
        if member.ischr() or member.isblk() or member.isfifo() or member.isdev():
            raise ContractError(f"unsupported special member in backup archive: {label}")
        normalized = str(candidate).removeprefix("./") or "."
        row: dict[str, object] = {
            "pathDigest": "sha256:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest(),
            "type": "file" if member.isfile() else "directory" if member.isdir() else "link",
            "mode": member.mode & 0o7777,
        }
        if member.isfile():
            source = archive.extractfile(member)
            if source is None:
                raise ContractError(f"regular member cannot be read: {label}")
            digest = hashlib.sha256()
            size = 0
            while block := source.read(1024 * 1024):
                digest.update(block)
                size += len(block)
            if size != member.size:
                raise ContractError(f"regular member is truncated: {label}")
            row.update({"bytes": size, "contentDigest": "sha256:" + digest.hexdigest()})
            regular_files += 1
            regular_bytes += size
        elif link is not None:
            row["linkTargetDigest"] = "sha256:" + hashlib.sha256(str(link).encode("utf-8")).hexdigest()
        rows.append(row)
    if not rows:
        raise ContractError(f"empty backup archive: {label}")
    rows.sort(key=lambda item: (str(item["pathDigest"]), str(item["type"])))
    return {
        "entryCount": len(rows),
        "regularFileCount": regular_files,
        "regularFileBytes": regular_bytes,
        "inventoryDigest": "sha256:" + hashlib.sha256(canonical_json(rows)).hexdigest(),
    }


def _archive_inventory(path: Path) -> dict[str, object]:
    with tarfile.open(path, "r:gz") as archive:
        return _member_inventory(archive, path.name)


def _volume_inventory(image: str, volume: str) -> dict[str, object]:
    process = subprocess.Popen(
        [
            "docker", "run", "--rm", "--read-only", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges:true",
            "--mount", f"type=volume,src={volume},dst=/source,readonly",
            "--entrypoint", "/bin/tar", image, "-C", "/source", "-cf", "-", ".",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if process.stdout is None:
        process.kill()
        raise ContractError("restored volume inventory stream is unavailable")
    try:
        with tarfile.open(fileobj=process.stdout, mode="r|") as archive:
            inventory = _member_inventory(archive, volume)
    except Exception:
        process.kill()
        process.wait()
        raise
    stderr = process.stderr.read() if process.stderr is not None else b""
    if process.wait() != 0:
        del stderr
        raise ContractError("restored volume inventory could not be read")
    del stderr
    return inventory


def _restore_volume(image: str, namespace: str, volume: str, backup_dir: Path, archive: str) -> None:
    _run("docker", "volume", "create", *_labels(namespace), volume)
    _run(
        "docker",
        "run",
        "--rm",
        "--read-only",
        "--cap-drop",
        "ALL",
        "--security-opt",
        "no-new-privileges:true",
        "--mount",
        f"type=volume,src={volume},dst=/target",
        "--mount",
        f"type=bind,src={backup_dir},dst=/backup,readonly",
        "--entrypoint",
        "/bin/sh",
        image,
        "-euc",
        f"tar -C /target -xzf /backup/{archive}",
    )


def _sanitize_admin_creation(source: Path, target: Path, administrator: str) -> None:
    create = re.compile(r'^CREATE ROLE ("(?:[^"]|"")*"|[A-Za-z_][A-Za-z0-9_$]*);$')

    def identifier(value: str) -> str:
        return value[1:-1].replace('""', '"') if value.startswith('"') else value.lower()

    removed = 0
    with source.open("r", encoding="utf-8") as reader, target.open("x", encoding="utf-8") as writer:
        os.chmod(target, 0o600)
        for line in reader:
            match = create.fullmatch(line.rstrip("\r\n"))
            if match and identifier(match.group(1)) == administrator:
                removed += 1
                continue
            writer.write(line)
    if removed != 1:
        raise ContractError("PostgreSQL dump did not contain exactly one administrator creation")


def _cleanup(namespace: str, container: str, network: str, volumes: list[str]) -> None:
    if not namespace.startswith("weave-restore-"):
        raise ContractError("isolated restore cleanup namespace is unsafe")
    subprocess.run(["docker", "container", "rm", "--force", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for volume in volumes:
        if volume.startswith(namespace + "-"):
            subprocess.run(["docker", "volume", "rm", volume], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if network == namespace + "-network":
        subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    remaining: list[str] = []
    for kind, name in (
        ("container", container),
        ("network", network),
        *((("volume", volume) for volume in volumes)),
    ):
        if subprocess.run(
            ["docker", kind, "inspect", name],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode == 0:
            remaining.append(f"{kind}:{name}")
    if remaining:
        raise ContractError("isolated restore cleanup left exact owned resources")


def _prepare_legacy_secret_continuity(context: object) -> tuple[Path, dict[str, object]]:
    """Migrate the literal legacy generation before it enters the backup set."""

    migration_path = context.generated_root / "adoption/legacy-secret-migration.json"
    if migration_path.is_symlink():
        raise ContractError("legacy secret migration receipt is a symlink")
    if migration_path.is_file():
        migration = json.loads(migration_path.read_text(encoding="utf-8"))
    else:
        legacy_bootstrap = context.generated_root / "bootstrap.env"
        if not legacy_bootstrap.is_file():
            raise ContractError(
                "existing runtime data has no verified literal legacy secret generation; "
                "refusing credential discontinuity"
            )
        migration = migrate(context, legacy_bootstrap)
        migration_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = migration_path.with_suffix(".tmp")
        temporary.write_text(json.dumps(migration, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.chmod(temporary, 0o600)
        os.replace(temporary, migration_path)
    proofs = migration.get("continuityProofs")
    nextcloud_proof = proofs[0] if isinstance(proofs, list) and len(proofs) == 1 else None
    if (
        migration.get("schemaVersion") != "weave.legacy-secret-migration-receipt.v1"
        or migration.get("supportSafe") is not True
        or migration.get("containsSecretValues") is not False
        or "keycloak-nextcloud" not in migration.get("generationFingerprints", {})
        or not isinstance(nextcloud_proof, dict)
        or nextcloud_proof.get("kind") != "nextcloud-user-oidc-decryption"
        or nextcloud_proof.get("providerIdentifier") != "keycloak"
        or nextcloud_proof.get("clientId") != "nextcloud"
        or nextcloud_proof.get("valueExposed") is not False
        or nextcloud_proof.get("supportSafe") is not True
    ):
        raise ContractError("legacy secret migration receipt is malformed")
    return migration_path, migration


def rehearse(
    context: object, backup_dir: Path, purpose: str = "adoption"
) -> dict[str, object]:
    if purpose not in {"adoption", "fresh-start"}:
        raise ContractError("backup rehearsal purpose is unsupported")
    manifest_path = backup_dir / "BackupManifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    candidate = os.environ["WEAVE_CANDIDATE_COMMIT"]
    if (
        manifest.get("schemaVersion") != "weave.compose-private-backup.v2"
        or manifest.get("candidateCommit") != candidate
        or manifest.get("profile") != context.profile
        or manifest.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
    ):
        raise ContractError("private backup manifest is stale or belongs to another deployment")
    artifacts = {item["path"]: item for item in manifest.get("artifacts", [])}
    for name, item in artifacts.items():
        path = backup_dir / name
        if not path.is_file() or _digest(path) != item.get("sha256"):
            raise ContractError(f"private backup artifact failed checksum validation: {name}")
        if name.endswith(".tgz"):
            _archive_inventory(path)
    namespace = "weave-restore-" + secrets.token_hex(6)
    network = namespace + "-network"
    db_volume = namespace + "-db-data"
    db_container = namespace + "-postgres"
    volumes = [db_volume]
    _run("docker", "network", "create", "--internal", *_labels(namespace), network)
    try:
        restored_inventories: list[dict[str, object]] = []
        for variable, archive, _kind in VOLUME_ARTIFACTS:
            volume = f"{namespace}-{variable.lower().removeprefix('weave_').removesuffix('_volume').replace('_', '-')}"
            volumes.append(volume)
            expected_inventory = _archive_inventory(backup_dir / archive)
            _restore_volume(context.env["WEAVE_POSTGRES_IMAGE"], namespace, volume, backup_dir, archive)
            observed_inventory = _volume_inventory(context.env["WEAVE_POSTGRES_IMAGE"], volume)
            if observed_inventory != expected_inventory:
                raise ContractError(f"restored provider volume inventory differs from backup artifact: {archive}")
            restored_inventories.append(
                {"artifact": archive, **expected_inventory, "verified": True}
            )
        with tempfile.TemporaryDirectory(prefix="weave-restore-") as temporary:
            password = Path(temporary) / "postgres-admin-password"
            descriptor = os.open(
                password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
            )
            with os.fdopen(descriptor, "w", encoding="ascii") as output:
                output.write(secrets.token_urlsafe(48))
            _run("docker", "volume", "create", *_labels(namespace), db_volume)
            _run(
                "docker",
                "run",
                "--detach",
                "--name",
                db_container,
                "--network",
                network,
                *_labels(namespace),
                "--env",
                f"POSTGRES_USER={context.env['WEAVE_DB_ADMIN_USERNAME']}",
                "--env",
                "POSTGRES_DB=postgres",
                "--env",
                "POSTGRES_PASSWORD_FILE=/run/secrets/postgres-admin-password",
                "--mount",
                f"type=volume,src={db_volume},dst=/var/lib/postgresql/data",
                "--mount",
                f"type=bind,src={password},dst=/run/secrets/postgres-admin-password,readonly",
                context.env["WEAVE_POSTGRES_IMAGE"],
            )
            ready = False
            for _ in range(60):
                result = subprocess.run(
                    [
                        "docker",
                        "exec",
                        db_container,
                        "pg_isready",
                        "-U",
                        context.env["WEAVE_DB_ADMIN_USERNAME"],
                        "-d",
                        "postgres",
                    ],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
                if result.returncode == 0:
                    ready = True
                    break
                time.sleep(1)
            if not ready:
                raise ContractError("isolated restore PostgreSQL did not become ready")
            sanitized = Path(temporary) / "postgres.sql"
            _sanitize_admin_creation(
                backup_dir / "postgres.sql",
                sanitized,
                context.env["WEAVE_DB_ADMIN_USERNAME"],
            )
            _run(
                "docker",
                "exec",
                "--interactive",
                db_container,
                "psql",
                "--no-psqlrc",
                "--set=ON_ERROR_STOP=1",
                "-U",
                context.env["WEAVE_DB_ADMIN_USERNAME"],
                "-d",
                "postgres",
                input_file=sanitized,
            )
            expected_databases = {
                context.env["WEAVE_BACKEND_DB_NAME"],
                context.env["WEAVE_KEYCLOAK_DB_NAME"],
                context.env["WEAVE_MAS_DB_NAME"],
                context.env["WEAVE_SYNAPSE_DB_NAME"],
                context.env["WEAVE_NEXTCLOUD_DB_NAME"],
            }
            query = (
                "SELECT datname FROM pg_database WHERE datname IN ("
                + ",".join("'" + name + "'" for name in sorted(expected_databases))
                + ") ORDER BY datname"
            )
            observed = _run(
                "docker",
                "exec",
                db_container,
                "psql",
                "--no-psqlrc",
                "-U",
                context.env["WEAVE_DB_ADMIN_USERNAME"],
                "-d",
                "postgres",
                "-Atqc",
                query,
            ).stdout.decode("utf-8").splitlines()
            if set(observed) != expected_databases:
                raise ContractError(
                    "isolated restore is missing one or more service databases"
                )
            realm_count = _run(
                "docker",
                "exec",
                db_container,
                "psql",
                "--no-psqlrc",
                "-U",
                context.env["WEAVE_DB_ADMIN_USERNAME"],
                "-d",
                context.env["WEAVE_KEYCLOAK_DB_NAME"],
                "-Atqc",
                "SELECT count(*) FROM realm WHERE name='weave'",
            ).stdout.decode("ascii").strip()
            if realm_count != "1":
                raise ContractError(
                    "isolated restore does not contain exactly one Weave realm"
                )
        manifest_digest = _digest(manifest_path)
        resources = [{"kind": "network", "name": context.env["WEAVE_DOCKER_NETWORK"]}]
        for key in (
            "WEAVE_CADDY_DATA_VOLUME",
            "WEAVE_CADDY_CONFIG_VOLUME",
            "WEAVE_DB_DATA_VOLUME",
            "WEAVE_KEYCLOAK_DATA_VOLUME",
            "WEAVE_MAILPIT_DATA_VOLUME",
            "WEAVE_NEXTCLOUD_DATA_VOLUME",
            "WEAVE_SYNAPSE_DATA_VOLUME",
            "WEAVE_MATRIX_APPSERVICE_VOLUME",
        ):
            resources.append({"kind": "volume", "name": context.env[key]})
        common = {
            "profile": context.profile,
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
            "candidateCommit": candidate,
            "backupRef": f"evidence:private-backup:sha256:{manifest_digest}",
            "databaseFingerprint": manifest["databaseFingerprint"],
            "backupVerified": True,
            "isolatedRestoreVerified": True,
            "restoredVolumeInventories": restored_inventories,
            "isolatedNamespace": namespace,
            "verifiedServiceDatabaseCount": len(expected_databases),
            "verifiedAt": datetime.now(timezone.utc).isoformat().replace(
                "+00:00", "Z"
            ),
            "supportSafe": True,
            "containsSecretValues": False,
        }
        if purpose == "adoption":
            migration_path, _migration = _prepare_legacy_secret_continuity(context)
            receipt = {
                "schemaVersion": "weave.compose-adoption-receipt.v1",
                **common,
                "secretContinuityVerified": True,
                "secretMigrationReceiptRef": (
                    "evidence:legacy-secret-migration:sha256:"
                    + _digest(migration_path)
                ),
                "resources": sorted(
                    resources, key=lambda item: (item["kind"], item["name"])
                ),
            }
        else:
            receipt = {
                "schemaVersion": "weave.fresh-start-private-backup-rehearsal.v1",
                **common,
                "recoveryBoundary": "private-backup-only-no-adoption",
                "legacyStateMigrated": False,
                "adoptionAuthorized": False,
                "privateArtifactCount": len(artifacts),
                "restoredProviderVolumeCount": len(restored_inventories),
            }
    finally:
        _cleanup(namespace, db_container, network, volumes)
    receipt["cleanupVerified"] = True
    return receipt


def execute(
    context: object, purpose: str, receipt_output: Path | None = None
) -> Path:
    if purpose == "adoption":
        # Adoption deliberately preserves the former credential generation.
        # Fresh Start never calls this migration path.
        _prepare_legacy_secret_continuity(context)
    backup_dir = backup(context)
    receipt = rehearse(context, backup_dir, purpose)
    output = (
        context.generated_root / "adoption/adoption-receipt.json"
        if purpose == "adoption"
        else receipt_output
        or backup_dir / "FreshStartBackupRehearsal.json"
    )
    output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    output.write_text(
        json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    os.chmod(output, 0o600)
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "test", "prod"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    parser.add_argument(
        "--purpose", choices=("adoption", "fresh-start"), default="adoption"
    )
    parser.add_argument("--receipt-output", type=Path)
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        if args.purpose == "adoption" and args.receipt_output is not None:
            raise ContractError("adoption receipt output is fixed by the deployment context")
        output = execute(context, args.purpose, args.receipt_output)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError, tarfile.TarError) as error:
        prefix = (
            "WEAVE_ADOPTION_REHEARSAL_ERROR"
            if args.purpose == "adoption"
            else "WEAVE_FRESH_START_BACKUP_REHEARSAL_ERROR"
        )
        print(f"{prefix} {error}", file=os.sys.stderr)
        return 1
    operation = (
        "adoption-rehearsal"
        if args.purpose == "adoption"
        else "fresh-start-backup-rehearsal"
    )
    print(f"{operation}: verified exact private backup; receipt={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
