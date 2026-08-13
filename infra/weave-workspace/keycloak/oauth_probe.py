#!/usr/bin/env python3
"""Support-safe OAuth/JWT probes used by the workload DCR verifier."""

from __future__ import annotations

import base64
import binascii
import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


class OAuthProbeError(RuntimeError):
    pass


def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _integer(value: str) -> int:
    return int.from_bytes(base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)), "big")


def _mgf(seed: bytes, length: int) -> bytes:
    output = bytearray()
    counter = 0
    while len(output) < length:
        output.extend(hashlib.sha256(seed + counter.to_bytes(4, "big")).digest())
        counter += 1
    return bytes(output[:length])


def _ps256(message: bytes, key: dict[str, Any]) -> bytes:
    try:
        modulus = _integer(str(key["n"]))
        private_exponent = _integer(str(key["d"]))
    except (KeyError, ValueError, binascii.Error) as error:
        raise OAuthProbeError("runtime administration private JWK is malformed") from error
    modulus_bits = modulus.bit_length()
    encoded_bits = modulus_bits - 1
    encoded_length = (encoded_bits + 7) // 8
    salt = os.urandom(32)
    message_digest = hashlib.sha256(message).digest()
    encoded_digest = hashlib.sha256(b"\x00" * 8 + message_digest + salt).digest()
    padding_length = encoded_length - len(encoded_digest) - len(salt) - 2
    if modulus_bits < 2048 or padding_length < 0:
        raise OAuthProbeError("runtime administration private JWK is malformed")
    data_block = b"\x00" * padding_length + b"\x01" + salt
    data_mask = _mgf(encoded_digest, encoded_length - len(encoded_digest) - 1)
    masked_data = bytearray(left ^ right for left, right in zip(data_block, data_mask))
    masked_data[0] &= 0xFF >> (8 * encoded_length - encoded_bits)
    encoded_message = bytes(masked_data) + encoded_digest + b"\xbc"
    signature = pow(int.from_bytes(encoded_message, "big"), private_exponent, modulus)
    return signature.to_bytes((modulus_bits + 7) // 8, "big")


def private_key_jwt_token_response(
    server: str,
    realm: str,
    client_id: str,
    private_jwk: dict[str, Any],
    assertion_audience: str | None = None,
) -> tuple[int, dict[str, Any]]:
    if (
        private_jwk.get("kty") != "RSA"
        or private_jwk.get("alg") != "PS256"
        or private_jwk.get("key_ops") != ["sign"]
        or not isinstance(private_jwk.get("kid"), str)
    ):
        raise OAuthProbeError("runtime administration private JWK is malformed")
    token_url = f"{server}/realms/{realm}/protocol/openid-connect/token"
    now = int(time.time())
    protected = _b64(
        json.dumps(
            {"alg": "PS256", "kid": private_jwk["kid"], "typ": "JWT"},
            separators=(",", ":"),
            sort_keys=True,
        ).encode("ascii")
    )
    claims = _b64(
        json.dumps(
            {
                "aud": assertion_audience or token_url,
                "exp": now + 60,
                "iat": now,
                "iss": client_id,
                "jti": str(uuid.uuid4()),
                "sub": client_id,
            },
            separators=(",", ":"),
            sort_keys=True,
        ).encode("ascii")
    )
    signing_input = f"{protected}.{claims}".encode("ascii")
    assertion = f"{protected}.{claims}.{_b64(_ps256(signing_input, private_jwk))}"
    request = urllib.request.Request(
        token_url,
        data=urllib.parse.urlencode(
            {
                "grant_type": "client_credentials",
                "client_id": client_id,
                "client_assertion_type": "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion": assertion,
            }
        ).encode("ascii"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            body = json.loads(response.read())
            if not isinstance(body, dict):
                raise OAuthProbeError("private_key_jwt response is malformed; response withheld")
            return response.status, body
    except urllib.error.HTTPError as error:
        try:
            body = json.loads(error.read(4096))
        except json.JSONDecodeError as parse_error:
            raise OAuthProbeError("private_key_jwt error response is malformed; response withheld") from parse_error
        if not isinstance(body, dict):
            raise OAuthProbeError("private_key_jwt error response is malformed; response withheld")
        return error.code, body
    except (urllib.error.URLError, json.JSONDecodeError) as error:
        raise OAuthProbeError("private_key_jwt request failed; response withheld") from error


def access_token_role_projection(
    access_token: str,
) -> tuple[set[str], dict[str, set[str]]]:
    try:
        segments = access_token.split(".")
        if len(segments) != 3:
            raise ValueError("JWT segment count")
        claims = json.loads(
            base64.urlsafe_b64decode(segments[1] + "=" * (-len(segments[1]) % 4)).decode("utf-8")
        )
        if not isinstance(claims, dict):
            raise ValueError("JWT claims")
        realm_access = claims.get("realm_access")
        if realm_access is None:
            realm_roles: list[str] = []
        elif (
            not isinstance(realm_access, dict)
            or set(realm_access) != {"roles"}
            or not isinstance(realm_access.get("roles"), list)
            or any(not isinstance(role, str) or not role for role in realm_access["roles"])
        ):
            raise ValueError("realm role projection")
        else:
            realm_roles = realm_access["roles"]
        resource_access = claims.get("resource_access") or {}
        if not isinstance(resource_access, dict):
            raise ValueError("client role projection")
        client_roles: dict[str, set[str]] = {}
        for client_id, access in resource_access.items():
            if (
                not isinstance(client_id, str)
                or not client_id
                or not isinstance(access, dict)
                or set(access) != {"roles"}
                or not isinstance(access.get("roles"), list)
                or any(not isinstance(role, str) or not role for role in access["roles"])
                or len(access["roles"]) != len(set(access["roles"]))
            ):
                raise ValueError("client role projection")
            client_roles[client_id] = set(access["roles"])
        if len(realm_roles) != len(set(realm_roles)):
            raise ValueError("realm role projection")
        return set(realm_roles), client_roles
    except (binascii.Error, UnicodeDecodeError, ValueError, json.JSONDecodeError) as error:
        raise OAuthProbeError("service-account token role projection is malformed; token withheld") from error


def administration_read_probe_status(
    server: str, realm: str, resource: str, access_token: str
) -> int:
    request = urllib.request.Request(
        f"{server}/admin/realms/{realm}/{resource}?max=1",
        headers={"Authorization": f"Bearer {access_token}"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            response.read(4096)
            return response.status
    except urllib.error.HTTPError as error:
        error.read(4096)
        return error.code
    except urllib.error.URLError as error:
        raise OAuthProbeError("administration read probe failed; response withheld") from error
