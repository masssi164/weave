#!/usr/bin/env python3
"""Register one disposable MCP workload without creating an ARC Cell."""

from __future__ import annotations

import argparse
import json
import os
import re
import socket
import ssl
import sys
from pathlib import Path
from urllib.parse import urlsplit


SCRIPTS = Path(__file__).resolve().parents[2] / "infra/weave-workspace/scripts"
sys.path.insert(0, str(SCRIPTS))
sys.path.insert(0, str(SCRIPTS.parent / "keycloak"))
import oauth_probe  # noqa: E402
import verify_keycloak_dcr_contract as dcr  # noqa: E402


def private_write(path: Path, value: object) -> None:
    if path.exists() or path.is_symlink():
        raise dcr.ContractError("release workload SecretRef already exists")
    payload = json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def run(args: argparse.Namespace) -> None:
    issuer = args.issuer.rstrip("/")
    parts = urlsplit(issuer)
    if (
        parts.scheme != "https"
        or parts.hostname not in {"auth.weave.test", "auth.weave.localhost"}
        or parts.path != "/realms/weave"
        or parts.query or parts.fragment
        or not re.fullmatch(r"weaver-cell-[0-9a-f]{16}", args.client_id)
    ):
        raise dcr.ContractError("release MCP issuer or client coordinate is invalid")
    if args.ca.is_symlink() or not args.ca.is_file():
        raise dcr.ContractError("isolated CA is unavailable")
    root = args.workload_root
    if root.is_symlink() or not root.is_dir() or root.stat().st_mode & 0o022:
        raise dcr.ContractError("release workload SecretRef directory is unsafe")

    # The disposable host names are resolved by the JVM through its private hosts
    # file. Keep this Python-only lookup equally narrow and preserve TLS hostname
    # and CA verification; no machine-wide DNS or trust-store change is made.
    resolver = socket.getaddrinfo

    def isolated_lookup(host: str, *remaining: object, **options: object):
        return resolver("127.0.0.1" if host == parts.hostname else host,
                        *remaining, **options)

    socket.getaddrinfo = isolated_lookup  # type: ignore[assignment]
    ssl._create_default_https_context = lambda: ssl.create_default_context(cafile=args.ca)

    base = issuer.removesuffix("/realms/weave")
    admin_jwk = dcr.private_json(
        root / "weave/keycloak/weave-agent-runtime-admin"
    )
    status, response = oauth_probe.private_key_jwt_token_response(
        base, "weave", "weave-agent-runtime-admin", admin_jwk, issuer
    )
    token = response.get("access_token")
    if status != 200 or not isinstance(token, str) or not token:
        raise dcr.ContractError("runtime administration workload authentication failed")
    key = dcr.generated_jwk(args.client_id + "-current")
    dcr.registration(
        issuer + "/clients-registrations/openid-connect",
        issuer, "weave", token, args.client_id, key,
    )
    private_write(root / "release-mcp-client.json", {
        "clientId": args.client_id,
        "privateJwk": key,
    })


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--issuer", required=True)
    parser.add_argument("--ca", type=Path, required=True)
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--workload-root", type=Path, required=True)
    try:
        run(parser.parse_args())
    except Exception as error:
        print("WEAVE_RELEASE_MCP_SETUP_ERROR " + str(error).replace("\n", " ")[:256],
              file=sys.stderr)
        return 2
    print("WEAVE_RELEASE_MCP_SETUP_RESULT status=passed cellCreated=false supportSafe=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
