#!/usr/bin/env python3
"""Exercise effective dogfood OIDC checks at their network/credential boundary."""

from __future__ import annotations

import base64
import io
import json
import ssl
import sys
import tempfile
import traceback
import unittest
import urllib.error
from contextlib import ExitStack, redirect_stdout
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from urllib.parse import parse_qs

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import compose_runtime  # noqa: E402
import dogfood_identity_readiness as readiness  # noqa: E402
from compose_env import ContractError  # noqa: E402


def token(issuer: str, client: str = "weave-identity-admin") -> str:
    payload = base64.urlsafe_b64encode(json.dumps({"iss": issuer, "azp": client}).encode())
    return "e30." + payload.rstrip(b"=").decode() + ".signature"


class Response(io.BytesIO):
    status = 200


class DogfoodIdentityReadinessTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        self.base = "https://auth.weave.test"
        self.issuer = self.base + "/realms/weave"
        self.context = SimpleNamespace(
            environment="dogfood", profile="dogfood", active_profiles=(),
            tls_root=root, secret_root=root,
            generated_root=root / "generated", isolated_namespace=None,
            env={"WEAVE_AUTH_URL": self.base},
        )
        (root / "ca.pem").write_text("fixture CA", encoding="ascii")
        self.key = root / "keycloak-weave-identity-admin-jwk.json"
        self.key.write_text(json.dumps({
            "kty": "RSA", "alg": "PS256", "key_ops": ["sign"], "kid": "test-key",
        }), encoding="ascii")
        self.key.chmod(0o600)
        protocol = self.issuer + "/protocol/openid-connect"
        self.discovery = {
            "issuer": self.issuer,
            "authorization_endpoint": protocol + "/auth",
            "token_endpoint": protocol + "/token",
            "userinfo_endpoint": protocol + "/userinfo",
            "jwks_uri": protocol + "/certs",
            "end_session_endpoint": protocol + "/logout",
        }
        self.requests = []
        self.token_response = {"access_token": token(self.issuer)}
        self.token_status = 200
        self.failure = None
        self.tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        self.tls.verify_flags |= ssl.VERIFY_X509_STRICT
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.ca_loader = self.stack.enter_context(
            patch.object(readiness.ssl, "create_default_context", return_value=self.tls)
        )
        self.opener_builder = self.stack.enter_context(
            patch.object(readiness.urllib.request, "build_opener",
                         return_value=SimpleNamespace(open=self.open_request))
        )
        self.stack.enter_context(patch.object(readiness.oauth_probe, "_ps256", return_value=b"signature"))

    def open_request(self, request, timeout):
        self.assertEqual(timeout, 15)
        self.requests.append(request)
        if self.failure is not None:
            raise self.failure
        if isinstance(request, str):
            self.assertEqual(request, self.issuer + "/.well-known/openid-configuration")
            return Response(json.dumps(self.discovery).encode())
        self.assertEqual(request.full_url, self.issuer + "/protocol/openid-connect/token")
        response = Response(json.dumps(self.token_response).encode())
        response.status = self.token_status
        return response

    def assert_blocked(self, code: str, *, reset: bool = False) -> None:
        try:
            readiness.verify(self.context)
        except ContractError as error:
            rendered = "".join(traceback.format_exception(error))
            self.assertIn("[" + code + "]", str(error))
            self.assertEqual("dogfoodReset" in str(error), reset)
            self.assertNotIn("sensitive-provider-body", rendered)
        else:
            self.fail("invalid live state was accepted")

    def test_matching_public_boundary_and_current_client_assertion_pass(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            readiness.verify(self.context)
        self.ca_loader.assert_called_once_with(cafile=str(self.context.tls_root / "ca.pem"))
        handlers = self.opener_builder.call_args.args
        self.assertIsInstance(handlers[0], readiness._NoRedirect)
        self.assertIs(handlers[1]._context, self.tls)
        self.assertTrue(self.tls.check_hostname)
        self.assertEqual(self.tls.verify_mode, ssl.CERT_REQUIRED)
        self.assertFalse(self.tls.verify_flags & ssl.VERIFY_X509_STRICT)
        self.assertEqual(len(self.requests), 2)
        request = self.requests[-1]
        self.assertEqual(request.get_method(), "POST")
        form = parse_qs(request.data.decode())
        self.assertEqual(form["client_id"], ["weave-identity-admin"])
        assertion = form["client_assertion"][0].split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(assertion + "=" * (-len(assertion) % 4)))
        self.assertEqual(claims["aud"], self.issuer)
        self.assertEqual(claims["iss"], "weave-identity-admin")
        self.assertIn("status=passed", output.getvalue())
        self.assertNotIn("signature", output.getvalue())

    def test_each_obsolete_public_url_blocks_before_credentials_are_sent(self) -> None:
        for field in self.discovery:
            with self.subTest(field=field):
                previous = self.discovery[field]
                self.discovery[field] = previous.replace("auth.weave.test", "auth.weave.test:44443")
                self.requests.clear()
                self.assert_blocked("discovery-url-mismatch", reset=True)
                self.assertEqual(len(self.requests), 1)
                self.discovery[field] = previous

    def test_missing_endpoint_is_invalid_response_not_known_stale_state(self) -> None:
        del self.discovery["end_session_endpoint"]
        self.assert_blocked("discovery-response-invalid")

    def test_provider_responses_have_bounded_size(self) -> None:
        self.discovery["unexpected"] = "x" * readiness.MAX_RESPONSE_BYTES
        self.assert_blocked("discovery-response-invalid")
        del self.discovery["unexpected"]
        self.token_response = {"access_token": "x" * readiness.MAX_RESPONSE_BYTES}
        self.assert_blocked("request-failed")

    def test_rejected_assertion_or_wrong_current_key_fails_without_provider_body(self) -> None:
        self.token_status = 401
        self.token_response = {"error_description": "sensitive-provider-body"}
        self.assert_blocked("identity-client-authentication-rejected")

    def test_old_token_issuer_cannot_pass_current_discovery(self) -> None:
        self.token_response = {"access_token": token(self.issuer.replace(".test", ".test:44443"))}
        self.assert_blocked("identity-token-issuer-mismatch", reset=True)

    def test_wrong_token_client_is_rejected(self) -> None:
        self.token_response = {"access_token": token(self.issuer, "another-client")}
        self.assert_blocked("identity-token-client-mismatch")

    def test_invalid_tls_and_unknown_transport_failures_do_not_recommend_reset(self) -> None:
        for error in (
            ssl.SSLCertVerificationError("sensitive-provider-body"),
            urllib.error.URLError("sensitive-provider-body"),
            RuntimeError("sensitive-provider-body"),
        ):
            with self.subTest(error=type(error).__name__):
                self.failure = error
                self.assert_blocked("request-failed")

    def test_redirect_is_rejected_instead_of_forwarding_the_assertion(self) -> None:
        redirect = readiness._NoRedirect()
        self.assertIsNone(redirect.redirect_request(None, None, 307, "redirect", {}, "https://other.test"))
        self.failure = urllib.error.HTTPError(
            self.issuer, 307, "sensitive-provider-body", {}, io.BytesIO(b"sensitive-provider-body")
        )
        self.assert_blocked("request-failed")

    def test_nonprivate_or_symlink_credential_is_not_used(self) -> None:
        self.key.chmod(0o644)
        self.assert_blocked("identity-credential-unavailable")
        self.key.unlink()
        self.key.symlink_to(self.context.tls_root / "ca.pem")
        self.assert_blocked("identity-credential-unavailable")

    def test_malformed_tokens_do_not_claim_known_stale_state(self) -> None:
        for value in (None, "not-a-jwt", "e30.e30.signature"):
            with self.subTest(value=value):
                self.token_response = {"access_token": value}
                self.assert_blocked("identity-token-invalid")

    def test_readiness_failure_prevents_successful_up_without_reset_or_migration(self) -> None:
        with (
            patch.object(compose_runtime, "script"),
            patch.object(compose_runtime, "prepare"),
            patch.object(compose_runtime, "compose") as compose,
            patch.object(compose_runtime, "verify_dogfood_identity", side_effect=ContractError("drift")),
            patch.object(compose_runtime, "reset_dogfood") as reset,
            patch.object(compose_runtime, "keycloak_migration_apply") as migration,
        ):
            with self.assertRaisesRegex(ContractError, "drift"):
                compose_runtime.execute(self.context, "up", [])
        reset.assert_not_called()
        migration.assert_not_called()
        self.assertTrue(all(call.args[1] == "up" for call in compose.call_args_list))

    def test_direct_owner_bootstrap_checks_identity_before_backend_mutation(self) -> None:
        request = self.context.secret_root / "owner-request.json"
        request.write_text(json.dumps({
            "displayName": "Owner", "email": "owner@example.test", "idempotencyKey": "first-owner",
        }), encoding="utf-8")
        request.chmod(0o600)
        with (
            patch.object(compose_runtime, "prepare"),
            patch.object(compose_runtime, "_owner_bootstrap_smtp_preflight"),
            patch.object(compose_runtime, "_await_healthy"),
            patch.object(compose_runtime, "verify_dogfood_identity", side_effect=ContractError("drift")),
            patch.object(compose_runtime, "_canonical_backend") as backend,
            patch.object(compose_runtime, "_bootstrap_override_command") as override,
        ):
            with self.assertRaisesRegex(ContractError, "drift"):
                compose_runtime.owner_bootstrap(self.context, ["--request-file", str(request)])
        backend.assert_not_called()
        override.assert_not_called()
        self.assertFalse((self.context.generated_root / "owner-bootstrap/request-anchor.json").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
