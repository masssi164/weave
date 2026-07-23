#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import nextcloud_reconcile as target  # noqa: E402


class NextcloudCredentialIdempotenceTest(unittest.TestCase):
    def context(self, secret_root: Path) -> SimpleNamespace:
        return SimpleNamespace(
            secret_root=secret_root,
            isolated_namespace=None,
            env={
                "WEAVE_AUTH_URL": "https://auth.weave.test",
                "WEAVE_NEXTCLOUD_ACTOR_USERNAME": "weave-backend",
            },
        )

    def test_matching_oidc_secret_does_not_replay_provider_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            secret = b"existing-oidc-secret"
            path = root / "keycloak-nextcloud"
            path.write_bytes(secret + b"\n")
            path.chmod(0o600)
            observed = SimpleNamespace(
                returncode=0,
                stdout=hashlib.sha256(secret).hexdigest().encode("ascii"),
                stderr=b"",
            )
            calls: list[tuple[tuple[object, ...], dict[str, object]]] = []

            def fake_exec(_context: object, *args: object, **kwargs: object) -> SimpleNamespace:
                calls.append((args, kwargs))
                return observed

            provider = {
                "identifier": "keycloak",
                "clientId": "nextcloud",
                "discoveryEndpoint": "https://auth.weave.test/realms/weave/.well-known/openid-configuration",
                "scope": "openid email profile",
                "settings": {
                    "groupProvisioning": True,
                    "checkBearer": True,
                    "bearerProvisioning": True,
                },
            }

            def fake_occ(_context: object, *args: str, **_kwargs: object) -> SimpleNamespace:
                if args[:2] == ("user_oidc:provider", "keycloak"):
                    return SimpleNamespace(returncode=0, stdout=json.dumps(provider).encode(), stderr=b"")
                if args[:2] == ("config:app:get", "user_oidc"):
                    return SimpleNamespace(returncode=0, stdout=b"0\n", stderr=b"")
                return SimpleNamespace(returncode=0, stdout=b"1\n", stderr=b"")

            with patch.object(target, "_exec", side_effect=fake_exec), patch.object(
                target, "_occ", side_effect=fake_occ
            ):
                mutations, projection_digest = target._configure_oidc(self.context(root))
                self.assertEqual(mutations, 0)
                self.assertRegex(projection_digest, r"^sha256:[0-9a-f]{64}$")
            self.assertFalse(any("user_oidc:provider" in " ".join(map(str, args)) for args, _ in calls))

    def test_managed_projection_includes_all_nonsecret_oidc_controls(self) -> None:
        source = (ROOT / "scripts/nextcloud_reconcile.py").read_text(encoding="utf-8")
        for field in (
            "discoveryEndpoint",
            "groupProvisioning",
            "checkBearer",
            "bearerProvisioning",
            "allowLocalRemoteServers",
            "oidcProviderBearerValidation",
            "allowInsecureHttp",
            "oidcManagedProjectionDigest",
        ):
            self.assertIn(field, source)

    def test_existing_actor_is_probed_and_never_reset(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "nextcloud-actor-token"
            path.write_text("existing-actor-secret\n", encoding="utf-8")
            path.chmod(0o600)
            occ_calls: list[tuple[str, ...]] = []

            def fake_occ(_context: object, *args: str, **_kwargs: object) -> SimpleNamespace:
                occ_calls.append(args)
                return SimpleNamespace(returncode=0, stdout=b"", stderr=b"")

            with patch.object(target, "_occ", side_effect=fake_occ), patch.object(
                target, "_dav_probe", return_value=207
            ), patch.object(target, "_exec") as execute:
                _username, _calendars, mutations = target._configure_actor(self.context(root))
            self.assertEqual(mutations, 0)
            self.assertFalse(any("user:resetpassword" in call for call in occ_calls))
            execute.assert_not_called()


if __name__ == "__main__":
    unittest.main()
