#!/usr/bin/env python3
"""Migrate one legacy bootstrap generation without executing it."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shlex
import subprocess
import tempfile
from pathlib import Path

from compose_env import ComposeContext, ContractError, canonical_json


ASSIGNMENT = re.compile(r"^[A-Z][A-Z0-9_]*=")


def _ansi_c(value: str) -> str:
    if not (value.startswith("$'") and value.endswith("'")):
        raise ContractError("legacy ANSI-C value is malformed")
    source = value[2:-1]
    output: list[str] = []
    index = 0
    while index < len(source):
        character = source[index]
        if character != "\\":
            output.append(character)
            index += 1
            continue
        index += 1
        if index >= len(source):
            raise ContractError("legacy ANSI-C value has a trailing escape")
        escape = source[index]
        simple = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\", "'": "'"}
        if escape in simple:
            output.append(simple[escape])
            index += 1
            continue
        if escape == "x" and re.fullmatch(r"[0-9A-Fa-f]{2}", source[index + 1 : index + 3]):
            output.append(chr(int(source[index + 1 : index + 3], 16)))
            index += 3
            continue
        match = re.match(r"[0-7]{1,3}", source[index:])
        if match:
            output.append(chr(int(match.group(0), 8)))
            index += len(match.group(0))
            continue
        raise ContractError("legacy ANSI-C value contains an unsupported escape")
    return "".join(output)


def _value(raw: str) -> str:
    if any(marker in raw for marker in ("`", "$(", "${")):
        raise ContractError("legacy bootstrap contains an executable expansion")
    if raw.startswith("$'"):
        result = _ansi_c(raw)
    else:
        parsed = shlex.split(raw, posix=True)
        if len(parsed) != 1:
            raise ContractError("legacy bootstrap value is ambiguous")
        result = parsed[0]
    if not result or "\x00" in result:
        raise ContractError("legacy bootstrap contains an empty or invalid protected value")
    return result


def parse(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ContractError("legacy bootstrap evidence must be a regular mode-0600 file")
    values: dict[str, str] = {}
    for number, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = original.strip()
        if not line or line.startswith("#"):
            continue
        if not line.startswith("export "):
            raise ContractError(f"legacy bootstrap line {number} is not a literal exported assignment")
        assignment = line.removeprefix("export ")
        if not ASSIGNMENT.match(assignment):
            raise ContractError(f"legacy bootstrap line {number} has an invalid key")
        key, raw = assignment.split("=", 1)
        if key in values:
            raise ContractError(f"legacy bootstrap repeats protected key {key}")
        values[key] = _value(raw)
    return values


def _install(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink():
        raise ContractError("legacy secret destination is a symlink")
    if path.exists():
        if not path.is_file() or path.stat().st_mode & 0o077 or path.read_bytes() != payload:
            raise ContractError(f"existing generation differs from legacy continuity input: {path.name}")
        return
    temporary = path.with_suffix(path.suffix + ".migration-tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def _recover_nextcloud_oidc_secret(context: ComposeContext) -> tuple[bytes, dict[str, object]]:
    """Recover the exact encrypted-at-rest legacy provider credential.

    Nextcloud's user_oidc listing deliberately masks the client secret.  This
    bounded first-adoption probe asks Nextcloud's own crypto service to decrypt
    the one ``keycloak`` provider and streams the value directly into a
    mode-0600 temporary file.  The value is never an argument, environment
    variable, stdout/stderr capture, receipt field, or support artifact.
    """

    container = f"{context.env['WEAVE_RESOURCE_PREFIX']}-nextcloud"
    inspected = subprocess.run(
        ["docker", "container", "inspect", container],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode != 0:
        raise ContractError("legacy Nextcloud container is unavailable for OIDC secret continuity")
    rows = json.loads(inspected.stdout)
    if len(rows) != 1 or rows[0].get("Name") != "/" + container:
        raise ContractError("legacy Nextcloud container identity is ambiguous")
    observed = rows[0]
    if observed.get("State", {}).get("Status") != "running":
        raise ContractError("legacy Nextcloud must be running for bounded OIDC secret recovery")
    networks = observed.get("NetworkSettings", {}).get("Networks", {})
    if context.env["WEAVE_DOCKER_NETWORK"] not in networks:
        raise ContractError("legacy Nextcloud is outside the exact deployment network")
    mounted = {
        mount.get("Name")
        for mount in observed.get("Mounts", [])
        if mount.get("Type") == "volume"
    }
    if context.env["WEAVE_NEXTCLOUD_DATA_VOLUME"] not in mounted:
        raise ContractError("legacy Nextcloud does not bind the expected persistent data volume")
    container_id = str(observed.get("Id", ""))
    if re.fullmatch(r"[0-9a-f]{64}", container_id) is None:
        raise ContractError("legacy Nextcloud container ID is invalid")

    php = (
        'require_once "/var/www/html/lib/base.php"; '
        '$m=OC::$server->get(OCA\\UserOIDC\\Db\\ProviderMapper::class); '
        '$c=OC::$server->get(OCP\\Security\\ICrypto::class); '
        '$p=$m->findProviderByIdentifier("keycloak"); '
        'if ($p->getClientId() !== "nextcloud") { exit(17); } '
        'echo $c->decrypt($p->getClientSecret());'
    )
    context.secret_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.NamedTemporaryFile(
        prefix=".nextcloud-oidc-recovery-",
        dir=context.secret_root,
        delete=False,
    ) as sink:
        temporary = Path(sink.name)
        os.chmod(temporary, 0o600)
        result = subprocess.run(
            [
                "docker", "exec", "--user", "www-data", container,
                "php", "-r", php,
            ],
            stdout=sink,
            stderr=subprocess.PIPE,
        )
    try:
        if result.returncode != 0:
            raise ContractError("bounded Nextcloud OIDC secret recovery failed")
        payload = temporary.read_bytes().strip()
        if not 20 <= len(payload) <= 512 or b"\x00" in payload or b"\n" in payload or b"\r" in payload:
            raise ContractError("recovered Nextcloud OIDC secret has an invalid closed shape")
        return payload + b"\n", {
            "kind": "nextcloud-user-oidc-decryption",
            "providerIdentifier": "keycloak",
            "clientId": "nextcloud",
            "containerIdFingerprint": "sha256:" + hashlib.sha256(container_id.encode("ascii")).hexdigest(),
            "valueExposed": False,
            "supportSafe": True,
        }
    finally:
        temporary.unlink(missing_ok=True)


def migrate(context: ComposeContext, source: Path) -> dict[str, object]:
    mapping_path = context.root / "migration/legacy-secret-map.json"
    mapping = json.loads(mapping_path.read_text(encoding="utf-8"))
    if mapping.get("schemaVersion") != "weave.legacy-secret-map.v1":
        raise ContractError("legacy secret map is not the supported revision")
    values = parse(source)
    fingerprints: dict[str, str] = {}
    for legacy, target in mapping["entries"].items():
        if legacy not in values:
            raise ContractError(f"legacy continuity input is missing required generation for {target}")
        payload = values[legacy].encode("utf-8")
        if not target.endswith((".pem", ".json")):
            payload += b"\n"
        _install(context.secret_root / target, payload)
        fingerprints[target] = "sha256:" + hashlib.sha256(payload.rstrip(b"\n")).hexdigest()

    nextcloud_secret, nextcloud_proof = _recover_nextcloud_oidc_secret(context)
    _install(context.secret_root / "keycloak-nextcloud", nextcloud_secret)
    fingerprints["keycloak-nextcloud"] = "sha256:" + hashlib.sha256(
        nextcloud_secret.rstrip(b"\n")
    ).hexdigest()

    for legacy, target in mapping["tlsPathEntries"].items():
        source_path = Path(values.get(legacy, "")).expanduser().resolve()
        if source_path.is_symlink() or not source_path.is_file():
            raise ContractError(f"legacy TLS continuity input is missing: {target}")
        payload = source_path.read_bytes()
        _install(context.tls_root / target, payload)
        fingerprints["tls/" + target] = "sha256:" + hashlib.sha256(payload).hexdigest()
    ca_path = Path(values[mapping["legacyCaPathKey"]]).expanduser().resolve()
    ca_key = ca_path.with_name("weave-local-ca-key.pem")
    if ca_key.is_symlink() or not ca_key.is_file():
        raise ContractError("legacy TLS CA key continuity input is missing")
    _install(context.tls_root / "ca-key.pem", ca_key.read_bytes())
    fingerprints["tls/ca-key.pem"] = "sha256:" + hashlib.sha256(ca_key.read_bytes()).hexdigest()

    retired = sorted(name for name in mapping["retiredWithoutCompatibility"] if name in values)
    return {
        "schemaVersion": "weave.legacy-secret-migration-receipt.v1",
        "sourceFingerprint": "sha256:" + hashlib.sha256(source.read_bytes()).hexdigest(),
        "generationFingerprints": dict(sorted(fingerprints.items())),
        "retiredGenerationNamesDigest": "sha256:" + hashlib.sha256(canonical_json(retired)).hexdigest(),
        "migratedGenerationCount": len(fingerprints),
        "retiredGenerationCount": len(retired),
        "continuityProofs": [nextcloud_proof],
        "valuesIncluded": False,
        "supportSafe": True,
        "containsSecretValues": False,
    }
