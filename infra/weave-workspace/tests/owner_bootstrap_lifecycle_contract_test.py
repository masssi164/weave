#!/usr/bin/env python3
"""Static and focused unit evidence for ephemeral dogfood owner bootstrap."""

from __future__ import annotations

import json
import os
import re
import sys
import tempfile
import unittest
from contextlib import ExitStack
from dataclasses import replace
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import compose_runtime  # noqa: E402
from compose_env import ContractError, load_context  # noqa: E402
from render_config import render_keycloak  # noqa: E402
from realm_renderer import MACHINE_KEY_PROJECTIONS  # noqa: E402


class OwnerBootstrapSmtpProfileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temporary = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.contexts, cls.realms = {}, {}
        temporary = Path(cls.temporary.name).resolve()
        for profile, domain in (("e2e", "weave.localhost"), ("dogfood", "weave.test")):
            selected = temporary / (profile + ".env")
            value = (ROOT / f"environments/{profile}.env.example").read_text()
            value = re.sub(r"sha256:replace-with-[a-zA-Z0-9.-]+", "sha256:" + "a" * 64, value)
            value = value.replace("weave.test", domain)
            selected.write_text(value)
            selected.chmod(0o600)
            with patch.dict(os.environ, {"WEAVE_E2E_STACK_SCOPE": "isolated" if profile == "e2e" else "persistent",
                                         "WEAVE_E2E_RUN_ID": "owner-bootstrap-smtp-profile"}):
                for key in ("WEAVE_E2E_RUN_NAMESPACE", "WEAVE_E2E_NAMESPACE"):
                    os.environ.pop(key, None)
                context = load_context(profile, ROOT, str(selected))
            context = replace(context, env={**context.env,
                "WEAVE_GENERATED_ROOT": str(temporary / profile / "generated"),
                "WEAVE_SECRET_ROOT": str(temporary / profile / "secrets"),
                "WEAVE_TLS_ROOT": str(temporary / profile / "tls"),
            })
            # SMTP qualification uses the real canonical realm renderer. Only
            # its unrelated public signing-key input is a deterministic fixture.
            for _, public_name in MACHINE_KEY_PROJECTIONS.values():
                public = context.generated_root / "keycloak/public-jwks" / public_name
                public.parent.mkdir(parents=True, exist_ok=True)
                public.write_text(json.dumps({"keys": [{"alg": "PS256", "e": "AQAB", "key_ops": ["verify"],
                    "kid": "smtp-public-fixture", "kty": "RSA", "n": "smtp-public-fixture-modulus", "use": "sig"}]}))
                public.chmod(0o644)
            render_keycloak(context)
            cls.contexts[profile] = context
            cls.realms[profile] = (context.generated_root / "keycloak/import/weave-realm.json").read_bytes()

    def setUp(self) -> None:
        for profile, context in self.contexts.items():
            (context.generated_root / "keycloak/import/weave-realm.json").write_bytes(self.realms[profile])

    def test_real_e2e_renderer_uses_configured_sender_with_required_implicit_tls(self) -> None:
        context = self.contexts["e2e"]
        realm = json.loads(self.realms["e2e"])
        self.assertEqual(context.env["WEAVE_TENANT_DOMAIN"], "weave.localhost")
        self.assertEqual(context.env["WEAVE_MAILPIT_REQUIRE_TLS"], "true")
        self.assertEqual(realm["smtpServer"], {"from": "noreply@weave.localhost", "fromDisplayName": "Weave",
            "host": "mailpit", "port": "1025", "ssl": "true", "starttls": "false"})
        compose_runtime._owner_bootstrap_smtp_preflight(context)

    def test_real_dogfood_renderer_preserves_reviewed_https_origin_and_sender(self) -> None:
        context = self.contexts["dogfood"]
        self.assertEqual(context.env["WEAVE_PUBLIC_URL"], "https://weave.test")
        self.assertEqual(json.loads(self.realms["dogfood"])["smtpServer"]["from"], "noreply@weave.test")
        compose_runtime._owner_bootstrap_smtp_preflight(context)

    def test_each_profile_rejects_changed_sender_transport_or_extra_credentials(self) -> None:
        mutations = ({"from": "wrong@example.test"}, {"fromDisplayName": "Unexpected"},
                     {"host": "smtp.example.test"}, {"port": "465"}, {"ssl": "false"},
                     {"starttls": "true"}, {"auth": "true"}, {"user": "private-fixture-user"},
                     {"password": "private-fixture-password"})
        for profile, context in self.contexts.items():
            for mutation in mutations:
                with self.subTest(profile=profile, changedField=next(iter(mutation))):
                    realm = json.loads(self.realms[profile])
                    realm["smtpServer"].update(mutation)
                    (context.generated_root / "keycloak/import/weave-realm.json").write_text(json.dumps(realm))
                    with self.assertRaisesRegex(ContractError, "implicit-TLS Mailpit SMTP baseline") as failure:
                        compose_runtime._owner_bootstrap_smtp_preflight(context)
                    self.assertNotIn("private-fixture", str(failure.exception))

    def test_dogfood_rejects_the_e2e_sender(self) -> None:
        context = self.contexts["dogfood"]
        realm = json.loads(self.realms["dogfood"])
        realm["smtpServer"]["from"] = "noreply@weave.localhost"
        (context.generated_root / "keycloak/import/weave-realm.json").write_text(json.dumps(realm))
        with self.assertRaises(ContractError):
            compose_runtime._owner_bootstrap_smtp_preflight(context)

    def test_stale_tls_render_cannot_override_invalid_effective_mail_profile(self) -> None:
        for profile, context in self.contexts.items():
            with self.subTest(profile=profile):
                invalid = replace(context, env={**context.env, "WEAVE_MAILPIT_REQUIRE_TLS": "false"})
                with self.assertRaisesRegex(ContractError, "implicit-TLS Mailpit SMTP baseline"):
                    compose_runtime._owner_bootstrap_smtp_preflight(invalid)


class OwnerBootstrapLifecycleContractTest(unittest.TestCase):
    def test_isolated_e2e_owner_operation_uses_existing_owned_resources_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = SimpleNamespace(environment="e2e", isolated_namespace="weave-e2e-owned",
                                      generated_root=Path(temporary).resolve() / "generated")
            with patch.object(compose_runtime, "resource_inventory", return_value={("volume", "owned")}), \
                    patch.object(compose_runtime, "inspect_resource", return_value=(True, False)), \
                    patch.object(compose_runtime, "_owner_bootstrap_locked") as operation:
                with self.assertRaisesRegex(ContractError, "existing owned"):
                    compose_runtime.owner_bootstrap(context, [])
                operation.assert_not_called()

    def test_isolated_e2e_first_invitation_has_temporary_authority_and_canonical_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, ExitStack() as stack:
            root = Path(temporary).resolve()
            context = SimpleNamespace(environment="e2e", isolated_namespace="weave-e2e-owned",
                                      generated_root=root / "generated", secret_root=root / "secrets",
                                      repository_root=root, tls_root=root / "tls",
                                      env={"WEAVE_RUNTIME_UID": str(os.getuid()), "WEAVE_RUNTIME_GID": str(os.getgid()),
                                           "WEAVE_API_ORIGIN": "https://weave.test"})
            context.secret_root.mkdir(mode=0o700)
            request = root / "request.json"
            request.write_text(json.dumps({"displayName": "Owner", "email": "owner@example.test",
                                           "idempotencyKey": "owner-e2e-bootstrap-0001"}))
            request.chmod(0o600)
            container = {"Image": "sha256:" + "a" * 64, "Config": {"Env": []}, "Mounts": []}
            def mock(name, **kwargs):
                return stack.enter_context(patch.object(compose_runtime, name, **kwargs))
            mock("resource_inventory", return_value={("volume", "owned")})
            mock("inspect_resource", return_value=(True, True))
            mock("prepare")
            mock("_owner_bootstrap_smtp_preflight")
            mock("_await_healthy")
            readiness = mock("verify_dogfood_identity")
            mock("_service_container", return_value=container)
            restore = mock("_canonical_backend", return_value=container)
            disabled = mock("_bootstrap_disabled", return_value=True)
            mock("compose_environment", return_value={})
            mock("_mailpit_recipient_summaries", side_effect=[{}, {"private-message-id": "current-time"}])
            def override(context, path, *arguments):
                payload = json.loads(path.read_text())["services"]["backend"]
                self.assertEqual(payload["environment"]["WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED"], "true")
                self.assertEqual(payload["volumes"][0]["target"], "/run/secrets/weave/bootstrap-owner")
                token = Path(payload["volumes"][0]["source"]) / "token"
                self.assertNotIn(token.read_text().strip(), json.dumps(payload))
            mock("_bootstrap_override_command", side_effect=override)
            def helper(context, command, **kwargs):
                self.assertIn("--connect-loopback", command)
                evidence = Path(command[command.index("--evidence") + 1])
                evidence.write_text(json.dumps({"supportSafe": True, "requestedRole": "owner"}))
                return SimpleNamespace(stderr="", stdout="")
            # Patch only the helper process; all Docker operations above are mocks.
            mock("_bootstrap_process", side_effect=helper)
            compose_runtime.owner_bootstrap(context, ["--request-file", str(request)])
            readiness.assert_not_called()
            restore.assert_called_once()
            disabled.assert_called_once()
            self.assertEqual(list(context.secret_root.iterdir()), [])
            self.assertEqual(list((context.generated_root / "operations").glob("*.compose.json")), [])
            evidence = json.loads((context.generated_root / "owner-bootstrap/evidence.json").read_text())
            self.assertTrue(evidence["bootstrapAuthorityAbsent"])
            self.assertTrue(evidence["tokenAbsent"])

    def test_private_request_is_exact_and_not_world_readable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "owner.json"
            path.write_text(
                json.dumps(
                    {
                        "displayName": "Owner",
                        "email": "OWNER@example.test",
                        "idempotencyKey": "owner-bootstrap-run-0001",
                    }
                ),
                encoding="utf-8",
            )
            os.chmod(path, 0o600)
            request = compose_runtime._owner_bootstrap_request(path)
            self.assertEqual(request["email"], "owner@example.test")
            os.chmod(path, 0o640)
            with self.assertRaisesRegex(ContractError, "group or other"):
                compose_runtime._owner_bootstrap_request(path)

    def test_boundary_detects_any_bootstrap_environment_or_mount(self) -> None:
        canonical = {"Config": {"Env": ["SPRING_PROFILES_ACTIVE=prod"]}, "Mounts": []}
        self.assertFalse(compose_runtime._bootstrap_boundary_present(canonical))
        self.assertTrue(
            compose_runtime._bootstrap_boundary_present(
                {
                    "Config": {"Env": ["WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED=true"]},
                    "Mounts": [],
                }
            )
        )
        self.assertTrue(
            compose_runtime._bootstrap_boundary_present(
                {
                    "Config": {"Env": []},
                    "Mounts": [
                        {"Destination": "/run/secrets/weave/bootstrap-owner"}
                    ],
                }
            )
        )

    def test_mailpit_matching_reads_recipient_projection_only(self) -> None:
        self.assertEqual(
            compose_runtime._mailpit_addresses(
                {"Address": "Owner@Example.Test", "Name": "Owner"}
            ),
            ["owner@example.test"],
        )
        self.assertEqual(
            compose_runtime._mailpit_addresses("activation body is not inspected"), []
        )

    def test_source_has_ephemeral_directory_and_unconditional_canonicalization(self) -> None:
        source = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
        lifecycle = source.split("def owner_bootstrap(", 1)[1].split(
            "\ndef _remove_exact_dogfood_resource(", 1
        )[0]
        self.assertIn('context.environment == "dogfood" and context.isolated_namespace is None', lifecycle)
        self.assertIn('context.environment == "e2e" and context.isolated_namespace is not None', lifecycle)
        self.assertIn('"--no-deps"', lifecycle)
        self.assertIn('"--force-recreate"', lifecycle)
        self.assertIn('"read_only": False', lifecycle)
        self.assertIn('"/run/secrets/weave/bootstrap-owner/token"', lifecycle)
        self.assertIn("finally:", lifecycle)
        self.assertIn("_canonical_backend(context, deadline=restoration_deadline)", lifecycle)
        self.assertIn("_bootstrap_disabled(context, deadline=restoration_deadline)", lifecycle)
        self.assertLess(
            lifecycle.index("_private_json(\n                request_anchor_path"),
            lifecycle.index("_bootstrap_override_command("),
        )
        self.assertIn('"requestAnchorPresent": request_anchor_path.is_file()', lifecycle)
        self.assertIn("shutil.rmtree(operation_root)", lifecycle)
        self.assertNotIn("require_completed_migration(context)", lifecycle)
        self.assertNotIn('compose(context, "down"', lifecycle)
        self.assertNotIn("providerInvitationId", lifecycle)


if __name__ == "__main__":
    unittest.main(verbosity=2)
