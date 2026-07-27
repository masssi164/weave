#!/usr/bin/env python3
"""Initialize stable local secret generations without placing values in env or output."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import secrets
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

KEYCLOAK_MODULE_ROOT = Path(__file__).resolve().parents[1] / "keycloak"
sys.path.insert(0, str(KEYCLOAK_MODULE_ROOT))

from compose_env import ComposeContext, ContractError, canonical_json, load_context
from crypto_runtime import OPENSSL  # noqa: E402


TEXT_SECRETS = (
    "keycloak-bootstrap-admin-password",
    "postgres-admin-password",
    "backend-db-password",
    "identity-reference-hmac-key",
    "keycloak-db-password",
    "mas-db-password",
    "synapse-db-password",
    "nextcloud-db-password",
    "control-db-password",
    "nextcloud-admin-password",
    "nextcloud-actor-token",
    "keycloak-weave-identity-admin",
    "keycloak-weave-agent-runtime-admin",
    "keycloak-nextcloud",
    "keycloak-matrix-mas",
    "mas-matrix-secret",
    "synapse-registration-shared-secret",
    "synapse-macaroon-secret-key",
    "synapse-form-secret",
    "matrix-appservice-as-token",
    "matrix-appservice-hs-token",
)
HEX_SECRETS = (
    # MAS requires exactly 32 bytes encoded as 64 lowercase hexadecimal
    # characters for database/cookie encryption.
    "mas-encryption-secret",
)
TEST_ONLY_SECRETS = ("identity-bootstrap-owner-token",)
PROD_ONLY_SECRETS = ("smtp-username", "smtp-password")
RSA_JWKS = (
    ("keycloak-weave-backend-jwk.json", "weave-backend-current"),
    ("keycloak-weave-mcp-server-jwk.json", "weave-mcp-server-current"),
)
PEM_KEYS = (
    ("mas-signing-key.pem", "RSA"),
)


def _mode(path: Path) -> int:
    return stat.S_IMODE(path.stat().st_mode)


def _assert_private_file(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        raise ContractError(f"secret must be a regular non-symlink file: {path}")
    if _mode(path) != 0o600:
        raise ContractError(f"secret must have mode 0600: {path}")


def _atomic_write(path: Path, payload: bytes) -> None:
    if path.exists() or path.is_symlink():
        _assert_private_file(path)
        return
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def _random_secret() -> bytes:
    return base64.urlsafe_b64encode(secrets.token_bytes(48)).rstrip(b"=") + b"\n"


def _random_hex_secret() -> bytes:
    return secrets.token_hex(32).encode("ascii") + b"\n"


def _read_der_length(value: bytes, offset: int) -> tuple[int, int]:
    first = value[offset]
    if first < 0x80:
        return first, offset + 1
    count = first & 0x7F
    if count == 0 or count > 4:
        raise ContractError("unsupported RSA DER length")
    return int.from_bytes(value[offset + 1 : offset + 1 + count], "big"), offset + 1 + count


def _read_der(value: bytes, offset: int, expected_tag: int) -> tuple[bytes, int]:
    if offset >= len(value) or value[offset] != expected_tag:
        raise ContractError("unexpected RSA DER shape")
    length, start = _read_der_length(value, offset + 1)
    end = start + length
    if end > len(value):
        raise ContractError("truncated RSA DER value")
    return value[start:end], end


def _integer_bytes(value: bytes) -> bytes:
    stripped = value.lstrip(b"\x00")
    return stripped or b"\x00"


def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _rsa_jwk(kid: str) -> bytes:
    generated = subprocess.run(
        [OPENSSL, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout
    der = b""
    for command in (
        [OPENSSL, "pkey", "-traditional", "-outform", "DER"],
        [OPENSSL, "rsa", "-outform", "DER"],
    ):
        converted = subprocess.run(
            command,
            input=generated,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if converted.returncode == 0 and converted.stdout:
            der = converted.stdout
            break
    if not der:
        raise ContractError("openssl cannot export a traditional RSA private key")
    sequence, end = _read_der(der, 0, 0x30)
    if end != len(der):
        raise ContractError("unexpected trailing RSA DER data")
    integers: list[bytes] = []
    offset = 0
    while offset < len(sequence):
        item, offset = _read_der(sequence, offset, 0x02)
        integers.append(_integer_bytes(item))
    if len(integers) != 9 or int.from_bytes(integers[0], "big") != 0:
        raise ContractError("openssl returned an unsupported RSA private-key representation")
    names = ("n", "e", "d", "p", "q", "dp", "dq", "qi")
    jwk = {"kty": "RSA", "use": "sig", "alg": "PS256", "kid": kid, "key_ops": ["sign"]}
    jwk.update({name: _b64(value) for name, value in zip(names, integers[1:])})
    return canonical_json(jwk) + b"\n"


def _pem(algorithm: str) -> bytes:
    command = [OPENSSL, "genpkey", "-algorithm", algorithm]
    if algorithm == "RSA":
        command.extend(("-pkeyopt", "rsa_keygen_bits:3072"))
    return subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def _generate_leaf_certificate(
    temporary_root: Path,
    ca_key: Path,
    ca_cert: Path,
    name: str,
    hosts: list[str],
) -> tuple[Path, Path]:
    key = temporary_root / f"{name}-key.pem"
    request = temporary_root / f"{name}-request.pem"
    cert = temporary_root / f"{name}-cert.pem"
    extension = temporary_root / f"{name}-extension.cnf"
    extension.write_text(
        "\n".join(
            (
                "basicConstraints=critical,CA:FALSE",
                "keyUsage=critical,digitalSignature,keyEncipherment",
                "extendedKeyUsage=serverAuth",
                "subjectAltName=" + ",".join(f"DNS:{host}" for host in hosts),
                "",
            )
        ),
        encoding="utf-8",
    )
    subprocess.run(
        [
            OPENSSL,
            "genpkey",
            "-algorithm",
            "RSA",
            "-pkeyopt",
            "rsa_keygen_bits:3072",
            "-out",
            key,
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    subprocess.run(
        [
            OPENSSL,
            "req",
            "-new",
            "-key",
            key,
            "-subj",
            f"/CN={hosts[0]}",
            "-out",
            request,
        ],
        check=True,
    )
    subprocess.run(
        [
            OPENSSL,
            "x509",
            "-req",
            "-in",
            request,
            "-CA",
            ca_cert,
            "-CAkey",
            ca_key,
            "-CAcreateserial",
            "-days",
            "397",
            "-sha256",
            "-extfile",
            extension,
            "-out",
            cert,
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return key, cert


def _generate_tls(context: ComposeContext) -> None:
    root = context.tls_root
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(root, 0o700)
    paths = {
        name: root / name
        for name in (
            "ca.pem",
            "ca-key.pem",
            "cert.pem",
            "key.pem",
            "mailpit-cert.pem",
            "mailpit-key.pem",
        )
    }
    if all(path.exists() for path in paths.values()):
        for path in paths.values():
            _assert_private_file(path)
        return
    if any(path.exists() for path in paths.values()):
        raise ContractError(f"partial TLS generation under {root}; restore or remove the incomplete generation explicitly")
    hosts = sorted(
        {
            context.env["WEAVE_TENANT_DOMAIN"],
            context.env["WEAVE_PUBLIC_URL"].split("//", 1)[1].split(":", 1)[0],
            context.env["WEAVE_API_ORIGIN"].split("//", 1)[1].split(":", 1)[0],
            context.env["WEAVE_AUTH_URL"].split("//", 1)[1].split(":", 1)[0],
            context.env["WEAVE_MATRIX_URL"].split("//", 1)[1].split(":", 1)[0],
            context.env["WEAVE_FILES_URL"].split("//", 1)[1].split(":", 1)[0],
        }
    )
    with tempfile.TemporaryDirectory(prefix="weave-tls-") as temporary:
        temp = Path(temporary)
        ca_key = temp / "ca-key.pem"
        ca_cert = temp / "ca.pem"
        subprocess.run([OPENSSL, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out", ca_key], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        subprocess.run([OPENSSL, "req", "-x509", "-new", "-key", ca_key, "-sha256", "-days", "825", "-subj", "/CN=Weave Local Compose CA", "-out", ca_cert], check=True)
        gateway_key, gateway_cert = _generate_leaf_certificate(
            temp,
            ca_key,
            ca_cert,
            "gateway",
            hosts,
        )
        mailpit_key, mailpit_cert = _generate_leaf_certificate(
            temp,
            ca_key,
            ca_cert,
            "mailpit",
            ["mailpit"],
        )
        generated = (
            (ca_key, paths["ca-key.pem"]),
            (ca_cert, paths["ca.pem"]),
            (gateway_key, paths["key.pem"]),
            (gateway_cert, paths["cert.pem"]),
            (mailpit_key, paths["mailpit-key.pem"]),
            (mailpit_cert, paths["mailpit-cert.pem"]),
        )
        for source, target in generated:
            _atomic_write(target, source.read_bytes())


def _validate_existing(context: ComposeContext) -> None:
    required = list(TEXT_SECRETS) + list(HEX_SECRETS) + [name for name, _ in RSA_JWKS] + [name for name, _ in PEM_KEYS]
    if context.profile == "test":
        required.extend(TEST_ONLY_SECRETS)
    if context.profile == "prod":
        required.extend(PROD_ONLY_SECRETS)
    for name in required:
        _assert_private_file(context.secret_root / name)
    for name in HEX_SECRETS:
        value = (context.secret_root / name).read_text(encoding="ascii").strip()
        if len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
            raise ContractError(f"secret must be a 32-byte lowercase hex value: {name}")
    appservice_tokens = tuple(
        (context.secret_root / name).read_bytes().strip()
        for name in ("matrix-appservice-as-token", "matrix-appservice-hs-token")
    )
    if not all(len(value) >= 64 for value in appservice_tokens) or len(set(appservice_tokens)) != 2:
        raise ContractError("Matrix Application Service tokens must be distinct high-entropy SecretRefs")
    for name, _ in RSA_JWKS:
        value = json.loads((context.secret_root / name).read_text(encoding="utf-8"))
        required_fields = {"kty", "kid", "n", "e", "d", "p", "q", "dp", "dq", "qi"}
        if (
            value.get("kty") != "RSA"
            or value.get("use") != "sig"
            or value.get("alg") != "PS256"
            or value.get("key_ops") != ["sign"]
            or not required_fields.issubset(value)
        ):
            raise ContractError(f"private JWK is incomplete: {name}")
    mcp_jwk = context.secret_root / "keycloak-weave-mcp-server-jwk.json"
    expected_uid = int(context.env["WEAVE_RUNTIME_UID"])
    if mcp_jwk.stat().st_uid != expected_uid:
        raise ContractError(
            f"MCP private JWK owner uid is {mcp_jwk.stat().st_uid}; runtime requires {expected_uid}"
        )
    if context.profile == "prod":
        for name in ("ca.pem", "cert.pem", "key.pem"):
            _assert_private_file(context.tls_root / name)


def initialize(context: ComposeContext) -> None:
    context.secret_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(context.secret_root, 0o700)
    if context.profile == "prod":
        _validate_existing(context)
        return
    for name in TEXT_SECRETS:
        _atomic_write(context.secret_root / name, _random_secret())
    for name in HEX_SECRETS:
        _atomic_write(context.secret_root / name, _random_hex_secret())
    if context.profile == "test":
        for name in TEST_ONLY_SECRETS:
            _atomic_write(context.secret_root / name, _random_secret())
    for name, kid in RSA_JWKS:
        path = context.secret_root / name
        if not path.exists():
            _atomic_write(path, _rsa_jwk(kid))
    for name, algorithm in PEM_KEYS:
        path = context.secret_root / name
        if not path.exists():
            _atomic_write(path, _pem(algorithm))
    _generate_tls(context)
    _validate_existing(context)
    manifest = {
        "schemaVersion": "weave.compose-secret-generation.v1",
        "environment": context.profile,
        "generationFingerprint": "sha256:" + hashlib.sha256(
            canonical_json(sorted(path.name for path in context.secret_root.iterdir() if path.is_file()))
        ).hexdigest(),
        "secretCount": len([path for path in context.secret_root.iterdir() if path.is_file()]),
        "containsSecretValues": False,
    }
    manifest_path = context.generated_root / "secret-generation.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    _atomic_write(manifest_path, json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8") + b"\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "test", "prod"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        initialize(context)
    except (ContractError, OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"WEAVE_SECRET_INIT_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"secret-init: verified {args.profile} generation (values withheld)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
