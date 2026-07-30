#!/usr/bin/env python3
"""Focused contracts for the live Keycloak DCR proof helper."""

from __future__ import annotations

import base64
import importlib.util
import json
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "scripts/verify_keycloak_dcr_contract.py"
SPEC = importlib.util.spec_from_file_location("verify_keycloak_dcr_contract", TARGET)
assert SPEC is not None and SPEC.loader is not None
target = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(target)


class VerifyKeycloakDcrContractTest(unittest.TestCase):
    def test_metadata_contains_only_public_workload_key_material(self) -> None:
        private = {
            "kty": "RSA",
            "use": "sig",
            "alg": "PS256",
            "kid": "test-current",
            "n": "modulus",
            "e": "AQAB",
            "d": "private",
            "p": "private",
        }

        value = target.metadata("weaver-cell-test", private)

        self.assertEqual(value["grant_types"], ["client_credentials"])
        self.assertEqual(value["response_types"], [])
        self.assertEqual(value["redirect_uris"], [])
        self.assertEqual(
            set(value["jwks"]["keys"][0]),
            {"kty", "use", "alg", "kid", "n", "e"},
        )
        self.assertNotIn("d", value["jwks"]["keys"][0])
        self.assertNotIn("p", value["jwks"]["keys"][0])

    def test_registration_binds_public_uri_while_calling_internal_endpoint(self) -> None:
        issuer = "https://auth.weave.test/realms/weave"
        direct = "http://127.0.0.1:18080/realms/weave/clients-registrations/openid-connect"
        client_id = "weaver-cell-test"
        private = {
            "kty": "RSA",
            "use": "sig",
            "alg": "PS256",
            "kid": "test-current",
            "n": "modulus",
            "e": "AQAB",
        }
        response = target.metadata(client_id, private)
        response.update(
            {
                "client_id": client_id,
                "registration_client_uri": (
                    issuer
                    + "/clients-registrations/openid-connect/"
                    + client_id
                ),
                "registration_access_token": "fixture-rat",
            }
        )

        with mock.patch.object(
            target, "exchange", return_value=(201, response)
        ) as exchange:
            uri, token = target.registration(
                direct, issuer, "fixture-admin-token", client_id, private
            )

        self.assertEqual(
            uri,
            issuer + "/clients-registrations/openid-connect/" + client_id,
        )
        self.assertEqual(token, "fixture-rat")
        self.assertEqual(exchange.call_args.args[0], direct)

    def test_realm_role_projection_is_bounded(self) -> None:
        claims = base64.urlsafe_b64encode(
            json.dumps(
                {"realm_access": {"roles": ["weaver-runtime"]}},
                separators=(",", ":"),
            ).encode("utf-8")
        ).rstrip(b"=").decode("ascii")

        self.assertEqual(
            target.access_token_realm_roles(f"e30.{claims}.signature"),
            {"weaver-runtime"},
        )
        self.assertEqual(target.access_token_realm_roles("not-a-token"), set())

    def test_exact_client_state_rejects_missing_post_policy_scope(self) -> None:
        client_id = "weaver-cell-test"
        private = {
            "kty": "RSA",
            "use": "sig",
            "alg": "PS256",
            "kid": "test-current",
            "n": "modulus",
            "e": "AQAB",
        }
        response = target.metadata(client_id, private)
        response.update(
            {
                "client_id": client_id,
                "registration_access_token": "fixture-rat",
            }
        )

        self.assertEqual(
            target.exact_client_state(response, client_id, private),
            "fixture-rat",
        )
        response.pop("scope")
        with self.assertRaisesRegex(
            target.ContractError,
            "exact workload contract",
        ):
            target.exact_client_state(response, client_id, private)
        response["scope"] = " ".join(target.APPROVED_SCOPES)
        response["provider_url"] = "https://forbidden.invalid"
        with self.assertRaisesRegex(
            target.ContractError,
            "exact workload contract",
        ):
            target.exact_client_state(response, client_id, private)

    def test_evidence_is_owner_only_and_contains_no_credentials(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "proof.json"
            target.atomic_evidence(
                path,
                {
                    "supportSafe": True,
                    "credentialsIncluded": False,
                },
            )

            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertNotIn("token", path.read_text(encoding="utf-8").casefold())

    def test_internal_spi_warning_scan_fails_closed_without_echoing_logs(self) -> None:
        clean = mock.Mock()
        clean.stdout = iter(["normal startup\n", "provider ready\n"])
        clean.wait.return_value = 0
        with mock.patch.object(target.subprocess, "Popen", return_value=clean):
            target.require_internal_spi_warning_absent("a" * 64)

        warned = mock.Mock()
        warned.stdout = iter(["KC-SERVICES0047 internal SPI diagnostic\n"])
        warned.wait.return_value = 0
        with mock.patch.object(target.subprocess, "Popen", return_value=warned):
            with self.assertRaisesRegex(
                target.ContractError,
                "forbidden internal-SPI provider",
            ):
                target.require_internal_spi_warning_absent("b" * 64)

    def test_negative_status_contract_accepts_only_protocol_rejections(self) -> None:
        for status in (400, 401, 403):
            self.assertEqual(
                target.rejected_status(status, "fixture"),
                "fixture",
            )
        with self.assertRaisesRegex(target.ContractError, "case=fixture"):
            target.rejected_status(201, "fixture")


if __name__ == "__main__":
    unittest.main()
