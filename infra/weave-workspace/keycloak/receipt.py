#!/usr/bin/env python3
"""Canonical Ed25519 JWS helpers and atomic evidence writes."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import secrets
import subprocess
from pathlib import Path

from crypto_runtime import OPENSSL


class ReceiptError(RuntimeError):
    pass


def canonical_json(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_ref(value: bytes | object) -> str:
    payload = value if isinstance(value, bytes) else canonical_json(value)
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def decode_b64url(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def atomic_private_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.parent.is_symlink():
        raise ReceiptError("evidence directory must not be a symlink")
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(json.dumps(value, indent=2, sort_keys=True).encode("utf-8") + b"\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def sign_receipt(payload: dict[str, object], private_key: Path, kid: str) -> dict[str, str]:
    if private_key.is_symlink() or not private_key.is_file():
        raise ReceiptError("supervisor signing key is unavailable")
    protected = {
        "alg": "EdDSA",
        "contractVersion": "weave.keycloak-reconciliation-receipt/v1",
        "kid": kid,
        "typ": "weave.keycloak-reconciliation-receipt+jws",
    }
    protected_value = b64url(canonical_json(protected))
    payload_value = b64url(canonical_json(payload))
    signing_input = f"{protected_value}.{payload_value}".encode("ascii")
    import tempfile

    with tempfile.TemporaryDirectory(prefix="weave-receipt-sign-") as directory:
        root = Path(directory)
        message = root / "message"
        signed = root / "signature"
        message.write_bytes(signing_input)
        os.chmod(message, 0o600)
        result = subprocess.run(
            [
                OPENSSL, "pkeyutl", "-sign", "-rawin", "-inkey", str(private_key),
                "-in", str(message), "-out", str(signed),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        signature = signed.read_bytes() if signed.is_file() else b""
    if result.returncode != 0 or len(signature) != 64:
        raise ReceiptError("supervisor could not sign the reconciliation receipt")
    return {"protected": protected_value, "payload": payload_value, "signature": b64url(signature)}


def verify_receipt(
    envelope: dict[str, object], public_key: Path, *, expected_kid: str | None = None
) -> dict[str, object]:
    if set(envelope) != {"protected", "payload", "signature"}:
        raise ReceiptError("signed receipt envelope is malformed")
    try:
        protected = json.loads(decode_b64url(str(envelope["protected"])))
        payload_bytes = decode_b64url(str(envelope["payload"]))
        payload = json.loads(payload_bytes)
        signature = decode_b64url(str(envelope["signature"]))
    except (ValueError, json.JSONDecodeError) as error:
        raise ReceiptError("signed receipt encoding is malformed") from error
    if protected.get("alg") != "EdDSA" or protected.get("typ") != "weave.keycloak-reconciliation-receipt+jws":
        raise ReceiptError("signed receipt protected header is not allowed")
    if expected_kid is not None and protected.get("kid") != expected_kid:
        raise ReceiptError("signed receipt key id does not match the selected trust generation")
    signing_input = f"{envelope['protected']}.{envelope['payload']}".encode("ascii")
    import tempfile

    with tempfile.TemporaryDirectory(prefix="weave-receipt-verify-") as directory:
        root = Path(directory)
        message = root / "message"
        signed = root / "signature"
        message.write_bytes(signing_input)
        signed.write_bytes(signature)
        os.chmod(message, 0o600)
        os.chmod(signed, 0o600)
        result = subprocess.run(
            [
                OPENSSL, "pkeyutl", "-verify", "-rawin", "-pubin",
                "-inkey", str(public_key), "-sigfile", str(signed), "-in", str(message),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    if result.returncode != 0:
        raise ReceiptError("signed reconciliation receipt failed Ed25519 verification")
    if canonical_json(payload) != payload_bytes:
        raise ReceiptError("signed receipt payload is not RFC 8785 canonical")
    return payload
