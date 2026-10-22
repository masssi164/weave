#!/usr/bin/env python3
"""Real local HTTPS evidence for isolated bootstrap routing without Docker."""
from __future__ import annotations

import contextlib
import http.server
import importlib.util
import io
import json
import os
import ssl
import subprocess
import sys
import tempfile
import threading
import types
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import bootstrap_https as transport
import compose_runtime as runtime
import default_space_bootstrap as space

SPEC = importlib.util.spec_from_file_location("bootstrap_owner_https", ROOT.parents[1] / "gradle/tasks/bootstrap-owner.py")
assert SPEC and SPEC.loader
owner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(owner)


class BootstrapHTTPSContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name).resolve()
        ca_configuration = cls.root / "ca.cnf"
        ca_configuration.write_text("[req]\ndistinguished_name=dn\nx509_extensions=ca\n[dn]\nCN=Fixture CA\n"
                                    "[ca]\nbasicConstraints=critical,CA:true\nkeyUsage=critical,keyCertSign,cRLSign\n"
                                    "subjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid:always\n")
        leaf_configuration = cls.root / "leaf.cnf"
        leaf_configuration.write_text("basicConstraints=critical,CA:false\nkeyUsage=critical,digitalSignature,keyEncipherment\n"
                                      "extendedKeyUsage=serverAuth\nsubjectAltName=DNS:api.fixture.invalid\n"
                                      "subjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer\n")
        commands = [
            ["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=Fixture CA",
             "-keyout", str(cls.root / "ca.key"), "-out", str(cls.root / "ca.pem"), "-config", str(ca_configuration)],
            ["openssl", "req", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=api.fixture.invalid",
             "-keyout", str(cls.root / "server.key"), "-out", str(cls.root / "server.csr")],
            ["openssl", "x509", "-req", "-in", str(cls.root / "server.csr"), "-CA", str(cls.root / "ca.pem"),
             "-CAkey", str(cls.root / "ca.key"), "-CAcreateserial", "-out", str(cls.root / "server.pem"),
             "-days", "1", "-extfile", str(leaf_configuration)],
        ]
        for command in commands:
            subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                self.rfile.read(int(self.headers.get("Content-Length", "0")))
                self.server.requests.append({"path": self.path, "host": self.headers.get("Host"),
                                             "space": self.headers.get(space.HEADER),
                                             "owner": self.headers.get("X-Weave-Bootstrap-Token")})
                if self.path == "/redirect":
                    self.send_response(302)
                    self.send_header("Location", f"https://api.fixture.invalid:{self.server.server_port}/leak")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                if self.server.disabled:
                    self.send_response(404)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                if self.path.endswith("/owner-invitation"):
                    payload = {"invitationHandle": "fixture-invitation", "organizationId": "fixture-organization",
                               "lifecycleStatus": "pending", "provisioningStatus": "pending", "requestedRole": "owner"}
                else:
                    payload = {"schemaVersion": "weave.default-space-bootstrap/v1", "status": "passed",
                               "spaceRef": "workspace-default", "outcome": "CREATED", "containsSecretValues": False,
                               **{flag: True for flag in space.RESPONSE_FLAGS}}
                body = json.dumps(payload).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            do_GET = do_POST
            def log_message(self, *args):
                pass
        cls.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.server.daemon_threads = True
        cls.server.requests, cls.server.sni, cls.server.disabled = [], [], False
        server_tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_tls.load_cert_chain(cls.root / "server.pem", cls.root / "server.key")
        server_tls.set_servername_callback(lambda connection, name, context: cls.server.sni.append(name))
        cls.server.socket = server_tls.wrap_socket(cls.server.socket, server_side=True)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.origin = f"https://api.fixture.invalid:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)
        cls.temporary.cleanup()

    def setUp(self):
        self.server.requests.clear()
        self.server.sni.clear()
        self.server.disabled = False
        self.context = types.SimpleNamespace(environment="e2e", isolated_namespace="weave-e2e-owned",
                                             tls_root=self.root, env={"WEAVE_API_ORIGIN": self.origin})

    def tls(self):
        return ssl.create_default_context(cafile=str(self.root / "ca.pem"))

    def test_loopback_preserves_hostname_sni_host_and_ca_with_ambient_proxy_ignored(self):
        request = urllib.request.Request(self.origin + "/api/bootstrap/default-space", data=b"{}", method="POST",
                                         headers={space.HEADER: "fixture-secret"})
        with patch.dict(os.environ, {"HTTPS_PROXY": "http://invalid.proxy.invalid:1", "https_proxy": "http://invalid.proxy.invalid:1"}):
            with transport.open_https(request, self.tls(), connect_loopback=True, timeout=3) as response:
                self.assertEqual(response.status, 200)
                response.read()
        self.assertEqual(self.server.sni, ["api.fixture.invalid"])
        self.assertEqual(self.server.requests[0]["host"], f"api.fixture.invalid:{self.server.server_port}")
        self.assertEqual(self.server.requests[0]["space"], "fixture-secret")

    def test_wrong_ca_and_wrong_hostname_fail_before_credentials_reach_http(self):
        for origin, tls in ((self.origin, ssl.create_default_context()),
                            (self.origin.replace("api.fixture.invalid", "wrong.fixture.invalid"), self.tls())):
            with self.subTest(origin=origin):
                request = urllib.request.Request(origin + "/api/bootstrap/default-space", data=b"{}", method="POST",
                                                 headers={space.HEADER: "fixture-secret"})
                with self.assertRaises(urllib.error.URLError) as failure:
                    transport.open_https(request, tls, connect_loopback=True, timeout=3)
                self.assertIsInstance(failure.exception.reason, ssl.SSLCertVerificationError)
        self.assertEqual(self.server.requests, [])

    def test_redirect_is_not_followed_and_never_forwards_bootstrap_credential(self):
        request = urllib.request.Request(self.origin + "/redirect", data=b"{}", method="POST",
                                         headers={space.HEADER: "fixture-secret"})
        with self.assertRaises(urllib.error.HTTPError) as failure:
            transport.open_https(request, self.tls(), connect_loopback=True, timeout=3)
        self.assertEqual(failure.exception.code, 302)
        failure.exception.close()
        self.assertEqual([item["path"] for item in self.server.requests], ["/redirect"])
        self.assertEqual(self.server.sni, ["api.fixture.invalid"])

    def test_dogfood_transport_keeps_original_dns_target(self):
        request = urllib.request.Request(self.origin + "/api/bootstrap/default-space", data=b"{}", method="POST")
        with patch.object(transport.socket, "create_connection", side_effect=OSError("fixture-stop")) as connection:
            with self.assertRaises(urllib.error.URLError):
                transport.open_https(request, self.tls(), connect_loopback=False, timeout=3)
        self.assertEqual(connection.call_args.args[0], ("api.fixture.invalid", self.server.server_port))

    def test_space_request_and_both_negative_probes_use_isolated_transport(self):
        result = space._request(self.context, "fixture-space-secret")
        self.assertTrue(result["ownerAccessVerified"])
        self.server.disabled = True
        self.assertTrue(space._disabled(self.context))
        self.assertTrue(runtime._bootstrap_disabled(self.context))
        self.assertEqual([item["path"] for item in self.server.requests],
                         ["/api/bootstrap/default-space", "/api/bootstrap/default-space", "/api/bootstrap/owner-invitation"])
        self.assertEqual(self.server.sni, ["api.fixture.invalid"] * 3)

    def test_owner_cli_uses_internal_loopback_flag_with_verified_https(self):
        token = self.root / "owner-token"
        request = self.root / "owner-request.json"
        evidence = self.root / "owner-evidence.json"
        token.write_text("fixture-owner-secret-" + "x" * 32)
        request.write_text(json.dumps({"email": "owner@example.test", "displayName": "Fixture",
                                       "idempotencyKey": "fixture-owner-invitation-0001"}))
        token.chmod(0o600)
        request.chmod(0o600)
        arguments = ["bootstrap-owner.py", "--api-base-url", self.origin, "--token-file", str(token),
                     "--request-file", str(request), "--ca-file", str(self.root / "ca.pem"),
                     "--evidence", str(evidence), "--connect-loopback"]
        output = io.StringIO()
        with patch.object(sys, "argv", arguments), contextlib.redirect_stdout(output):
            self.assertEqual(owner.main(), 0)
        self.assertEqual(self.server.sni, ["api.fixture.invalid"])
        self.assertEqual(self.server.requests[0]["owner"], token.read_text())
        self.assertNotIn(token.read_text(), evidence.read_text() + output.getvalue())
        self.assertTrue(json.loads(evidence.read_text())["supportSafe"])

    def test_unverified_tls_or_non_https_authority_is_rejected(self):
        unsafe = ssl._create_unverified_context()
        with self.assertRaisesRegex(ValueError, "CA and hostname"):
            transport.open_https(urllib.request.Request(self.origin), unsafe, connect_loopback=True)
        with self.assertRaisesRegex(ValueError, "HTTPS endpoint"):
            transport.open_https(urllib.request.Request("http://api.fixture.invalid/"), self.tls(), connect_loopback=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)
