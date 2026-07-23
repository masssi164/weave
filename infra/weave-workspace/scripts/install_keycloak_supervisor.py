#!/usr/bin/env python3
"""Install the Keycloak reconciliation supervisor outside a release candidate.

The installer is an explicit host-operator action.  It creates an immutable,
root-owned Python package, preserves one Ed25519 signing generation, exports
only its public trust key, and writes an adjacent platform attestation.  It is
idempotent for byte-identical inputs and refuses implicit package, key, image,
or trust rotation.
"""

from __future__ import annotations

import argparse
import grp
import hashlib
import json
import os
import platform
import re
import secrets
import stat
import subprocess
import sys
from pathlib import Path
from typing import Any

KEYCLOAK_MODULE_ROOT = Path(__file__).resolve().parents[1] / "keycloak"
sys.path.insert(0, str(KEYCLOAK_MODULE_ROOT))

from crypto_runtime import OPENSSL  # noqa: E402


SUPERVISOR_VERSION = "1.0.0"
PACKAGE_FILES = (
    "admin_sanitizer.py",
    "crypto_runtime.py",
    "deployment_context.py",
    "desired_state_authority.py",
    "kcadm_driver.py",
    "lease_control.py",
    "receipt.py",
    "reconciler.py",
    "rfc8785.py",
    "sanitizer_daemon.py",
    "supervisor.py",
)
COMMAND_ALLOWLIST = (
    "acquire",
    "stop-keycloak",
    "bootstrap-admin-service",
    "start-keycloak",
    "reconcile-through-sanitizer",
    "probe",
    "teardown",
    "sign-receipt",
)
SHA256 = re.compile(r"^sha256:[0-9a-f]{64}$")
APPROVAL = re.compile(r"^approval:keycloak-(?:supervisor-package|image):[A-Za-z0-9._:/-]+$")
OPERATOR_GROUP = re.compile(r"^[a-z_][a-z0-9_-]{0,31}$")
SAFE_EXECUTABLE = re.compile(r"^/[A-Za-z0-9._/-]+$")
GOVERNED_INSTALL_ROOT = Path("/opt/weave/keycloak-supervisor")


class InstallError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return "sha256:" + digest.hexdigest()


def package_files(source_root: Path) -> dict[str, str]:
    if not source_root.is_absolute() or source_root.is_symlink() or not source_root.is_dir():
        raise InstallError("supervisor source root must be an absolute regular directory")
    result: dict[str, str] = {}
    for name in PACKAGE_FILES:
        path = source_root / name
        if path.is_symlink() or not path.is_file() or not stat.S_ISREG(path.stat().st_mode):
            raise InstallError(f"supervisor source module is unavailable or unsafe: {name}")
        result[name] = sha256_file(path)
    return result


def package_digest(files: dict[str, str]) -> str:
    if set(files) != set(PACKAGE_FILES) or any(not SHA256.fullmatch(value) for value in files.values()):
        raise InstallError("supervisor package manifest is incomplete")
    payload = json.dumps(files, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def build_sudoers_policy(installed_path: Path, operator_group: str) -> bytes:
    """Build the only privilege edge exposed to the deployment operator.

    The executable is immutable and root-owned.  Candidate-controlled values
    are accepted only as arguments to the supervisor's closed argparse and
    deployment-context parsers; sudo may not preserve or set environment
    values for the command.
    """

    executable = str(installed_path)
    if not installed_path.is_absolute() or not SAFE_EXECUTABLE.fullmatch(executable):
        raise InstallError("installed supervisor path is not sudoers-safe")
    if not OPERATOR_GROUP.fullmatch(operator_group):
        raise InstallError("supervisor operator group is malformed")
    return (
        "# Managed by Weave; changes require a new reviewed supervisor generation.\n"
        f"%{operator_group} ALL=(root) NOPASSWD:NOSETENV: {executable} *\n"
    ).encode("utf-8")


def build_attestation(
    *,
    installed_path: Path,
    trust_key_sha256: str,
    files: dict[str, str],
    approved_image_digests: list[str],
    approved_sanitizer_image_digests: list[str],
    package_approval_ref: str,
    image_approval_ref: str,
    operator_group: str,
    sudoers_policy_path: Path,
    sudoers_policy_sha256: str,
    system: str | None = None,
    machine: str | None = None,
) -> dict[str, Any]:
    if not installed_path.is_absolute():
        raise InstallError("installed supervisor path must be absolute")
    if not SHA256.fullmatch(trust_key_sha256):
        raise InstallError("supervisor trust-key digest is malformed")
    approved = sorted(set(approved_image_digests))
    if not approved or any(not SHA256.fullmatch(value) for value in approved):
        raise InstallError("at least one immutable Keycloak image digest is required")
    approved_sanitizer = sorted(set(approved_sanitizer_image_digests))
    if not approved_sanitizer or any(not SHA256.fullmatch(value) for value in approved_sanitizer):
        raise InstallError("at least one immutable sanitizer image digest is required")
    if not APPROVAL.fullmatch(package_approval_ref) or not APPROVAL.fullmatch(image_approval_ref):
        raise InstallError("package and image approval references must use the closed approval namespace")
    if not OPERATOR_GROUP.fullmatch(operator_group):
        raise InstallError("supervisor operator group is malformed")
    if not sudoers_policy_path.is_absolute() or not SAFE_EXECUTABLE.fullmatch(str(sudoers_policy_path)):
        raise InstallError("sudoers policy path is unsafe")
    if not SHA256.fullmatch(sudoers_policy_sha256):
        raise InstallError("sudoers policy digest is malformed")
    digest = package_digest(files)
    attestation_ref = (
        "attestation:keycloak-supervisor:"
        f"{(system or platform.system()).lower()}/{(machine or platform.machine()).lower()}/"
        f"{digest.removeprefix('sha256:')[:24]}"
    )
    return {
        "schemaVersion": "weave.keycloak-supervisor-platform-attestation.v1",
        "supervisorVersion": SUPERVISOR_VERSION,
        "installedPath": str(installed_path),
        "candidateIndependent": True,
        "controlPlane": "root-owned-run-bound-supervisor",
        "privilegedInvocation": "sudo-noninteractive-fixed-executable",
        "operatorGroup": operator_group,
        "sudoersPolicyPath": str(sudoers_policy_path),
        "sudoersPolicySha256": sudoers_policy_sha256,
        "commandAllowlist": list(COMMAND_ALLOWLIST),
        "packageFiles": dict(sorted(files.items())),
        "packageDigest": digest,
        "trustKeySha256": trust_key_sha256,
        "keyGenerationRef": "keyref:keycloak-supervisor/current",
        "approvedKeycloakImageDigests": approved,
        "approvedSanitizerImageDigests": approved_sanitizer,
        "packageApprovalRef": package_approval_ref,
        "keycloakImageApprovalRef": image_approval_ref,
        "platform": {
            "system": (system or platform.system()).lower(),
            "machine": (machine or platform.machine()).lower(),
        },
        "attestationRef": attestation_ref,
    }


def _assert_root_directory(path: Path, mode: int) -> None:
    path.mkdir(parents=True, exist_ok=True, mode=mode)
    if path.is_symlink() or not path.is_dir():
        raise InstallError(f"installation directory is unsafe: {path}")
    metadata = path.stat()
    if metadata.st_uid != 0 or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
        raise InstallError(f"installation directory must be root-owned and non-writable by other users: {path}")
    os.chmod(path, mode)


def assert_root_owned_ancestor_chain(path: Path) -> None:
    current = path.resolve()
    while True:
        if current.is_symlink() or not current.exists():
            raise InstallError(f"governed path ancestor is missing or a symlink: {current}")
        metadata = current.stat()
        if metadata.st_uid != 0 or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            raise InstallError(f"governed path ancestor is not root-owned and immutable: {current}")
        if current == current.parent:
            return
        current = current.parent


def _create_or_verify(path: Path, payload: bytes, mode: int, label: str) -> None:
    if path.exists() or path.is_symlink():
        if path.is_symlink() or not path.is_file():
            raise InstallError(f"{label} is not a regular file")
        metadata = path.stat()
        if metadata.st_uid != 0 or stat.S_IMODE(metadata.st_mode) != mode:
            raise InstallError(f"{label} ownership or mode changed")
        if path.read_bytes() != payload:
            raise InstallError(f"{label} differs; install a new immutable package generation")
        return
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _private_key(path: Path) -> bytes:
    if path.exists() or path.is_symlink():
        if path.is_symlink() or not path.is_file():
            raise InstallError("supervisor signing key is not a regular file")
        metadata = path.stat()
        if metadata.st_uid != 0 or stat.S_IMODE(metadata.st_mode) != 0o600:
            raise InstallError("supervisor signing key must remain root-owned mode 0600")
    else:
        temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
        try:
            result = subprocess.run(
                [OPENSSL, "genpkey", "-algorithm", "ED25519", "-out", str(temporary)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            if result.returncode != 0 or not temporary.is_file():
                raise InstallError("OpenSSL could not create the supervisor signing generation")
            os.chmod(temporary, 0o600)
            os.replace(temporary, path)
        finally:
            if temporary.exists():
                temporary.unlink()
    check = subprocess.run(
        [OPENSSL, "pkey", "-in", str(path), "-noout", "-check"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check.returncode != 0:
        raise InstallError("supervisor signing key failed OpenSSL validation")
    public = subprocess.run(
        [OPENSSL, "pkey", "-in", str(path), "-pubout"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout
    if b"BEGIN PUBLIC KEY" not in public:
        raise InstallError("OpenSSL did not derive the supervisor public trust key")
    return public


def _validate_sudoers(payload: bytes, state_root: Path) -> None:
    temporary = state_root / f".sudoers-policy.{secrets.token_hex(8)}.tmp"
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o440)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        result = subprocess.run(
            ["visudo", "-cf", str(temporary)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode != 0:
            raise InstallError("generated supervisor sudoers policy failed visudo validation")
    finally:
        if temporary.exists():
            temporary.unlink()


def install(args: argparse.Namespace) -> dict[str, Any]:
    if os.geteuid() != 0:
        raise InstallError("the persistent supervisor installer must run as root")
    source = args.source_root.resolve()
    install_root = args.install_root.resolve()
    state_root = Path("/var/lib/weave/keycloak-supervisor")
    trust_output = args.trust_key_output.resolve()
    for path, label in (
        (install_root, "install root"),
        (trust_output, "trust-key output"),
    ):
        if not path.is_absolute():
            raise InstallError(f"{label} must be an absolute path")
    if source == install_root or source in install_root.parents:
        raise InstallError("installed package must be outside its candidate source tree")
    if install_root.parent != GOVERNED_INSTALL_ROOT:
        raise InstallError(f"supervisor generations must be direct children of {GOVERNED_INSTALL_ROOT}")
    files = package_files(source)
    try:
        grp.getgrnam(args.operator_group)
    except KeyError as error:
        raise InstallError("supervisor operator group does not exist") from error
    _assert_root_directory(install_root, 0o755)
    _assert_root_directory(state_root, 0o700)
    assert_root_owned_ancestor_chain(install_root)
    assert_root_owned_ancestor_chain(state_root)
    signing_key = state_root / "signing-key.pem"
    public_key = _private_key(signing_key)
    for name in PACKAGE_FILES:
        mode = 0o555 if name == "supervisor.py" else 0o444
        _create_or_verify(install_root / name, (source / name).read_bytes(), mode, f"installed module {name}")
    trust_output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if trust_output.parent.is_symlink() or not trust_output.parent.is_dir():
        raise InstallError("trust-key destination directory is unsafe")
    _create_or_verify(trust_output, public_key, 0o444, "exported public trust key")
    installed_supervisor = install_root / "supervisor.py"
    policy = build_sudoers_policy(installed_supervisor, args.operator_group)
    policy_path = Path("/etc/sudoers.d") / (
        "weave-keycloak-supervisor-" + package_digest(files).removeprefix("sha256:")[:20]
    )
    _validate_sudoers(policy, state_root)
    _create_or_verify(policy_path, policy, 0o440, "supervisor sudoers policy")
    attestation = build_attestation(
        installed_path=installed_supervisor,
        trust_key_sha256="sha256:" + hashlib.sha256(public_key).hexdigest(),
        files=files,
        approved_image_digests=args.approved_keycloak_image_digest,
        approved_sanitizer_image_digests=args.approved_sanitizer_image_digest,
        package_approval_ref=args.package_approval_ref,
        image_approval_ref=args.image_approval_ref,
        operator_group=args.operator_group,
        sudoers_policy_path=policy_path,
        sudoers_policy_sha256="sha256:" + hashlib.sha256(policy).hexdigest(),
    )
    encoded = json.dumps(attestation, indent=2, sort_keys=True).encode("utf-8") + b"\n"
    _create_or_verify(
        install_root / "supervisor.py.attestation.json",
        encoded,
        0o444,
        "supervisor platform attestation",
    )
    return attestation


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--install-root", type=Path, required=True)
    parser.add_argument("--trust-key-output", type=Path, required=True)
    parser.add_argument("--approved-keycloak-image-digest", action="append", required=True)
    parser.add_argument("--approved-sanitizer-image-digest", action="append", required=True)
    parser.add_argument("--package-approval-ref", required=True)
    parser.add_argument("--image-approval-ref", required=True)
    parser.add_argument("--operator-group", required=True)
    args = parser.parse_args()
    try:
        attestation = install(args)
    except (InstallError, OSError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_KEYCLOAK_SUPERVISOR_INSTALL_ERROR {error}", file=sys.stderr)
        return 1
    print(
        json.dumps(
            {
                "installedPath": attestation["installedPath"],
                "packageDigest": attestation["packageDigest"],
                "attestationRef": attestation["attestationRef"],
                "containsSecretValues": False,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
