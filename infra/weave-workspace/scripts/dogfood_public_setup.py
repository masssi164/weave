"""Public-only projections of the existing reviewed Dogfood CA and origins."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
from urllib.parse import urlsplit

from compose_env import ComposeContext, ContractError

PUBLIC_FILES = frozenset({"ca.pem", "trust.json", "README.md"})


def https_origin(value: str) -> str:
    parsed = urlsplit(value)
    try:
        port = parsed.port
    except ValueError:
        raise ContractError("local trust requires canonical HTTPS origins") from None
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or parsed.path not in ("", "/")
            or parsed.query or parsed.fragment or any(c.isspace() for c in value)
            or (port is not None and not 1 <= port <= 65535)):
        raise ContractError("local trust requires canonical HTTPS origins")
    return "https://" + parsed.netloc


def _no_symlink(path: Path) -> None:
    if any(parent.is_symlink() for parent in (path, *path.parents)):
        raise ContractError("public setup paths must not contain symlinks")


def public_setup(context: ComposeContext) -> tuple[bytes, dict[str, object]]:
    if context.environment != "dogfood" or context.isolated_namespace is not None:
        raise ContractError("public local trust setup requires persistent Dogfood")
    certificate = context.tls_root / "ca.pem"
    _no_symlink(certificate)
    try:
        info = certificate.stat()
        if not stat.S_ISREG(info.st_mode) or not 0 < info.st_size <= 16384:
            raise ValueError()
        pem = certificate.read_bytes()
        if re.fullmatch(rb"\s*-----BEGIN CERTIFICATE-----\r?\n[A-Za-z0-9+/=\r\n]+-----END CERTIFICATE-----\s*", pem) is None:
            raise ValueError()
        der = subprocess.run(["openssl", "x509", "-in", str(certificate), "-outform", "DER"],
                             capture_output=True, check=True, timeout=10).stdout
        constraints = subprocess.run(["openssl", "x509", "-in", str(certificate), "-noout", "-ext", "basicConstraints"],
                                     capture_output=True, check=True, timeout=10).stdout
        if not der or b"CA:TRUE" not in constraints:
            raise ValueError()
    except (OSError, ValueError, subprocess.SubprocessError):
        raise ContractError("existing public Dogfood CA is unavailable or invalid; no CA was created or replaced") from None
    organization = https_origin(context.env["WEAVE_PUBLIC_URL"])
    fingerprint = ":".join(re.findall("..", hashlib.sha256(der).hexdigest().upper()))
    return pem, {
        "schemaVersion": "weave.dogfood-local-trust/v1", "environment": "dogfood",
        "organizationOrigin": organization, "apiOrigin": https_origin(context.env["WEAVE_API_ORIGIN"]),
        "issuerOrigin": https_origin(context.env["WEAVE_AUTH_URL"]),
        "certificateUrl": organization + "/admin-console/setup/ca.pem",
        "caSha256Fingerprint": fingerprint, "supportSafe": True, "containsSecretValues": False,
    }


def write_public_setup(context: ComposeContext, destination: Path | None = None) -> dict[str, object]:
    pem, descriptor = public_setup(context)
    destination = destination or context.generated_root / "public-setup"
    _no_symlink(destination)
    absolute = destination.absolute()
    for protected in (context.tls_root.absolute(), context.secret_root.absolute()):
        if absolute == protected or protected in absolute.parents:
            raise ContractError("public setup must be separate from TLS and secret roots")
    destination.mkdir(parents=True, exist_ok=True, mode=0o755)
    info = destination.stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        raise ContractError("public setup directory must be operator-owned")
    for entry in destination.iterdir():
        details = entry.lstat()
        if (entry.name not in PUBLIC_FILES or not stat.S_ISREG(details.st_mode)
                or details.st_uid != os.getuid() or details.st_nlink != 1):
            raise ContractError("public setup contains an unmanaged or unsafe entry")
    readme = ("Weave Dogfood local trust setup\n\n"
              f"Organization: {descriptor['organizationOrigin']}\n"
              f"Admin Console: {descriptor['organizationOrigin']}/admin-console/\n"
              f"API: {descriptor['apiOrigin']}\nIssuer: {descriptor['issuerOrigin']}\n"
              f"Existing CA SHA-256: {descriptor['caSha256Fingerprint']}\n\n"
              "Compare this fingerprint before trusting ca.pem. Installing or trusting the CA requires your explicit operating-system authentication.\n"
              "Downloading a certificate or accepting one browser warning is not proof of trust for the other origins.\n"
              "Rerun dogfoodPrepare after completing trust. Mac HTTPS, iPhone HTTPS and app signing are separate checks.\n"
              "This bundle contains public certificate material only and grants no application access.\n")
    payloads = {"ca.pem": pem, "trust.json": (json.dumps(descriptor, indent=2, sort_keys=True) + "\n").encode(),
                "README.md": readme.encode()}
    for name, data in payloads.items():
        path = destination / name
        if path.is_file() and path.read_bytes() == data:
            os.chmod(path, 0o644)
            continue
        fd, temporary_name = tempfile.mkstemp(prefix=".public-setup-", dir=destination)
        temporary = Path(temporary_name)
        try:
            with os.fdopen(fd, "wb") as stream:
                os.fchmod(stream.fileno(), 0o644)
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)
    return descriptor
