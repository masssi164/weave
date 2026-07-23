#!/usr/bin/env python3

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
sys.path.insert(0, str(ROOT / "keycloak"))
sys.path.insert(0, str(ROOT / "scripts"))

from admin_sanitizer import Operation, SanitizerDenied, SanitizerPolicy, SecretResolver  # noqa: E402
from compose_env import compose_environment, load_context  # noqa: E402
from crypto_runtime import OPENSSL  # noqa: E402
from install_keycloak_supervisor import (  # noqa: E402
    InstallError,
    PACKAGE_FILES,
    build_attestation,
    build_sudoers_policy,
    package_digest,
    package_files,
)
from receipt import sign_receipt, verify_receipt  # noqa: E402
from supervisor import _required_secret_refs  # noqa: E402


class SanitizerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        corpus = REPOSITORY.parent / "weave-specs/contracts/examples/keycloak-admin-sanitizer-profile.valid.json"
        if not corpus.is_file():
            lock = json.loads((REPOSITORY / "specs/weave-specs.lock.json").read_text())
            corpus = (REPOSITORY / lock["specCorpus"]["localPath"] / "contracts/examples/keycloak-admin-sanitizer-profile.valid.json").resolve()
        cls.profile = json.loads(corpus.read_text(encoding="utf-8"))

    def policy(self, mode: str = "apply") -> SanitizerPolicy:
        return SanitizerPolicy(self.profile, mode, "weave-reconcile-123e4567-e89b-12d3-a456-426614174000")

    def test_forbidden_precedes_allowlist(self) -> None:
        with self.assertRaises(SanitizerDenied):
            self.policy().register_request("GET", "/admin/realms/weave/clients/abc/client-secret", "none")

    def test_exact_query_binding_and_one_use_registration(self) -> None:
        policy = self.policy()
        target = "/admin/realms/weave/clients?first=0&max=100"
        policy.register_request("GET", target, "none")
        operation = policy.resolve("GET", target, b"", "")
        self.assertEqual(operation.operation_id, "clients")
        with self.assertRaises(SanitizerDenied):
            policy.resolve("GET", target, b"", "")
        with self.assertRaises(SanitizerDenied):
            policy.register_request("GET", "/admin/realms/weave/clients?first=0&max=99", "none")

    def test_mode_matrix_blocks_mutation_during_plan(self) -> None:
        body = b'{"enabled":true,"realm":"weave"}'
        digest = "sha256:" + __import__("hashlib").sha256(body).hexdigest()
        with self.assertRaises(SanitizerDenied):
            self.policy("plan").register_request("PUT", "/admin/realms/weave", digest)

    def test_projection_keeps_arrays_and_drops_credentials(self) -> None:
        body = json.dumps(
            [{"id": "123e4567-e89b-12d3-a456-426614174000", "clientId": "one", "redirectUris": ["https://safe.invalid/callback"], "secret": "forbidden"}],
            separators=(",", ":"),
        ).encode()
        operation = Operation("clients", "client", "none", {})
        projected = json.loads(self.policy().project(operation, 200, {}, body))
        self.assertEqual(projected[0]["redirectUris"], ["https://safe.invalid/callback"])
        self.assertNotIn("secret", projected[0])

    def test_nested_group_create_requires_bound_parent(self) -> None:
        policy = self.policy()
        body = b'{"name":"owners"}'
        target = "/admin/realms/weave/groups/123e4567-e89b-12d3-a456-426614174000/children"
        digest = "sha256:" + __import__("hashlib").sha256(body).hexdigest()
        policy.register_request(
            "POST",
            target,
            digest,
            {"resourceKey": "group:owners", "parentResourceKey": "group:weave-root"},
        )
        operation = policy.resolve("POST", target, body, "application/json")
        self.assertEqual(operation.operation_id, "group-children")
        with self.assertRaises(SanitizerDenied):
            other = SanitizerPolicy(self.profile, "apply", "weave-reconcile-123e4567-e89b-12d3-a456-426614174000")
            other.register_request(
                "POST",
                target,
                digest,
                {"resourceKey": "group:owners", "parentResourceKey": ""},
            )
            other.resolve("POST", target, body, "application/json")

    def test_top_level_group_create_rejects_child_binding(self) -> None:
        policy = self.policy()
        body = b'{"name":"owners"}'
        target = "/admin/realms/weave/groups"
        digest = "sha256:" + __import__("hashlib").sha256(body).hexdigest()
        policy.register_request(
            "POST",
            target,
            digest,
            {"resourceKey": "group:owners", "parentResourceKey": "group:weave-root"},
        )
        with self.assertRaises(SanitizerDenied):
            policy.resolve("POST", target, body, "application/json")

    def test_secret_resolver_projects_only_public_jwk(self) -> None:
        with tempfile.TemporaryDirectory(prefix="weave-secret-resolver-") as directory:
            root = Path(directory)
            private = root / "keycloak-weave-backend-jwk.json"
            private.write_text(
                json.dumps(
                    {
                        "kty": "RSA", "kid": "test", "alg": "PS256", "use": "sig",
                        "n": "modulus", "e": "AQAB", "d": "private", "p": "private-p",
                    }
                ),
                encoding="utf-8",
            )
            private.chmod(0o600)
            resolver = SecretResolver(root)
            resolved = json.loads(
                resolver.resolve_body(
                    b'{"attributes":{"jwks.string":"public-jwks:secretref:keycloak/weave-backend-jwk"}}',
                    "application/json",
                )
            )
            jwks = json.loads(resolved["attributes"]["jwks.string"])
            self.assertEqual(jwks["keys"][0]["kid"], "test")
            self.assertNotIn("d", jwks["keys"][0])


class ReceiptContractTest(unittest.TestCase):
    def test_ed25519_round_trip_and_tamper_denial(self) -> None:
        with tempfile.TemporaryDirectory(prefix="weave-receipt-test-") as directory:
            root = Path(directory)
            private = root / "private.pem"
            public = root / "public.pem"
            subprocess.run([OPENSSL, "genpkey", "-algorithm", "ED25519", "-out", private], check=True)
            subprocess.run([OPENSSL, "pkey", "-in", private, "-pubout", "-out", public], check=True)
            envelope = sign_receipt({"value": "bound"}, private, "test-kid")
            self.assertEqual(verify_receipt(envelope, public, expected_kid="test-kid"), {"value": "bound"})
            tampered = dict(envelope)
            tampered["payload"] = tampered["payload"][:-1] + ("A" if tampered["payload"][-1] != "A" else "B")
            with self.assertRaises(Exception):
                verify_receipt(tampered, public, expected_kid="test-kid")


class ProcessEnvironmentContractTest(unittest.TestCase):
    def test_closed_candidate_override_and_credential_exclusion(self) -> None:
        old = dict(os.environ)
        try:
            os.environ["WEAVE_BACKEND_IMAGE"] = "sha256:" + "a" * 64
            os.environ["WEAVE_MCP_IMAGE"] = "sha256:" + "b" * 64
            os.environ["WEAVE_BACKEND_PASSWORD"] = "not-compose-input"
            context = load_context("dev", ROOT)
            self.assertEqual(context.env["WEAVE_BACKEND_IMAGE"], "sha256:" + "a" * 64)
            process = compose_environment(context)
            self.assertNotIn("WEAVE_BACKEND_PASSWORD", process)
        finally:
            os.environ.clear()
            os.environ.update(old)


class SupervisorInstallerContractTest(unittest.TestCase):
    def test_manifest_and_attestation_are_deterministic_and_closed(self) -> None:
        files = {name: "sha256:" + format(index + 1, "064x") for index, name in enumerate(PACKAGE_FILES)}
        digest = package_digest(files)
        attestation = build_attestation(
            installed_path=Path("/opt/weave/keycloak-supervisor/reviewed/supervisor.py"),
            trust_key_sha256="sha256:" + "f" * 64,
            files=files,
            approved_image_digests=["sha256:" + "b" * 64, "sha256:" + "a" * 64],
            approved_sanitizer_image_digests=["sha256:" + "c" * 64],
            package_approval_ref="approval:keycloak-supervisor-package:review/42",
            image_approval_ref="approval:keycloak-image:review/43",
            operator_group="weave-deploy",
            sudoers_policy_path=Path("/etc/sudoers.d/weave-keycloak-supervisor-0123456789abcdef0123"),
            sudoers_policy_sha256="sha256:" + "d" * 64,
            system="Linux",
            machine="AARCH64",
        )
        self.assertEqual(attestation["packageDigest"], digest)
        self.assertEqual(
            attestation["approvedKeycloakImageDigests"],
            ["sha256:" + "a" * 64, "sha256:" + "b" * 64],
        )
        self.assertEqual(attestation["platform"], {"system": "linux", "machine": "aarch64"})
        self.assertNotIn("installedAt", attestation)
        self.assertEqual(attestation["privilegedInvocation"], "sudo-noninteractive-fixed-executable")

    def test_sudoers_policy_is_fixed_no_setenv_and_path_safe(self) -> None:
        payload = build_sudoers_policy(
            Path("/opt/weave/keycloak-supervisor/reviewed/supervisor.py"),
            "weave-deploy",
        ).decode("utf-8")
        self.assertIn("NOPASSWD:NOSETENV:", payload)
        self.assertIn("/opt/weave/keycloak-supervisor/reviewed/supervisor.py *", payload)
        self.assertNotIn("SETENV:", payload.replace("NOSETENV:", ""))
        with self.assertRaises(InstallError):
            build_sudoers_policy(Path("/opt/weave/unsafe path/supervisor.py"), "weave-deploy")

    def test_manifest_rejects_symlinked_source_module(self) -> None:
        with tempfile.TemporaryDirectory(prefix="weave-supervisor-package-") as directory:
            root = Path(directory).resolve()
            payload = root / "payload.py"
            payload.write_text("pass\n", encoding="utf-8")
            for name in PACKAGE_FILES:
                (root / name).write_text("pass\n", encoding="utf-8")
            (root / PACKAGE_FILES[0]).unlink()
            (root / PACKAGE_FILES[0]).symlink_to(payload)
            with self.assertRaises(InstallError):
                package_files(root)

    def test_attestation_rejects_mutable_image_reference(self) -> None:
        files = {name: "sha256:" + "1" * 64 for name in PACKAGE_FILES}
        with self.assertRaises(InstallError):
            build_attestation(
                installed_path=Path("/opt/weave/keycloak-supervisor/reviewed/supervisor.py"),
                trust_key_sha256="sha256:" + "f" * 64,
                files=files,
                approved_image_digests=["quay.io/keycloak/keycloak:latest"],
                approved_sanitizer_image_digests=["sha256:" + "c" * 64],
                package_approval_ref="approval:keycloak-supervisor-package:review/42",
                image_approval_ref="approval:keycloak-image:review/43",
                operator_group="weave-deploy",
                sudoers_policy_path=Path("/etc/sudoers.d/weave-keycloak-supervisor-0123456789abcdef0123"),
                sudoers_policy_sha256="sha256:" + "d" * 64,
            )

    def test_candidate_runner_cannot_verify_or_consume_its_own_receipt(self) -> None:
        candidate_runner = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
        supervisor = (ROOT / "keycloak/supervisor.py").read_text(encoding="utf-8")
        self.assertNotIn("verify_receipt", candidate_runner)
        self.assertNotIn("consume_receipt", candidate_runner)
        self.assertNotIn("signingKeyPath", candidate_runner.replace('if "signingKeyPath" in attestation:', ""))
        self.assertIn('["sudo", "--non-interactive"]', candidate_runner)
        self.assertIn("def accept_receipt", supervisor)
        self.assertIn("verify_receipt(envelope", supervisor)
        self.assertIn("verify_receipt(\n            report_attestation", supervisor)
        self.assertIn("self.controller.consume_receipt", supervisor)

    def test_secret_projection_is_exact_and_bootstrap_secret_is_not_container_state(self) -> None:
        driver = (ROOT / "keycloak/kcadm_driver.py").read_text(encoding="utf-8")
        refs = _required_secret_refs(
            {
                "client": "secretref:keycloak/nextcloud",
                "jwks": "public-jwks:secretref:keycloak/weave-mcp-server-jwk",
                "irrelevant": "plain-value",
            }
        )
        self.assertEqual(
            refs,
            ("secretref:keycloak/nextcloud", "secretref:keycloak/weave-mcp-server-jwk"),
        )
        self.assertIn("self.secret_projection", driver)
        self.assertNotIn('src={self.secret_root},dst=/run/weave/secrets', driver)
        self.assertIn('"docker", "exec", "--env", "KC_CLI_CLIENT_SECRET"', driver)
        create_section = driver.split("def _create_kcadm", 1)[1].split("def _assert_direct_route_denied", 1)[0]
        self.assertNotIn('"--env", "KC_CLI_CLIENT_SECRET"', create_section)
        self.assertIn('{{json .Config.Env}}', create_section)

    def test_success_evidence_is_derived_from_post_apply_verification(self) -> None:
        supervisor = (ROOT / "keycloak/supervisor.py").read_text(encoding="utf-8")
        self.assertIn('verifier.reconcile_resources("verify")', supervisor)
        self.assertIn('verifier.reconcile_associations("verify", verified_inventory)', supervisor)
        self.assertIn('all(action.get("action") == "noop"', supervisor)
        self.assertIn('"observedHash": observed_digest', supervisor)
        self.assertNotIn('"observedHash": desired_revision', supervisor)
        self.assertNotIn('"projectedObservedStateDigest": desired_revision', supervisor)

    def test_public_attestation_uses_opaque_key_generation_only(self) -> None:
        files = {name: "sha256:" + "2" * 64 for name in PACKAGE_FILES}
        attestation = build_attestation(
            installed_path=Path("/opt/weave/keycloak-supervisor/reviewed/supervisor.py"),
            trust_key_sha256="sha256:" + "f" * 64,
            files=files,
            approved_image_digests=["sha256:" + "a" * 64],
            approved_sanitizer_image_digests=["sha256:" + "b" * 64],
            package_approval_ref="approval:keycloak-supervisor-package:review/42",
            image_approval_ref="approval:keycloak-image:review/43",
            operator_group="weave-deploy",
            sudoers_policy_path=Path("/etc/sudoers.d/weave-keycloak-supervisor-0123456789abcdef0123"),
            sudoers_policy_sha256="sha256:" + "d" * 64,
        )
        self.assertEqual(attestation["keyGenerationRef"], "keyref:keycloak-supervisor/current")
        self.assertNotIn("signingKeyPath", attestation)


if __name__ == "__main__":
    unittest.main()
