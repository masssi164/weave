#!/usr/bin/env python3
"""Invoke the one-shot empty-realm owner invitation without exposing its SecretRef."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import ssl
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BOOTSTRAP_PATH = "/api/bootstrap/owner-invitation"
TOKEN_MINIMUM_BYTES = 32
TOKEN_MAXIMUM_BYTES = 512
IDEMPOTENCY_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create or replay the protected first owner invitation."
    )
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--request-file", type=Path, required=True)
    parser.add_argument("--ca-file", type=Path)
    parser.add_argument("--evidence", type=Path)
    return parser.parse_args()


def read_private_token(path: Path) -> str:
    resolved = path.expanduser().absolute()
    if path.is_symlink() or not resolved.is_file():
        raise ValueError("Owner bootstrap SecretRef is not a regular file")
    mode = stat.S_IMODE(resolved.stat().st_mode)
    if os.name == "posix" and mode & 0o077:
        raise ValueError("Owner bootstrap SecretRef must not grant group or other access")
    token = resolved.read_text(encoding="utf-8").strip()
    encoded = token.encode("utf-8")
    if not TOKEN_MINIMUM_BYTES <= len(encoded) <= TOKEN_MAXIMUM_BYTES:
        raise ValueError("Owner bootstrap SecretRef has an invalid length")
    return token


def read_private_request(path: Path) -> dict[str, str]:
    resolved = path.expanduser().absolute()
    if path.is_symlink() or not resolved.is_file():
        raise ValueError("Owner bootstrap request is not a regular file")
    mode = stat.S_IMODE(resolved.stat().st_mode)
    if os.name == "posix" and mode & 0o077:
        raise ValueError("Owner bootstrap request must not grant group or other access")
    try:
        value = json.loads(resolved.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError("Owner bootstrap request is not valid JSON") from error
    if not isinstance(value, dict) or set(value) != {
        "displayName",
        "email",
        "idempotencyKey",
    }:
        raise ValueError("Owner bootstrap request has an invalid shape")
    if not all(isinstance(item, str) for item in value.values()):
        raise ValueError("Owner bootstrap request values must be strings")
    return value


def endpoint(base_url: str) -> str:
    parsed = urllib.parse.urlsplit(base_url.rstrip("/"))
    if parsed.scheme != "https":
        raise ValueError("Owner bootstrap requires an HTTPS API base URL")
    if not parsed.netloc or parsed.query or parsed.fragment:
        raise ValueError("Owner bootstrap API base URL is invalid")
    return urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, parsed.path.rstrip("/") + BOOTSTRAP_PATH, "", "")
    )


def ssl_context(ca_file: Path | None) -> ssl.SSLContext:
    if ca_file is None:
        return ssl.create_default_context()
    if ca_file.is_symlink() or not ca_file.is_file():
        raise ValueError("Configured CA file is unavailable")
    return ssl.create_default_context(cafile=str(ca_file))


def support_safe_result(response: dict[str, object], email: str) -> dict[str, object]:
    required = (
        "invitationHandle",
        "organizationId",
        "lifecycleStatus",
        "provisioningStatus",
        "requestedRole",
    )
    missing = [field for field in required if not response.get(field)]
    if missing:
        raise ValueError(
            "Owner bootstrap returned an incomplete invitation projection: "
            + ", ".join(missing)
        )
    if response["requestedRole"] != "owner" or "capabilities" in response:
        raise ValueError("Owner bootstrap returned an invalid authority projection")
    return {
        "schemaVersion": "weave-owner-bootstrap-evidence-v2",
        "supportSafe": True,
        "invitationHandleSha256": hashlib.sha256(
            str(response["invitationHandle"]).encode("utf-8")
        ).hexdigest(),
        "organizationId": response["organizationId"],
        "emailSha256": hashlib.sha256(email.lower().encode("utf-8")).hexdigest(),
        "lifecycleStatus": response["lifecycleStatus"],
        "provisioningStatus": response["provisioningStatus"],
        "requestedRole": response["requestedRole"],
    }


def write_evidence(path: Path, result: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp-" + secrets.token_hex(8))
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            stream.write(json.dumps(result, indent=2, sort_keys=True) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        temporary.replace(path)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()


def main() -> int:
    args = parse_args()
    owner_request = read_private_request(args.request_file)
    email = owner_request["email"]
    display_name = owner_request["displayName"]
    idempotency_key = owner_request["idempotencyKey"]
    if not IDEMPOTENCY_PATTERN.fullmatch(idempotency_key):
        raise ValueError(
            "Idempotency key must be 16-128 environment-safe characters"
        )
    normalized_email = email.strip().lower()
    if "@" not in normalized_email or len(normalized_email) > 320:
        raise ValueError("A bounded owner email address is required")

    request = urllib.request.Request(
        endpoint(args.api_base_url),
        data=json.dumps(
            {"email": normalized_email, "displayName": display_name.strip() or None}
        ).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Idempotency-Key": idempotency_key,
            "X-Weave-Bootstrap-Token": read_private_token(args.token_file),
        },
    )
    try:
        with urllib.request.urlopen(
            request, context=ssl_context(args.ca_file), timeout=20
        ) as response:
            if response.status not in (200, 201):
                raise ValueError(
                    f"Owner bootstrap failed with HTTP status {response.status}"
                )
            payload = json.load(response)
    except urllib.error.HTTPError as failure:
        try:
            error = json.loads(failure.read().decode("utf-8"))
            code = error.get("code", "owner-bootstrap-failed")
        except (UnicodeDecodeError, json.JSONDecodeError):
            code = "owner-bootstrap-failed"
        raise ValueError(
            f"Owner bootstrap failed with HTTP status {failure.code} ({code})"
        ) from None

    result = support_safe_result(payload, normalized_email)
    if args.evidence is not None:
        write_evidence(args.evidence, result)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, urllib.error.URLError) as failure:
        print(f"owner-bootstrap: {failure}", file=sys.stderr)
        sys.exit(1)
