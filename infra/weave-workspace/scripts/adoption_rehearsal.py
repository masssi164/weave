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
import sys
import tarfile
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

sys.path.insert(
    0, str(Path(__file__).resolve().parents[3] / "tools")
)
from private_backup_integrity import (  # noqa: E402
    IntegrityError,
    validate_backup,
)

from backup_runtime import VOLUME_ARTIFACTS, active_volume_artifacts, backup
from compose_env import ContractError, load_context
from compose_runtime import active_volume_keys
from legacy_secret_migration import migrate
from compose_env import canonical_json
from recovery_receipt import (
    ReceiptContractError,
    database_inventory_digest,
    validate_receipt,
)


def _digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            value.update(block)
    return value.hexdigest()


def _validate_private_backup(
    backup_dir: Path, context: object
) -> dict[str, object]:
    expected_artifacts = {
        "postgres.sql",
        "private-config-secrets.tgz",
        *(archive for _variable, archive, _kind in active_volume_artifacts(context)),
    }
    try:
        return validate_backup(
            backup_dir, expected_artifacts=expected_artifacts
        )
    except IntegrityError as error:
        raise ContractError(
            "private backup failed its closed v3 integrity contract"
        ) from error


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


def _member_inventory(
    archive: tarfile.TarFile, label: str, *, require_root: bool = False
) -> dict[str, object]:
    rows: list[dict[str, object]] = []
    regular_files = 0
    regular_bytes = 0
    root_metadata: dict[str, int] | None = None
    normalized_paths: set[str] = set()
    symlink_paths: set[str] = set()
    for member in archive:
        candidate = PurePosixPath(member.name)
        link = PurePosixPath(member.linkname) if member.issym() or member.islnk() else None
        unsafe_link = link is not None and (link.is_absolute() or ".." in link.parts)
        if candidate.is_absolute() or ".." in candidate.parts or unsafe_link:
            raise ContractError(f"unsafe member in backup archive: {label}")
        if member.ischr() or member.isblk() or member.isfifo() or member.isdev():
            raise ContractError(f"unsupported special member in backup archive: {label}")
        if not (
            member.isfile()
            or member.isdir()
            or member.issym()
            or member.islnk()
        ):
            raise ContractError(
                f"unsupported member type in backup archive: {label}"
            )
        normalized = str(candidate).removeprefix("./") or "."
        if normalized in normalized_paths:
            raise ContractError(f"duplicate member in backup archive: {label}")
        normalized_paths.add(normalized)
        if (
            not isinstance(member.uid, int)
            or isinstance(member.uid, bool)
            or member.uid < 0
            or member.uid > 2_147_483_647
            or not isinstance(member.gid, int)
            or isinstance(member.gid, bool)
            or member.gid < 0
            or member.gid > 2_147_483_647
        ):
            raise ContractError(f"invalid numeric ownership in backup archive: {label}")
        if member.isfile() and member.mode & 0o6000:
            raise ContractError(
                f"privileged regular-file mode in backup archive: {label}"
            )
        row: dict[str, object] = {
            "pathDigest": "sha256:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest(),
            "type": "file" if member.isfile() else "directory" if member.isdir() else "link",
            "mode": member.mode & 0o7777,
            "uid": member.uid,
            "gid": member.gid,
        }
        if normalized == ".":
            if not member.isdir() or root_metadata is not None:
                raise ContractError(f"backup archive has an invalid root entry: {label}")
            root_metadata = {
                "mode": member.mode & 0o7777,
                "uid": member.uid,
                "gid": member.gid,
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
            if member.issym():
                symlink_paths.add(normalized)
        rows.append(row)
    if not rows:
        raise ContractError(f"empty backup archive: {label}")
    if require_root and root_metadata is None:
        raise ContractError(
            f"provider-volume backup archive has no root directory: {label}"
        )
    for normalized in normalized_paths:
        candidate = PurePosixPath(normalized)
        if any(str(parent) in symlink_paths for parent in candidate.parents):
            raise ContractError(
                f"backup archive traverses a parent symlink: {label}"
            )
    rows.sort(key=lambda item: (str(item["pathDigest"]), str(item["type"])))
    return {
        "entryCount": len(rows),
        "regularFileCount": regular_files,
        "regularFileBytes": regular_bytes,
        "inventoryDigest": "sha256:" + hashlib.sha256(canonical_json(rows)).hexdigest(),
        "rootMetadata": root_metadata,
    }


def _archive_inventory(
    path: Path, *, require_root: bool = False
) -> dict[str, object]:
    with tarfile.open(path, "r:gz") as archive:
        return _member_inventory(
            archive, path.name, require_root=require_root
        )


def _verify_restore_helper(image: str) -> None:
    result = _run(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--read-only",
        "--cap-drop",
        "ALL",
        "--security-opt",
        "no-new-privileges:true",
        "--entrypoint",
        "/bin/tar",
        image,
        "--version",
    )
    if not result.stdout.startswith(b"tar (GNU tar) "):
        raise ContractError(
            "isolated provider-volume restore requires the pinned GNU tar helper"
        )


def _volume_inventory(image: str, volume: str) -> dict[str, object]:
    process = subprocess.Popen(
        [
            "docker", "run", "--rm", "--network", "none", "--read-only",
            "--cap-drop", "ALL", "--cap-add", "DAC_READ_SEARCH",
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


def _restore_volume(
    image: str,
    namespace: str,
    volume: str,
    backup_dir: Path,
    archive: str,
    root_metadata: object,
) -> None:
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]*\.tgz", archive):
        raise ContractError("backup archive name is unsafe")
    if not isinstance(root_metadata, dict):
        raise ContractError("volume backup archive root metadata is missing")
    values: list[int] = []
    for field, maximum in (("uid", 2_147_483_647), ("gid", 2_147_483_647), ("mode", 0o7777)):
        value = root_metadata.get(field)
        if (
            not isinstance(value, int)
            or isinstance(value, bool)
            or value < 0
            or value > maximum
        ):
            raise ContractError(f"backup archive root {field} is invalid")
        values.append(value)
    uid, gid, mode = values
    _run("docker", "volume", "create", *_labels(namespace), volume)
    _run(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--read-only",
        "--cap-drop",
        "ALL",
        "--cap-add",
        "CHOWN",
        "--cap-add",
        "DAC_OVERRIDE",
        "--cap-add",
        "FOWNER",
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
        (
            f"tar -C /target --strip-components 1 --numeric-owner "
            f"--same-owner --same-permissions --delay-directory-restore "
            f"-xzf /backup/{archive} && "
            f"chown {uid}:{gid} /target && chmod {mode:04o} /target"
        ),
    )


def _wait_for_final_postgres(
    container: str, administrator: str, attempts: int = 120
) -> None:
    for _ in range(attempts):
        final_process = subprocess.run(
            [
                "docker",
                "exec",
                container,
                "/bin/sh",
                "-euc",
                (
                    'executable="$(tr \'\\000\' \'\\n\' '
                    '</proc/1/cmdline | head -n 1)"; '
                    'runtime_uid="$(awk \'/^Uid:/{print $2}\' /proc/1/status)"; '
                    'postgres_uid="$(id -u postgres)"; '
                    'test "${executable##*/}" = postgres; '
                    'test "${runtime_uid}" = "${postgres_uid}"; '
                    'test "${runtime_uid}" != 0'
                ),
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # The official image temporarily starts PostgreSQL while initdb runs.
        # pg_isready can succeed against that transient process before the
        # entrypoint shuts it down. PID 1 becomes postgres only after init has
        # completed and the final server has been exec'd.
        if final_process.returncode == 0:
            ready = subprocess.run(
                [
                    "docker",
                    "exec",
                    container,
                    "pg_isready",
                    "-U",
                    administrator,
                    "-d",
                    "postgres",
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if ready.returncode == 0:
                return
        time.sleep(1)
    raise ContractError("isolated restore PostgreSQL did not become ready")


def _sanitize_admin_role(source: Path, target: Path, administrator: str) -> None:
    create = re.compile(r'^CREATE ROLE ("(?:[^"]|"")*"|[A-Za-z_][A-Za-z0-9_$]*);$')
    alter = re.compile(
        r'^ALTER ROLE ("(?:[^"]|"")*"|[A-Za-z_][A-Za-z0-9_$]*) WITH .+;$'
    )

    def identifier(value: str) -> str:
        return value[1:-1].replace('""', '"') if value.startswith('"') else value.lower()

    removed_create = 0
    removed_alter = 0
    with source.open("r", encoding="utf-8") as reader, target.open("x", encoding="utf-8") as writer:
        os.chmod(target, 0o600)
        for line in reader:
            statement = line.rstrip("\r\n")
            create_match = create.fullmatch(statement)
            if create_match and identifier(create_match.group(1)) == administrator:
                removed_create += 1
                continue
            alter_match = alter.fullmatch(statement)
            if alter_match and identifier(alter_match.group(1)) == administrator:
                removed_alter += 1
                continue
            writer.write(line)
    if removed_create != 1 or removed_alter != 1:
        raise ContractError(
            "PostgreSQL dump did not contain exactly one administrator definition"
        )


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
    integrity = _validate_private_backup(backup_dir, context)
    manifest_path = backup_dir / "BackupManifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    candidate = os.environ["WEAVE_CANDIDATE_COMMIT"]
    candidate_manifest_digest = os.environ["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
    postgres_dump_client_image = manifest.get("postgresDumpClientImage")
    postgres_databases = manifest.get("postgresDatabases")
    postgres_database_inventory_digest = manifest.get(
        "postgresDatabaseInventoryDigest"
    )
    if (
        manifest.get("schemaVersion") != "weave.compose-private-backup.v3"
        or integrity.get("candidateCommit") != candidate
        or manifest.get("candidateCommit") != candidate
        or manifest.get("candidateManifestDigest")
        != candidate_manifest_digest
        or manifest.get("profile") != getattr(context, "environment", context.profile)
        or manifest.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or not isinstance(postgres_dump_client_image, str)
        or not re.fullmatch(
            r"postgres@sha256:[0-9a-f]{64}", postgres_dump_client_image
        )
        or not isinstance(postgres_databases, list)
        or "postgres" not in postgres_databases
        or postgres_databases != sorted(set(postgres_databases))
        or any(
            not isinstance(name, str)
            or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]{0,62}", name)
            for name in postgres_databases
        )
        or postgres_database_inventory_digest
        != database_inventory_digest(postgres_databases)
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
    # The pinned Debian-based provider image is already part of the governed
    # deployment input and supplies GNU tar. BusyBox tar does not restore
    # symlink ownership and can apply a read-only "." mode before children.
    restore_helper_image = context.env["WEAVE_NEXTCLOUD_IMAGE"]
    if not re.fullmatch(
        r"[^\s@]+@sha256:[0-9a-f]{64}", restore_helper_image
    ):
        raise ContractError(
            "isolated provider-volume restore helper must use an immutable digest"
        )
    _verify_restore_helper(restore_helper_image)
    _run("docker", "network", "create", "--internal", *_labels(namespace), network)
    try:
        restored_inventories: list[dict[str, object]] = []
        for variable, archive, _kind in active_volume_artifacts(context):
            volume = f"{namespace}-{variable.lower().removeprefix('weave_').removesuffix('_volume').replace('_', '-')}"
            volumes.append(volume)
            expected_inventory = _archive_inventory(
                backup_dir / archive, require_root=True
            )
            _restore_volume(
                restore_helper_image,
                namespace,
                volume,
                backup_dir,
                archive,
                expected_inventory["rootMetadata"],
            )
            observed_inventory = _volume_inventory(
                restore_helper_image, volume
            )
            if observed_inventory != expected_inventory:
                raise ContractError(f"restored provider volume inventory differs from backup artifact: {archive}")
            restored_inventories.append(
                {"artifact": archive, **expected_inventory, "verified": True}
            )
        with tempfile.TemporaryDirectory(prefix="weave-restore-") as temporary:
            password = Path(temporary) / "postgres-admin-password"
            pgpass = Path(temporary) / "postgres-client-pgpass"
            descriptor = os.open(
                password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
            )
            password_value = secrets.token_urlsafe(48)
            with os.fdopen(descriptor, "w", encoding="ascii") as output:
                output.write(password_value)
            administrator = context.env["WEAVE_DB_ADMIN_USERNAME"]
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,62}", administrator):
                raise ContractError("PostgreSQL administrator identifier is invalid")
            descriptor = os.open(
                pgpass, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
            )
            with os.fdopen(descriptor, "w", encoding="ascii") as output:
                output.write(
                    f"{db_container}:5432:*:{administrator}:{password_value}\n"
                )
            del password_value
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
                "--read-only",
                "--cap-drop",
                "ALL",
                "--cap-add",
                "CHOWN",
                "--cap-add",
                "DAC_OVERRIDE",
                "--cap-add",
                "FOWNER",
                "--cap-add",
                "SETGID",
                "--cap-add",
                "SETUID",
                "--security-opt",
                "no-new-privileges:true",
                "--tmpfs",
                "/tmp:rw,noexec,nosuid,nodev,size=16777216,mode=1777",
                "--tmpfs",
                (
                    "/var/run/postgresql:rw,nosuid,nodev,"
                    "size=16777216,mode=3775"
                ),
                "--env",
                f"POSTGRES_USER={administrator}",
                "--env",
                "POSTGRES_DB=postgres",
                "--env",
                "POSTGRES_PASSWORD_FILE=/run/secrets/postgres-admin-password",
                "--mount",
                f"type=volume,src={db_volume},dst=/var/lib/postgresql/data",
                "--mount",
                f"type=bind,src={password},dst=/run/secrets/postgres-admin-password,readonly",
                postgres_dump_client_image,
            )
            _wait_for_final_postgres(
                db_container, administrator
            )
            sanitized = Path(temporary) / "postgres.sql"
            _sanitize_admin_role(
                backup_dir / "postgres.sql",
                sanitized,
                administrator,
            )
            _run(
                "docker",
                "run",
                "--rm",
                "--interactive",
                "--network",
                network,
                "--read-only",
                "--cap-drop",
                "ALL",
                "--cap-add",
                "DAC_READ_SEARCH",
                "--security-opt",
                "no-new-privileges:true",
                "--mount",
                f"type=bind,src={pgpass},dst=/run/secrets/pgpass,readonly",
                "--env",
                "PGPASSFILE=/run/secrets/pgpass",
                "--entrypoint",
                "psql",
                postgres_dump_client_image,
                "--no-psqlrc",
                "--set=ON_ERROR_STOP=1",
                "--host",
                db_container,
                "-U",
                administrator,
                "-d",
                "postgres",
                input_file=sanitized,
            )
            query = (
                "SELECT datname FROM pg_database "
                "WHERE datistemplate = false ORDER BY datname"
            )
            observed = _run(
                "docker",
                "exec",
                db_container,
                "psql",
                "--no-psqlrc",
                "-U",
                administrator,
                "-d",
                "postgres",
                "-Atqc",
                query,
            ).stdout.decode("utf-8").splitlines()
            if observed != postgres_databases:
                raise ContractError(
                    "isolated restore database inventory differs from the backup"
                )
            realm_count = _run(
                "docker",
                "exec",
                db_container,
                "psql",
                "--no-psqlrc",
                "-U",
                administrator,
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
        for key in active_volume_keys(context):
            resources.append({"kind": "volume", "name": context.env[key]})
        common = {
            "profile": getattr(context, "environment", context.profile),
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
            "candidateCommit": candidate,
            "candidateManifestDigest": candidate_manifest_digest,
            "backupRef": f"evidence:private-backup:sha256:{manifest_digest}",
            "databaseFingerprint": manifest["databaseFingerprint"],
            "postgresDumpClientImage": postgres_dump_client_image,
            "postgresDatabaseInventoryDigest": (
                postgres_database_inventory_digest
            ),
            "postgresDatabaseCount": len(postgres_databases),
            "backupVerified": True,
            "isolatedRestoreVerified": True,
            "restoreHelperImage": restore_helper_image,
            "restoredVolumeInventories": restored_inventories,
            "isolatedNamespace": namespace,
            "verifiedDatabaseCount": len(postgres_databases),
            "verifiedServiceDatabaseCount": len(
                [name for name in postgres_databases if name != "postgres"]
            ),
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
    try:
        validate_receipt(
            receipt,
            purpose=purpose,
            candidate=candidate,
            candidate_manifest_digest=candidate_manifest_digest,
            profile=getattr(context, "environment", context.profile),
            compose_project=context.env["WEAVE_COMPOSE_PROJECT"],
            backup_manifest_digest="sha256:" + manifest_digest,
        )
    except ReceiptContractError as error:
        raise ContractError(str(error)) from error
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
    parser.add_argument("profile", choices=("dogfood", "prod", "test"))
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
