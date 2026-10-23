"""Public-only renderer and exact Dogfood gateway boundary regressions."""
from __future__ import annotations

from dataclasses import replace
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from compose_env import ContractError, load_context
from dogfood_public_setup import PUBLIC_FILES, public_setup, write_public_setup
from rendering.gateway import render_caddy


class PublicSetupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.certificates = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.certificates.cleanup)
        cls.certificate = Path(cls.certificates.name) / "ca.pem"
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=Public setup fixture", "-addext", "basicConstraints=critical,CA:TRUE",
                        "-keyout", str(Path(cls.certificates.name) / "private-key.pem"), "-out", str(cls.certificate)],
                       check=True, capture_output=True, timeout=15)

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name).resolve()
        self.context = self.context_at(self.directory)

    @classmethod
    def context_at(cls, directory):
        tls = directory / "tls"; tls.mkdir(parents=True)
        shutil.copyfile(cls.certificate, tls / "ca.pem")
        (tls / "key.pem").write_text("private-key-must-never-be-copied")
        secrets = directory / "secrets"; secrets.mkdir()
        (secrets / "credential").write_text("credential-must-never-be-copied")
        return SimpleNamespace(environment="dogfood", profile="dogfood", isolated_namespace=None,
            generated_root=directory / "generated", tls_root=tls, secret_root=secrets,
            active_profiles=("dogfood",), env={
                "WEAVE_PUBLIC_URL": "https://org.fixture.invalid:44443", "WEAVE_API_ORIGIN": "https://api.fixture.invalid:44443",
                "WEAVE_API_URL": "https://api.fixture.invalid:44443/api", "WEAVE_AUTH_URL": "https://auth.fixture.invalid:44443",
                "WEAVE_MATRIX_URL": "https://provider.fixture.invalid:44443", "WEAVE_FILES_URL": "https://files.fixture.invalid:44443",
                "WEAVE_MAILPIT_URL": "https://mail.fixture.invalid:44443", "WEAVE_CHAT_PROVIDER": "weave-native",
                "WEAVE_FILES_PROVIDER": "weave-native", "WEAVE_CALENDAR_PROVIDER": "weave-native"})

    def test_public_bundle_fingerprint_closed_schema_no_secrets_and_idempotent(self):
        source_before = {p.name: p.read_bytes() for p in self.context.tls_root.iterdir()}
        descriptor = write_public_setup(self.context)
        root = self.context.generated_root / "public-setup"
        self.assertEqual({p.name for p in root.iterdir()}, PUBLIC_FILES)
        self.assertEqual(set(descriptor), {"schemaVersion", "environment", "organizationOrigin", "apiOrigin", "issuerOrigin",
                         "certificateUrl", "caSha256Fingerprint", "supportSafe", "containsSecretValues"})
        der = ssl.PEM_cert_to_DER_cert(self.certificate.read_text())
        expected = ":".join(re.findall("..", hashlib.sha256(der).hexdigest().upper()))
        self.assertEqual(descriptor["caSha256Fingerprint"], expected)
        self.assertEqual(descriptor["certificateUrl"], "https://org.fixture.invalid:44443/admin-console/setup/ca.pem")
        before = {p.name: (p.stat().st_ino, p.read_bytes()) for p in root.iterdir()}
        write_public_setup(self.context)
        self.assertEqual(before, {p.name: (p.stat().st_ino, p.read_bytes()) for p in root.iterdir()})
        self.assertEqual(source_before, {p.name: p.read_bytes() for p in self.context.tls_root.iterdir()})
        for path in root.iterdir():
            self.assertEqual(path.stat().st_mode & 0o777, 0o644)
            self.assertNotIn(b"private-key-must-never", path.read_bytes())
            self.assertNotIn(b"credential-must-never", path.read_bytes())

    def test_non_dogfood_and_unsafe_sources_fail_without_writing(self):
        for environment in ("dev", "prod", "e2e"):
            with self.subTest(environment=environment):
                self.context.environment = environment
                with self.assertRaises(ContractError): write_public_setup(self.context)
        self.context.environment = "dogfood"
        certificate = self.context.tls_root / "ca.pem"
        certificate.write_bytes(self.certificate.read_bytes() + b"\n-----BEGIN PRIVATE KEY-----\nsecret\n")
        with self.assertRaises(ContractError): write_public_setup(self.context)
        self.assertFalse(self.context.generated_root.exists())
        certificate.unlink(); certificate.symlink_to(self.certificate)
        with self.assertRaises(ContractError): write_public_setup(self.context)

    def test_unmanaged_files_symlinks_and_protected_destinations_are_rejected(self):
        root = self.context.generated_root / "public-setup"; root.mkdir(parents=True)
        extra = root / "key.pem"; extra.write_text("unrelated")
        with self.assertRaises(ContractError): write_public_setup(self.context)
        self.assertEqual(extra.read_text(), "unrelated")
        extra.unlink(); (root / "ca.pem").symlink_to(self.certificate)
        with self.assertRaises(ContractError): write_public_setup(self.context)
        for protected in (self.context.tls_root, self.context.secret_root / "public"):
            with self.assertRaises(ContractError): write_public_setup(self.context, protected)

    def test_real_gateway_has_only_two_exact_read_only_resources_and_no_production_serve(self):
        rendered = render_caddy(self.context)
        for name in ("ca.pem", "trust.json"):
            self.assertIn("path /admin-console/setup/" + name + "\n    method GET HEAD", rendered)
            self.assertIn("rewrite * /" + name, rendered)
        self.assertEqual(rendered.count("root * /weave-public-setup"), 2)
        self.assertIn('handle /admin-console/setup/* {\n    respond 404', rendered)
        self.assertIn('@internal path /api/internal/* /actuator/*', rendered)
        self.assertNotIn("browse", rendered)
        self.assertNotIn("root * /certs", rendered)
        for environment in ("prod", "dev", "e2e"):
            self.context.environment = environment; self.context.profile = environment
            self.context.active_profiles = (environment,)
            other = render_caddy(self.context)
            self.assertNotIn("/weave-public-setup", other)
            self.assertNotIn("path /admin-console/setup/ca.pem", other)
            self.assertIn('handle /admin-console/setup/* {\n    respond 404', other)
        dogfood = (ROOT / "compose.dogfood.yaml").read_text()
        self.assertIn("/public-setup/ca.pem:/weave-public-setup/ca.pem:ro", dogfood)
        self.assertIn("/public-setup/trust.json:/weave-public-setup/trust.json:ro", dogfood)
        self.assertNotIn("public-setup", (ROOT / "compose.prod.yaml").read_text())

    def test_full_renderer_uses_accepted_corpus_and_materialized_dogfood_configuration(self):
        from compose_profile_modular_contract_test import materialize_example
        from init_secrets import initialize
        from render_config import render
        selected = materialize_example("dogfood", self.directory / "reviewed.env")
        context = load_context("dogfood", ROOT, str(selected))
        context = replace(context, env={**context.env, "WEAVE_RUNTIME_UID": str(os.getuid()), "WEAVE_RUNTIME_GID": str(os.getgid())})
        initialize(context)
        render(context)
        descriptor = json.loads((context.generated_root / "public-setup/trust.json").read_text())
        self.assertEqual(descriptor["organizationOrigin"], context.env["WEAVE_PUBLIC_URL"].rstrip("/"))
        self.assertIn("path /admin-console/setup/ca.pem", (context.generated_root / "caddy/Caddyfile").read_text())


if __name__ == "__main__":
    unittest.main()
