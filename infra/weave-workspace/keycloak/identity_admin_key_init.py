#!/usr/bin/env python3
"""Project the initialized Server-owned identity-admin key into public-only realm input."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any


class KeyPreparationError(RuntimeError):
    pass


PRIVATE_FIELDS = frozenset({"d", "p", "q", "dp", "dq", "qi", "oth", "k"})
PRIVATE_REQUIRED = frozenset({"kty", "use", "alg", "kid", "key_ops", "n", "e", "d", "p", "q", "dp", "dq", "qi"})
IDENTITY_ADMIN_KEY_REF = "secretref:keycloak/weave-identity-admin-jwk"


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def revision(value: dict[str, Any]) -> str:
    projection = dict(value)
    projection.pop("revision", None)
    return "sha256:" + hashlib.sha256(canonical(projection)).hexdigest()


def valid_private_jwk(value: object) -> bool:
    return (
        isinstance(value, dict)
        and PRIVATE_REQUIRED.issubset(value)
        and value.get("kty") == "RSA"
        and value.get("use") == "sig"
        and value.get("alg") == "PS256"
        and value.get("key_ops") == ["sign"]
        and all(isinstance(value.get(name), str) and value[name] for name in PRIVATE_REQUIRED - {"key_ops"})
    )


def public_jwks(private: dict[str, Any]) -> dict[str, Any]:
    public = {name: value for name, value in private.items() if name not in PRIVATE_FIELDS}
    public["key_ops"] = ["verify"]
    if set(public).intersection(PRIVATE_FIELDS):
        raise KeyPreparationError("public JWKS contains private key material")
    return {"keys": [public]}


def write(path: Path, value: object, mode: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    payload = canonical(value) + b"\n"
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
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


def load_private(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o777 != 0o600:
        raise KeyPreparationError(
            "identity administration private JWK must be an initialized mode-0600 regular file"
        )
    try:
        current = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise KeyPreparationError(
            "identity administration authority is not a private JWK; Fresh Start or explicit rotation is required"
        ) from error
    if valid_private_jwk(current):
        return current
    raise KeyPreparationError(
        "identity administration private JWK is invalid; Fresh Start or explicit rotation is required"
    )


def upgraded_desired_state(source: Path) -> dict[str, Any]:
    desired = json.loads(source.read_text(encoding="utf-8"))
    clients = desired.get("clients")
    if not isinstance(clients, list):
        raise KeyPreparationError("desired state has no client inventory")
    identity = [client for client in clients if client.get("key") == "client:weave-identity-admin"]
    if len(identity) != 1:
        raise KeyPreparationError("desired state must contain one identity administration client")
    client = identity[0]
    if client.get("clientId") != "weave-identity-admin" or client.get("serviceAccountsEnabled") is not True:
        raise KeyPreparationError("identity administration client shape is invalid")
    if client.get("authenticationMethod") not in {"client_secret_basic", "private_key_jwt"}:
        raise KeyPreparationError("identity administration authentication method is unsupported")
    client["authenticationMethod"] = "private_key_jwt"
    client["keyRef"] = IDENTITY_ADMIN_KEY_REF
    client.pop("secretRef", None)
    desired["revision"] = revision(desired)
    return desired


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--private-jwk", type=Path, default=Path("/authority/private/weave-identity-admin-private-jwk.json"))
    parser.add_argument("--desired", type=Path, default=Path("/authority/output/desired-state.json"))
    parser.add_argument("--public-jwks", type=Path, default=Path("/authority/output/identity-admin-public-jwks.json"))
    parser.add_argument("--output-desired", type=Path, default=Path("/authority/output/desired-state.identity-admin-private-key-jwt.json"))
    args = parser.parse_args()
    try:
        private = load_private(args.private_jwk)
        write(args.public_jwks, public_jwks(private), 0o600)
        write(args.output_desired, upgraded_desired_state(args.desired), 0o644)
    except (KeyPreparationError, OSError, json.JSONDecodeError) as error:
        print(f"WEAVE_IDENTITY_ADMIN_KEY_INIT_ERROR {error}", file=os.sys.stderr)
        return 1
    print("identity-admin-key-init: public realm projection prepared; values withheld")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
