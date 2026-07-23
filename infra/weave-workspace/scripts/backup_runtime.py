#!/usr/bin/env python3
"""Create a private, quiesced Compose consistency-set backup."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import tarfile
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context


VOLUME_ARTIFACTS = (
    ("WEAVE_NEXTCLOUD_DATA_VOLUME", "nextcloud-data.tgz", "files-calendar-provider-data"),
    ("WEAVE_SYNAPSE_DATA_VOLUME", "synapse-data.tgz", "matrix-media-and-local-state"),
    ("WEAVE_CADDY_DATA_VOLUME", "caddy-data.tgz", "gateway-runtime-state"),
    ("WEAVE_CADDY_CONFIG_VOLUME", "caddy-config.tgz", "gateway-config-state"),
    ("WEAVE_KEYCLOAK_DATA_VOLUME", "keycloak-data.tgz", "keycloak-runtime-state"),
    ("WEAVE_MATRIX_APPSERVICE_VOLUME", "matrix-appservice.tgz", "matrix-appservice-runtime"),
)
QUIESCED_SERVICES = ("caddy", "mcp", "backend", "synapse", "mas", "nextcloud", "keycloak")
SERVICE_SUFFIXES = {
    "caddy": "proxy",
    "mcp": "mcp-server",
    "backend": "backend",
    "synapse": "synapse",
    "mas": "mas",
    "nextcloud": "nextcloud",
    "keycloak": "keycloak",
}
SERVICE_VOLUMES = {
    "caddy": ("WEAVE_CADDY_DATA_VOLUME", "WEAVE_CADDY_CONFIG_VOLUME"),
    "synapse": ("WEAVE_SYNAPSE_DATA_VOLUME", "WEAVE_MATRIX_APPSERVICE_VOLUME"),
    "nextcloud": ("WEAVE_NEXTCLOUD_DATA_VOLUME",),
    "keycloak": ("WEAVE_KEYCLOAK_DATA_VOLUME",),
}


def _sha256(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
            size += len(block)
    return digest.hexdigest(), size


def _private_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=False, mode=0o700)
    os.chmod(path, 0o700)


def _compose(context: ComposeContext, *arguments: str, capture: bool = False) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        [*context.compose_base_command, *arguments],
        cwd=context.root,
        env=compose_environment(context),
        check=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    return result


def _container(context: ComposeContext, suffix: str) -> str:
    return f"{context.env['WEAVE_RESOURCE_PREFIX']}-{suffix}"


def _running_services(context: ComposeContext) -> tuple[list[str], list[dict[str, str]]]:
    result = _compose(context, "ps", "--status", "running", "--services", capture=True)
    compose_services = [line for line in result.stdout.decode("utf-8").splitlines() if line]
    inventory = [
        {"service": service, "authority": "compose", "container": _container(context, SERVICE_SUFFIXES[service])}
        for service in compose_services
        if service in SERVICE_SUFFIXES
    ]
    if compose_services:
        return compose_services, inventory

    # One-time former-state adoption is structurally proven from exact names,
    # the exact deployment network, and every expected persistent mount. The
    # old runtime did not attach ownership labels, so names alone are never
    # accepted.
    legacy: list[str] = []
    for service, suffix in SERVICE_SUFFIXES.items():
        name = _container(context, suffix)
        inspected = subprocess.run(
            ["docker", "container", "inspect", name],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if inspected.returncode != 0:
            continue
        rows = json.loads(inspected.stdout)
        if len(rows) != 1 or rows[0].get("Name") != "/" + name:
            raise ContractError(f"legacy container identity is ambiguous: {name}")
        value = rows[0]
        if value.get("State", {}).get("Status") != "running":
            continue
        networks = value.get("NetworkSettings", {}).get("Networks", {})
        if context.env["WEAVE_DOCKER_NETWORK"] not in networks:
            raise ContractError(f"legacy container is outside the exact deployment network: {name}")
        mounted = {
            mount.get("Name")
            for mount in value.get("Mounts", [])
            if mount.get("Type") == "volume" and isinstance(mount.get("Name"), str)
        }
        expected = {context.env[key] for key in SERVICE_VOLUMES.get(service, ())}
        if not expected.issubset(mounted):
            raise ContractError(f"legacy container does not bind the expected persistent volumes: {name}")
        container_id = str(value.get("Id", ""))
        if not re.fullmatch(r"[0-9a-f]{64}", container_id):
            raise ContractError(f"legacy container ID is invalid: {name}")
        legacy.append(service)
        inventory.append(
            {
                "service": service,
                "authority": "former-state-adoption",
                "container": name,
                "containerIdFingerprint": "sha256:" + hashlib.sha256(container_id.encode("ascii")).hexdigest(),
            }
        )
    return legacy, inventory


def _stop(context: ComposeContext, services: list[str], inventory: list[dict[str, str]]) -> None:
    compose = [item["service"] for item in inventory if item["authority"] == "compose" and item["service"] in services]
    legacy = [item["container"] for item in inventory if item["authority"] == "former-state-adoption" and item["service"] in services]
    if compose:
        _compose(context, "stop", "--timeout", "60", *compose)
    if legacy:
        subprocess.run(["docker", "stop", "--time", "60", *legacy], check=True, stdout=subprocess.DEVNULL)


def _start(context: ComposeContext, services: list[str], inventory: list[dict[str, str]]) -> None:
    compose = [item["service"] for item in inventory if item["authority"] == "compose" and item["service"] in services]
    legacy = [item["container"] for item in inventory if item["authority"] == "former-state-adoption" and item["service"] in services]
    if compose:
        _compose(context, "start", *reversed(compose))
    if legacy:
        subprocess.run(["docker", "start", *reversed(legacy)], check=True, stdout=subprocess.DEVNULL)


def _archive_volume(context: ComposeContext, volume: str, target: Path) -> None:
    if subprocess.run(["docker", "volume", "inspect", volume], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
        raise ContractError(f"required persistent volume is absent: {volume}")
    subprocess.run(
        [
            "docker",
            "run",
            "--rm",
            "--read-only",
            "--cap-drop",
            "ALL",
            "--security-opt",
            "no-new-privileges:true",
            "--mount",
            f"type=volume,src={volume},dst=/source,readonly",
            "--mount",
            f"type=bind,src={target.parent},dst=/backup",
            "--entrypoint",
            "/bin/sh",
            context.env["WEAVE_POSTGRES_IMAGE"],
            "-euc",
            f"tar -C /source -czf /backup/{target.name} . && chmod 0600 /backup/{target.name}",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def _postgres_dump(context: ComposeContext, target: Path) -> str:
    container = _container(context, "db")
    fingerprint = subprocess.run(
        [
            "docker",
            "exec",
            container,
            "psql",
            "--no-psqlrc",
            "-U",
            context.env["WEAVE_DB_ADMIN_USERNAME"],
            "-d",
            "postgres",
            "-Atqc",
            "SELECT system_identifier FROM pg_control_system()",
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout.strip()
    if not fingerprint.isdigit():
        raise ContractError("PostgreSQL system identifier is unavailable")
    with target.open("xb") as output:
        os.chmod(target, 0o600)
        subprocess.run(
            ["docker", "exec", container, "pg_dumpall", "-U", context.env["WEAVE_DB_ADMIN_USERNAME"]],
            check=True,
            stdout=output,
            stderr=subprocess.PIPE,
        )
    return "sha256:" + hashlib.sha256(fingerprint.encode("ascii")).hexdigest()


def _archive_private_config(context: ComposeContext, target: Path) -> None:
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(descriptor)
    with tarfile.open(target, "w:gz", dereference=True) as archive:
        roots = (
            (context.generated_root, "generated"),
            (context.secret_root, "secrets"),
            (context.tls_root, "tls"),
        )
        seen: set[Path] = set()
        for source, name in roots:
            resolved = source.resolve()
            if resolved in seen or not source.exists():
                continue
            if source.is_symlink() or not source.is_dir():
                raise ContractError(f"private configuration root is unsafe: {name}")
            archive.add(source, arcname=name, recursive=True)
            seen.add(resolved)


def backup(context: ComposeContext) -> Path:
    if context.profile not in ("dogfood", "main"):
        raise ContractError("private consistency backups are required for dogfood/main, not H2 host-dev")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ContractError("WEAVE_CANDIDATE_COMMIT must bind the private backup to an exact candidate")
    backup_root_value = os.environ.get("WEAVE_BACKUP_ROOT", "")
    if not backup_root_value:
        raise ContractError("WEAVE_BACKUP_ROOT is required and must be outside the checkout")
    backup_root = Path(backup_root_value).expanduser().resolve()
    if context.repository_root == backup_root or context.repository_root in backup_root.parents:
        raise ContractError("WEAVE_BACKUP_ROOT must be outside the implementation checkout")
    backup_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    if stat.S_IMODE(backup_root.stat().st_mode) & 0o077:
        raise ContractError("WEAVE_BACKUP_ROOT must not be group/world accessible")
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_id = f"weave-{context.profile}-{timestamp}-{candidate[:12]}"
    destination = backup_root / backup_id
    _private_directory(destination)
    running, inventory = _running_services(context)
    to_stop = [name for name in QUIESCED_SERVICES if name in running]
    artifacts: list[dict[str, object]] = []
    try:
        if to_stop:
            _stop(context, to_stop, inventory)
        dump = destination / "postgres.sql"
        database_fingerprint = _postgres_dump(context, dump)
        for variable, archive, kind in VOLUME_ARTIFACTS:
            target = destination / archive
            _archive_volume(context, context.env[variable], target)
            digest, size = _sha256(target)
            artifacts.append({"path": archive, "kind": kind, "sha256": digest, "bytes": size})
        private_config = destination / "private-config-secrets.tgz"
        _archive_private_config(context, private_config)
        for target, kind in ((dump, "postgres-consistency-dump"), (private_config, "private-config-secretrefs")):
            digest, size = _sha256(target)
            artifacts.append({"path": target.name, "kind": kind, "sha256": digest, "bytes": size})
        manifest = {
            "schemaVersion": "weave.compose-private-backup.v2",
            "backupId": backup_id,
            "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "candidateCommit": candidate,
            "profile": context.profile,
            "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
            "databaseFingerprint": database_fingerprint,
            "quiescedServices": to_stop,
            "runtimeInventory": inventory,
            "artifacts": sorted(artifacts, key=lambda item: str(item["path"])),
            "supportSafe": False,
            "containsSecretsOrMemberData": True,
        }
        manifest_path = destination / "BackupManifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.chmod(manifest_path, 0o600)
    finally:
        if to_stop:
            _start(context, to_stop, inventory)
    return destination


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "dogfood", "main"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        output = backup(context)
    except (ContractError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_BACKUP_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"backup: private consistency set written to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
