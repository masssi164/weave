#!/usr/bin/env python3
"""Check the effective dogfood OIDC boundary without reconciling realm state."""

from __future__ import annotations

import base64
import binascii
import json
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

from compose_env import ComposeContext, ContractError
from rendering.io import read_secret

KEYCLOAK_ROOT = Path(__file__).resolve().parents[1] / "keycloak"
if str(KEYCLOAK_ROOT) not in sys.path:
    sys.path.insert(0, str(KEYCLOAK_ROOT))

import oauth_probe  # noqa: E402

MAX_RESPONSE_BYTES = 64 * 1024
IDENTITY_CLIENT = "weave-identity-admin"


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        # Credentials and discovery must remain at the reviewed public origin.
        return None


def _blocked(code: str, *, stale: bool = False) -> ContractError:
    recovery = (
        " The persisted realm differs from the reviewed URLs. "
        "Run ./gradlew dogfoodReset only when discarding the dogfood session is intended; "
        "the operator-owned TLS identity is preserved."
        if stale
        else " Check the reviewed configuration, local CA, connectivity and identity credential."
    )
    return ContractError(
        f"dogfood identity readiness failed [{code}]."
        + recovery
        + " No realm configuration was changed; response bodies and credentials are withheld."
    )


def _discovery(opener: urllib.request.OpenerDirector, issuer: str) -> dict[str, object]:
    with opener.open(issuer + "/.well-known/openid-configuration", timeout=15) as response:
        if response.status != 200:
            raise _blocked("discovery-unavailable")
        payload = response.read(MAX_RESPONSE_BYTES + 1)
    if len(payload) > MAX_RESPONSE_BYTES:
        raise _blocked("discovery-response-invalid")
    value = json.loads(payload)
    if not isinstance(value, dict):
        raise _blocked("discovery-response-invalid")
    return value


def _verify_discovery(value: dict[str, object], issuer: str) -> None:
    base = issuer + "/protocol/openid-connect"
    expected = {
        "issuer": issuer,
        "authorization_endpoint": base + "/auth",
        "token_endpoint": base + "/token",
        "userinfo_endpoint": base + "/userinfo",
        "jwks_uri": base + "/certs",
        "end_session_endpoint": base + "/logout",
    }
    for field, url in expected.items():
        actual = value.get(field)
        if not isinstance(actual, str) or not actual:
            raise _blocked("discovery-response-invalid")
        if actual != url:
            raise _blocked("discovery-url-mismatch", stale=True)


def _verify_identity_token(token: object, issuer: str) -> None:
    if not isinstance(token, str) or len(token) > MAX_RESPONSE_BYTES:
        raise _blocked("identity-token-invalid")
    try:
        parts = token.split(".")
        if len(parts) != 3:
            raise ValueError("JWT shape")
        claims = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
        if not isinstance(claims, dict):
            raise ValueError("JWT claims")
    except (ValueError, UnicodeDecodeError, binascii.Error):
        raise _blocked("identity-token-invalid") from None
    # This is a readback of a token received directly over verified TLS, not a
    # replacement JWT verifier or an application authorization decision.
    if not isinstance(claims.get("iss"), str) or not claims["iss"]:
        raise _blocked("identity-token-invalid")
    if claims["iss"] != issuer:
        raise _blocked("identity-token-issuer-mismatch", stale=True)
    if claims.get("azp") != IDENTITY_CLIENT:
        raise _blocked("identity-token-client-mismatch")


def verify(context: ComposeContext) -> None:
    """Fail closed on URL drift or unusable owner-invitation client credentials."""

    if context.environment != "dogfood":
        raise ContractError("dogfood identity readiness is restricted to dogfood")
    base = context.env["WEAVE_AUTH_URL"].rstrip("/")
    parsed = urlsplit(base)
    if (
        parsed.scheme != "https" or not parsed.hostname or parsed.path
        or parsed.username or parsed.password or parsed.query or parsed.fragment
    ):
        raise _blocked("reviewed-origin-invalid")
    issuer = base + "/realms/weave"
    try:
        ca = context.tls_root / "ca.pem"
        if ca.is_symlink() or not ca.is_file():
            raise _blocked("local-ca-unavailable")
        tls = ssl.create_default_context(cafile=str(ca))
        # Match the existing owner-bootstrap policy for the stable local CA.
        # Python 3.13 strict key-usage checks must not force certificate rotation;
        # normal certificate-chain and hostname verification remain enabled.
        tls.verify_flags &= ~ssl.VERIFY_X509_STRICT
        opener = urllib.request.build_opener(
            _NoRedirect(), urllib.request.HTTPSHandler(context=tls)
        )
        _verify_discovery(_discovery(opener, issuer), issuer)
        key = json.loads(read_secret(context, "keycloak-weave-identity-admin-jwk.json"))
        if not isinstance(key, dict):
            raise _blocked("identity-credential-invalid")
        status, response = oauth_probe.private_key_jwt_token_response(
            base, "weave", IDENTITY_CLIENT, key, issuer, opener=opener
        )
        if status != 200:
            raise _blocked("identity-client-authentication-rejected")
        _verify_identity_token(response.get("access_token"), issuer)
    except ContractError as error:
        if str(error).startswith("dogfood identity readiness failed ["):
            raise error from None
        raise _blocked("identity-credential-unavailable") from None
    except Exception:
        raise _blocked("request-failed") from None
    print(
        "WEAVE_DOGFOOD_IDENTITY_READINESS status=passed "
        "discovery=matched identityAdmin=authenticated supportSafe=true"
    )
