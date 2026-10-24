"""Offline explicit-preparation preflight, lock and native-trust reducer checks."""
import copy
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import hashlib
import signal
from types import SimpleNamespace
from unittest.mock import Mock, patch
import unittest
import types

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dogfood_prepare as prepare
import dogfood_functional_smoke as smoke
from compose_env import ContractError
from compose_runtime import _dogfood_session_lock
from dogfood_lifecycle import effective_reviewed_env_file


class PrepareTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temporary = tempfile.TemporaryDirectory(); cls.addClassCleanup(temporary.cleanup)
        cls.certificate = Path(temporary.name) / "ca.pem"
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=Preparation fixture", "-addext", "basicConstraints=critical,CA:TRUE",
                        "-keyout", str(Path(temporary.name) / "private-key.pem"), "-out", str(cls.certificate)],
                       check=True, capture_output=True, timeout=15)

    def setUp(self):
        self.artifacts = types.ModuleType("artifact_consumers")
        self.artifacts.ArtifactError = type("ArtifactError", (ValueError,), {})
        self.artifacts.verify_runtime_container = Mock(return_value={})
        self.artifacts.native_bundle = Mock()
        self.artifacts.copy_driver_products = Mock()
        self.enterContext(patch.dict(sys.modules, {"artifact_consumers": self.artifacts}))
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name).resolve()
        tls = self.directory / "tls"; tls.mkdir()
        shutil.copyfile(self.certificate, tls / "ca.pem")
        self.context = SimpleNamespace(environment="dogfood", isolated_namespace=None,
            generated_root=self.directory / "generated", tls_root=tls, secret_root=self.directory / "secrets",
            env={"WEAVE_PUBLIC_URL": "https://org.fixture.invalid", "WEAVE_API_ORIGIN": "https://api.fixture.invalid",
                 "WEAVE_AUTH_URL": "https://auth.fixture.invalid", "WEAVE_API_URL": "https://api.fixture.invalid/api",
                 "WEAVE_COMPOSE_PROJECT": "weave-dogfood-fixture", "WEAVE_RESOURCE_PREFIX": "weave-dogfood-fixture"})

    def checks(self):
        return [{"origin": self.context.env[key], "status": "passed", "category": "trusted", "nativeCode": 0, "httpStatus": 200}
                for key in ("WEAVE_PUBLIC_URL", "WEAVE_API_ORIGIN", "WEAVE_AUTH_URL")]

    def test_missing_hosted_artifact_blocks_native_setup_and_both_invitation_authorities(self):
        for mode in ("operator", "console"):
            with self.subTest(mode=mode):
                fixture = self.directory / (mode + "/identities.json")
                result = {"trust": self.descriptor(), "localTrustStatus": "passed"}
                runner = smoke.Runner(self.directory)
                self.artifacts.native_bundle.side_effect = self.artifacts.ArtifactError("unavailable")
                def run(command, **kwargs):
                    if command[-1] == "HEAD": return "a" * 40
                    if command[-1].endswith("^{tree}"): return "b" * 40
                    return ""
                backend = {"Image": "sha256:" + "c" * 64,
                           "Config": {"Labels": {"org.opencontainers.image.revision": "a" * 40}}}
                with patch.object(smoke, "Runner", return_value=runner), patch.object(runner, "run", side_effect=run) as execute, \
                        patch.object(smoke, "inspect_toolchain", side_effect=smoke.NativeFailure("infrastructure", "rust-toolchain", "private-detail")) as inspect, \
                        patch.object(smoke, "select_runtime") as runtime, \
                        patch("compose_runtime.verify_dogfood_identity"), patch("compose_runtime._service_container", return_value=backend), \
                        patch.object(prepare, "operator_backend"), patch.object(prepare, "operator_driver") as operator, \
                        patch.object(prepare, "admin_driver") as console:
                    observed = prepare.prepare_members_onboarding(self.context, result, fixture, prepare.ledger_for(fixture), authorization_mode=mode)
                inspect.assert_not_called(); runtime.assert_not_called()
                operator.assert_not_called(); console.assert_not_called()
                self.assertTrue(all(call.args[0][0] == "git" for call in execute.call_args_list))
                self.assertEqual(observed["boundary"], "verified-hosted-simulator-artifact-required")
                self.assertFalse(observed["preparationComplete"])
                self.assertFalse(fixture.exists())
                self.assertNotIn("private-detail", json.dumps(observed))

    def descriptor(self):
        return prepare.write_public_setup(self.context)

    def test_local_trust_success_still_cannot_claim_administrator_or_functional_readiness(self):
        with patch.object(prepare, "native_trust", return_value=self.checks()) as probe:
            result = prepare.preflight(self.context, self.directory / "local-public-bundle")
        self.assertEqual(result["localTrustStatus"], "passed")
        self.assertEqual(result["status"], "incomplete")
        self.assertIs(result["preparationComplete"], False)
        self.assertEqual(result["boundary"], "administrator-preparation-required")
        self.assertEqual(probe.call_count, 1)
        self.assertEqual((self.directory / "local-public-bundle/ca.pem").read_bytes(), self.certificate.read_bytes())

    def test_only_untrusted_ca_requests_trust_and_other_failures_keep_their_boundaries(self):
        for category, boundary in (("tls-untrusted", "macos-ca-trust-required"),
                                   ("tls-ca-mismatch", "reviewed-tls-ca-mismatch"),
                                   ("tls-certificate-invalid", "reviewed-tls-certificate-invalid"),
                                   ("dns", "native-dns-unavailable"), ("timeout", "native-connectivity-required")):
            with self.subTest(category=category):
                checks = self.checks(); checks[1].update(status="incomplete", category=category, nativeCode=-1202)
                with patch.object(prepare, "native_trust", return_value=checks):
                    result = prepare.preflight(self.context, self.directory / "local-public-bundle")
                self.assertEqual(result["boundary"], boundary)
                self.assertEqual(result["localTrustStatus"], "incomplete")
                if category != "tls-untrusted": self.assertNotIn("explicitly install", result["humanAction"])

    def test_session_lock_rejects_competing_operation_before_any_bundle_or_probe(self):
        with _dogfood_session_lock(self.context), patch.object(prepare, "native_trust") as probe:
            with self.assertRaisesRegex(ContractError, "another dogfood"):
                prepare.preflight(self.context, self.directory / "local-public-bundle")
        probe.assert_not_called()
        self.assertFalse((self.context.generated_root / "public-setup").exists())

    def test_native_probe_reducer_requires_all_exact_origins_and_closed_results(self):
        descriptor = self.descriptor()
        valid = {"schemaVersion": "weave.dogfood-native-trust-check/v1", "checks": self.checks(),
                 "supportSafe": True, "containsSecretValues": False}
        response = SimpleNamespace(returncode=0, stdout=json.dumps(valid))
        with patch.object(prepare.sys, "platform", "darwin"), patch.object(prepare.subprocess, "run", return_value=response) as execute:
            self.assertEqual(prepare.native_trust(descriptor), self.checks())
        command = execute.call_args.args[0]
        self.assertEqual(command[0], "/usr/bin/swift")
        self.assertEqual(command[-3:], [c["origin"] for c in self.checks()])
        for mutation in ("missing-origin", "wrong-origin", "stale-extra-field", "false-success", "noninteger-status", "secret-field"):
            broken = copy.deepcopy(valid)
            if mutation == "missing-origin": broken["checks"].pop()
            if mutation == "wrong-origin": broken["checks"][0]["origin"] = "https://foreign.fixture.invalid"
            if mutation == "stale-extra-field": broken["request"] = "old-invocation"
            if mutation == "false-success": broken["checks"][0]["category"] = "tls-untrusted"
            if mutation == "noninteger-status": broken["checks"][0]["httpStatus"] = True
            if mutation == "secret-field": broken["checks"][0]["rawError"] = "private-payload"
            with self.subTest(mutation=mutation), patch.object(prepare.sys, "platform", "darwin"), patch.object(prepare.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout=json.dumps(broken))):
                with self.assertRaisesRegex(ContractError, "native-trust-probe-unavailable"):
                    prepare.native_trust(descriptor)

    def test_native_probe_error_and_nonmacos_never_emit_raw_error_or_success(self):
        descriptor = self.descriptor()
        with patch.object(prepare.sys, "platform", "darwin"), patch.object(prepare.subprocess, "run", side_effect=OSError("private-path-or-payload")):
            with self.assertRaisesRegex(ContractError, "^native-trust-probe-unavailable$"):
                prepare.native_trust(descriptor)
        with patch.object(prepare.sys, "platform", "linux"), patch.object(prepare.subprocess, "run") as execute:
            with self.assertRaisesRegex(ContractError, "^macos-native-trust-verification-required$"):
                prepare.native_trust(descriptor)
        execute.assert_not_called()

    def test_effective_reviewed_file_is_literal_fixed_default_ignores_ambient_and_rejects_symlinks(self):
        with patch.object(Path, "home", return_value=self.directory), patch.dict(os.environ, {"WEAVE_ENV_FILE": "/unreviewed/foreign.env"}):
            expected = self.directory / ".weave/dogfood/reviewed-compose.env"
            self.assertEqual(effective_reviewed_env_file(), expected)
            expected.parent.mkdir(parents=True)
            foreign = self.directory / "real.env"; foreign.write_text("fixture")
            expected.symlink_to(foreign)
            with self.assertRaises(ContractError): effective_reviewed_env_file()
            expected.unlink(); expected.parent.rmdir()
            expected.parent.symlink_to(self.directory, target_is_directory=True)
            with self.assertRaises(ContractError): effective_reviewed_env_file()

    def test_root_task_has_no_build_or_lifecycle_dependency_and_main_remains_incomplete(self):
        text = (prepare.ROOT / "gradle/tasks/architecture-lifecycle.gradle").read_text()
        task = text.split("tasks.register('dogfoodPrepare', Exec)", 1)[1].split("\n}\n", 1)[0]
        self.assertNotIn("dependsOn", task)
        self.assertIn("'python3', 'tools/dogfood_prepare.py'", task)
        with patch.object(prepare.sys, "argv", ["dogfood_prepare.py"]), patch.object(Path, "home", return_value=self.directory), \
                patch.dict(os.environ, {"WEAVE_ENV_FILE": "/unreviewed/foreign.env"}), \
                patch.object(prepare, "load_context", return_value=self.context) as load, \
                patch.object(prepare, "preflight", return_value={"boundary": "administrator-preparation-required", "stage": "local-trust", "status": "incomplete"}), \
                patch.object(prepare, "prepare_members", side_effect=lambda _c, result, *_p, **_k: result), \
                patch.object(prepare, "_private_json") as write, patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(prepare.main(), 2)
        self.assertEqual(load.call_args.args[2], str(self.directory / ".weave/dogfood/reviewed-compose.env"))
        self.assertEqual(write.call_count, 1)
        native = (prepare.ROOT / "infra/weave-workspace/scripts/dogfood_native_trust.swift").read_text()
        self.assertIn("SecTrustEvaluateWithError(trust, nil)", native)
        self.assertIn(".performDefaultHandling", native)
        self.assertIn("completionHandler(nil)", native)
        for forbidden in ("SecTrustSetAnchorCertificates", ".useCredential", "add-trusted-cert", "ignoreHTTPSErrors"):
            self.assertNotIn(forbidden, native)

    def receipt(self, plan, stage="invitations-ready", nonce=None):
        return {"schemaVersion": "weave.dogfood-admin-preparation-receipt/v1", "planId": plan["planId"],
                "nonce": nonce or plan["nonce"], "stage": stage, "organizationId": "organization-private-ref",
                "administratorAccountRef": "administrator-private-ref", "intendedOwnerAccountRef": "owner-private-ref",
                "ownerDisplayName": "Actual owner", "members": [
                    {"slot": m["slot"], "email": m["email"], "displayName": m["displayName"],
                     "invitationHandle": "invitation-" + m["slot"], **(
                         {"memberHandle": "member-" + m["slot"], "accountRef": "account-" + m["slot"], "grantVersion": '"version-' + m["slot"] + '"'}
                         if stage == "grants-ready" else {})} for m in plan["members"]],
                "supportSafe": True, "containsSecretValues": False}

    def proof(self, plan, grants):
        return {"schemaVersion": "weave.dogfood-admin-preparation-proof/v1", "planId": plan["planId"], "nonce": "f" * 32,
                "members": [{"slot": m["slot"], "accountDigest": prepare.identity_digest("weave-account", plan["issuerUrl"], m["accountRef"]),
                             "organizationDigest": prepare.identity_digest("weave-organization", plan["issuerUrl"], grants["organizationId"])} for m in grants["members"]]}

    def test_plan_is_reviewed_exact_and_private_ledger_retains_credentials_across_resume(self):
        path = self.directory / "private/ledger.json"
        first = prepare.preparation_ledger(self.context, path, "fingerprint")
        second = prepare.preparation_ledger(self.context, path, "fingerprint")
        self.assertEqual(first, second)
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)
        self.assertEqual(first["plan"]["members"][0]["spaceRole"], "EDIT")
        for key, changed in (("apiOrigin", "https://foreign.invalid"), ("issuerUrl", "http://auth.fixture.invalid/realms/weave"), ("nonce", "old")):
            invalid = copy.deepcopy(first["plan"]); invalid[key] = changed
            with self.subTest(key=key), self.assertRaises(ContractError): prepare.validate_plan(invalid, self.context)
        with self.assertRaises(ContractError): prepare.preparation_ledger(self.context, path, "changed-ca")
        invalid = copy.deepcopy(first); invalid["passwords"]["A"] = "lost"
        prepare.private_write(path, invalid)
        with self.assertRaises(ContractError): prepare.preparation_ledger(self.context, path, "fingerprint")

    def test_private_records_reject_symlinks_hardlinks_permissions_and_unclosed_payload(self):
        path = self.directory / "private/ledger.json"
        prepare.private_write(path, {"private": "value"})
        linked = path.with_name("linked.json"); os.link(path, linked)
        with self.assertRaises(ContractError): prepare.private_read(path)
        linked.unlink(); path.chmod(0o644)
        with self.assertRaises(ContractError): prepare.private_read(path)
        path.chmod(0o600); symbolic = path.with_name("symbolic.json"); symbolic.symlink_to(path)
        with self.assertRaises(ContractError): prepare.private_write(symbolic, {})
        path.parent.chmod(0o755)
        with self.assertRaises(ContractError): prepare.private_read(path)

    def test_receipt_requires_current_nonce_distinct_owner_and_exact_member_authority(self):
        plan = prepare.new_plan(self.context)
        valid = self.receipt(plan, "grants-ready")
        prepare.validate_receipt(valid, plan, "grants-ready", plan["nonce"])
        for mutation in ("nonce", "owner", "admin-changed", "email", "token", "missing-version", "duplicate-slot", "extra-member"):
            broken = copy.deepcopy(valid)
            previous = self.receipt(plan)
            if mutation == "nonce": broken["nonce"] = "b" * 32
            if mutation == "owner": broken["intendedOwnerAccountRef"] = "account-A"
            if mutation == "admin-changed": broken["administratorAccountRef"] = "different"
            if mutation == "email": broken["members"][0]["email"] = "someone@foreign.invalid"
            if mutation == "token": broken["accessToken"] = "private"
            if mutation == "missing-version": del broken["members"][0]["grantVersion"]
            if mutation == "duplicate-slot": broken["members"][1] = copy.deepcopy(broken["members"][0])
            if mutation == "extra-member": broken["members"].append(copy.deepcopy(broken["members"][0]))
            with self.subTest(mutation=mutation), self.assertRaises(ContractError):
                prepare.validate_receipt(broken, plan, "grants-ready", plan["nonce"], previous)

    def test_admin_driver_uses_private_stdin_and_never_clicks_approval_or_trusts_stale_receipt(self):
        plan = prepare.new_plan(self.context)
        directory = prepare.private_directory(self.directory / "driver")
        def execute(command, **kwargs):
            self.assertEqual(command[-1], "--dogfood-admin-preparation")
            self.assertEqual(command[0], "/usr/bin/swift")
            payload = json.loads(kwargs["input"])
            self.assertNotIn(plan["members"][0]["email"], " ".join(command))
            output = Path(payload["receiptOutputPath"])
            self.assertEqual(output.stat().st_mode & 0o777, 0o600)
            prepare.private_write(output, self.receipt(plan))
            return SimpleNamespace(returncode=0, stdout="DOGFOOD_ADMIN_PREPARATION_RESULT status=passed boundary=none supportSafe=true\n", stderr="PRIVATE")
        with patch.object(prepare.subprocess, "run", side_effect=execute):
            self.assertEqual(prepare.admin_driver(plan, directory, "invitations"), self.receipt(plan))
        self.assertEqual(list(directory.iterdir()), [])
        for boundary in ("reviewed-test-member-plan-approval-required", "normal-administrator-authentication-required", "administrator-session-unavailable"):
            with self.subTest(boundary=boundary), patch.object(prepare.subprocess, "run", return_value=SimpleNamespace(
                    returncode=2, stdout="DOGFOOD_ADMIN_PREPARATION_RESULT status=incomplete boundary=" + boundary + " supportSafe=true")):
                with self.assertRaisesRegex(ContractError, "^" + boundary + "$"):
                    prepare.admin_driver(plan, directory, "invitations")
        self.assertEqual(list(directory.iterdir()), [])

    def test_stock_mail_action_selection_validates_full_issuer_path_client_and_unique_query(self):
        issuer = "https://auth.fixture.invalid:4443/realms/weave"
        link = issuer + "/protocol/openid-connect/registrations?client_id=account&response_type=code&token=PRIVATE"
        self.assertEqual(prepare.action_links({"HTML": '<a href="' + link.replace("&", "&amp;") + '">Register</a>'}, issuer, False), {link})
        verify = issuer + "/login-actions/action-token?client_id=account&key=PRIVATE"
        self.assertEqual(prepare.action_links({"Text": verify}, issuer, True), {verify})
        for wrong in (link.replace(":4443", ":4444"), link.replace("https://", "http://"), link.replace("auth.fixture", "foreign.fixture"),
                      link.replace("client_id=account", "client_id=admin-console"), link + "&token=SECOND", link + "#fragment",
                      link.replace("/realms/weave", "/realms/foreign"), link.replace("https://auth", "https://user:password@auth")):
            with self.subTest(wrong=wrong): self.assertEqual(prepare.action_links({"Text": wrong}, issuer, False), set())

    def test_private_mail_inbox_matches_exact_recipient_and_rejects_ambiguous_or_foreign_detail(self):
        self.context.env["WEAVE_MAILPIT_WEB_HOST_PORT"] = "8025"
        issuer = "https://auth.fixture.invalid/realms/weave"; email = "test@org.fixture.invalid"
        link = issuer + "/protocol/openid-connect/registrations?client_id=account&response_type=code&token=PRIVATE"
        class Response:
            status = 200
            def __init__(self, payload): self.payload = payload
            def __enter__(self): return self
            def __exit__(self, *unused): pass
            def read(self, maximum): return json.dumps(self.payload).encode()
        def invoke(detail):
            calls = []
            def fetch(url, timeout):
                calls.append(url)
                self.assertTrue(url.startswith("http://127.0.0.1:8025/api/v1/"))
                return Response({"messages": [{"ID": "private-id", "To": [{"Address": email}]}], "total": 1}
                                if "/messages?" in url else detail)
            with patch.object(prepare.urllib.request, "build_opener", return_value=SimpleNamespace(open=fetch)) as opener:
                result = prepare.await_mail_link(self.context, email, issuer, False)
                self.assertIsInstance(opener.call_args.args[0], prepare.urllib.request.ProxyHandler)
                self.assertEqual(opener.call_args.args[0].proxies, {})
                return result, calls
        result, calls = invoke({"To": [{"Address": email}], "HTML": link})
        self.assertEqual(result, link); self.assertEqual(len(calls), 2)
        with self.assertRaisesRegex(ContractError, "^private-test-member-inbox-unavailable$"):
            invoke({"To": [{"Address": "other@org.fixture.invalid"}], "HTML": link})
        with self.assertRaisesRegex(ContractError, "^private-test-member-inbox-ambiguous$"):
            invoke({"To": [{"Address": email}], "HTML": link + " " + link.replace("PRIVATE", "SECOND")})

    def test_independent_proof_and_atomic_fixture_bind_selected_owner_not_admin(self):
        fixture = self.directory / "private/identities.json"; ledger_path = prepare.ledger_for(fixture)
        fingerprint = self.descriptor()["caSha256Fingerprint"]
        ledger = prepare.preparation_ledger(self.context, ledger_path, fingerprint)
        plan = ledger["plan"]; ledger["invitationReceipt"] = self.receipt(plan)
        grants = self.receipt(plan, "grants-ready", "f" * 32)
        proof = self.proof(plan, grants)
        prepare.publish_fixture(fixture, ledger_path, ledger, grants, proof, {"candidateCommit": "a" * 40})
        value = prepare.private_read(fixture)
        binding = prepare.fixture_binding(self.context, fixture, value)
        self.assertEqual(binding["owner"]["accountDigest"], prepare.identity_digest("weave-account", plan["issuerUrl"], "owner-private-ref"))
        self.assertNotEqual(binding["owner"]["accountDigest"], prepare.identity_digest("weave-account", plan["issuerUrl"], "administrator-private-ref"))
        self.assertEqual(set(value), {"schemaVersion", "organizationOrigin", "ownerDisplayName", "members"})
        broken = copy.deepcopy(proof); broken["members"][0]["accountDigest"] = broken["members"][1]["accountDigest"]
        with self.assertRaises(ContractError): prepare.verify_proof(broken, grants, plan)
        value["members"][0]["password"] = "changed"
        with self.assertRaises(ContractError): prepare.fixture_binding(self.context, fixture, value)

    def test_unapproved_plan_cleans_owned_simulators_without_publishing_fixture(self):
        result = {"trust": self.descriptor(), "localTrustStatus": "passed", "status": "incomplete", "preparationComplete": False}
        fixture = self.directory / "private/identities.json"
        commands = []
        fake_runner = SimpleNamespace(deadline=0, run=lambda command, **kwargs: commands.append(command) or
            ("a" * 40 if command[-1] in {"HEAD", "a" * 40 + "^{tree}"} else "b" * 40 if command[-1] == "HEAD^{tree}" else ""))
        backend = {"Image": "sha256:" + "c" * 64, "Config": {"Labels": {"org.opencontainers.image.revision": "a" * 40}}}
        # The deployed tree must be exact; return the corresponding tree here.
        def run(command, **kwargs):
            commands.append(command)
            if command[-1] == "HEAD": return "a" * 40
            if command[-1].endswith("^{tree}"): return "b" * 40
            return ""
        fake_runner.run = run
        def build(_runner, _context, _candidate, devices):
            devices.extend(["owned-A", "owned-B"])
            commands.append(["build-before-invitations"])
            return {}
        with patch.object(smoke, "Runner", return_value=fake_runner), patch("compose_runtime.verify_dogfood_identity"), \
                patch("compose_runtime._service_container", return_value=backend), patch.object(prepare, "build_actors", side_effect=build), \
                patch.object(prepare, "admin_driver", side_effect=ContractError("reviewed-test-member-plan-approval-required")):
            observed = prepare.prepare_members_onboarding(self.context, result, fixture, prepare.ledger_for(fixture), authorization_mode="console")
        self.assertEqual(observed["boundary"], "reviewed-test-member-plan-approval-required")
        self.assertFalse(observed["preparationComplete"])
        self.assertFalse(fixture.exists())
        self.assertEqual(commands[-2:], [["xcrun", "simctl", "delete", "owned-A"], ["xcrun", "simctl", "delete", "owned-B"]])
        self.assertIs(observed["ownedPreparationSimulatorsRemoved"], True)

    def execute_preparation(self, failure=None, cleanup_failure=False):
        result = {"trust": self.descriptor(), "localTrustStatus": "passed", "status": "incomplete", "preparationComplete": False}
        fixture = self.directory / "private/identities.json"
        calls = []
        def run(command, **kwargs):
            calls.append(command)
            if cleanup_failure and command[:3] == ["xcrun", "simctl", "delete"]:
                raise smoke.SmokeError("native-command-failed")
            if command[-1] == "HEAD": return "a" * 40
            if command[-1].endswith("^{tree}"): return "b" * 40
            return ""
        runner = SimpleNamespace(run=run, deadline=0)
        backend = {"Image": "sha256:" + "c" * 64, "Config": {"Labels": {"org.opencontainers.image.revision": "a" * 40}}}
        def build(_runner, _context, _candidate, devices):
            devices.extend(["owned-A", "owned-B"]); calls.append(["build"])
            if failure == "term": signal.raise_signal(signal.SIGTERM)
            return {slot: {"slot": slot} for slot in ("A", "B")}
        def admin(plan, _directory, operation, proof=None):
            calls.append([operation])
            if failure == "approval": raise ContractError("reviewed-test-member-plan-approval-required")
            return self.receipt(plan, "invitations-ready" if operation == "invitations" else "grants-ready", proof["nonce"] if proof else plan["nonce"])
        def member(_runner, actor, _context, plan, planned, password, invitation, state, record):
            self.assertEqual(prepare.private_read(prepare.ledger_for(fixture), 65536)["passwords"][actor["slot"]], password)
            calls.append(["own-app-identity", actor["slot"]])
            record("registration-complete")
            grants = self.receipt(plan, "grants-ready")
            return self.proof(plan, grants)["members"][0 if actor["slot"] == "A" else 1]
        before = signal.getsignal(signal.SIGTERM)
        with patch.object(smoke, "Runner", return_value=runner), patch("compose_runtime.verify_dogfood_identity"), \
                patch("compose_runtime._service_container", return_value=backend), patch.object(prepare, "build_actors", side_effect=build), \
                patch.object(prepare, "admin_driver", side_effect=admin), patch.object(prepare, "member_proof", side_effect=member):
            observed = prepare.prepare_members_onboarding(self.context, result, fixture, prepare.ledger_for(fixture), authorization_mode="console")
        self.assertEqual(signal.getsignal(signal.SIGTERM), before)
        return observed, fixture, calls

    def test_success_requires_two_ordinary_member_proofs_fresh_grants_and_cleanup_before_publication(self):
        result, fixture, calls = self.execute_preparation()
        self.assertTrue(result["preparationComplete"])
        self.assertEqual(result["boundary"], "ordinary-functional-smoke-required")
        self.assertEqual([call for call in calls if call[0] in {"build", "invitations", "own-app-identity", "grants"}],
                         [["build"], ["invitations"], ["own-app-identity", "A"], ["own-app-identity", "B"], ["grants"]])
        self.assertEqual(calls[-2:], [["xcrun", "simctl", "delete", "owned-A"], ["xcrun", "simctl", "delete", "owned-B"]])
        self.assertEqual(len(prepare.private_read(fixture)["members"]), 2)
        binding = prepare.fixture_binding(self.context, fixture, prepare.private_read(fixture))
        self.assertNotEqual(binding["owner"]["accountDigest"], binding["members"]["A"]["accountDigest"])

    def test_term_restores_signal_handler_and_cleans_only_owned_simulators_without_fixture(self):
        result, fixture, calls = self.execute_preparation(failure="term")
        self.assertEqual(result["boundary"], "administrator-preparation-terminated")
        self.assertFalse(result["preparationComplete"]); self.assertFalse(fixture.exists())
        self.assertEqual(calls[-2:], [["xcrun", "simctl", "delete", "owned-A"], ["xcrun", "simctl", "delete", "owned-B"]])

    def test_failed_owned_cleanup_prevents_fixture_even_after_valid_auth_and_grants(self):
        result, fixture, _calls = self.execute_preparation(cleanup_failure=True)
        self.assertEqual(result["boundary"], "owned-preparation-simulator-cleanup-required")
        self.assertFalse(result["preparationComplete"]); self.assertFalse(fixture.exists())

    def test_preparation_canonicalizes_only_its_new_directory_under_os_temp_alias(self):
        real_root = self.directory / "os-temp-real"; real_root.mkdir(mode=0o700)
        alias = self.directory / "os-temp-alias"; alias.symlink_to(real_root, target_is_directory=True)
        sentinel = real_root / "unrelated"; sentinel.write_text("preserve")
        original_temporary_directory = tempfile.TemporaryDirectory
        created = []; used = []; deleted = []
        def temporary_directory(**kwargs):
            temporary = original_temporary_directory(dir=alias, **kwargs)
            path = Path(temporary.name)
            self.assertIn(alias, path.parents)  # Do not resolve the reproduction away.
            self.assertTrue(alias.is_symlink())
            created.append(path)
            return temporary
        def runner(directory):
            used.append(directory)
            def run(command, **kwargs):
                if command[:3] == ["xcrun", "simctl", "delete"]: deleted.append(command[-1])
                if command[-1] == "HEAD": return "a" * 40
                if command[-1].endswith("^{tree}"): return "b" * 40
                return ""
            return SimpleNamespace(directory=directory, run=run, deadline=0)
        def build(runner, _context, _candidate, devices):
            # The existing XCTest producer also receives this canonical root.
            private = runner.directory / "private-xctest-input.json"
            prepare.private_write(private, {"owned": True})
            self.assertEqual(prepare.private_read(private), {"owned": True})
            devices.extend(["owned-A", "owned-B"])
            return {slot: {"slot": slot} for slot in ("A", "B")}
        receipts = []
        def native(command, **kwargs):
            self.assertEqual(command[-1], "--dogfood-admin-preparation")
            invocation = json.loads(kwargs["input"])
            path = Path(invocation["receiptOutputPath"])
            self.assertIn(real_root, path.parents)
            self.assertNotIn(alias, path.parents)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            plan = invocation["plan"]
            grants = invocation["operation"] == "grants"
            receipt = self.receipt(plan, "grants-ready" if grants else "invitations-ready",
                                   invocation["proof"]["nonce"] if grants else plan["nonce"])
            prepare.private_write(path, receipt)
            receipts.append(path)
            return SimpleNamespace(returncode=0, stdout="DOGFOOD_ADMIN_PREPARATION_RESULT status=passed boundary=none supportSafe=true\n")
        def member(_runner, actor, _context, plan, *args):
            return self.proof(plan, self.receipt(plan, "grants-ready"))["members"][0 if actor["slot"] == "A" else 1]
        backend = {"Image": "sha256:" + "c" * 64, "Config": {"Labels": {"org.opencontainers.image.revision": "a" * 40}}}
        fixture = self.directory / "private/identities.json"
        result = {"trust": self.descriptor(), "localTrustStatus": "passed", "status": "incomplete", "preparationComplete": False}
        with patch.object(prepare.tempfile, "TemporaryDirectory", side_effect=temporary_directory), \
                patch.object(smoke, "Runner", side_effect=runner), patch("compose_runtime.verify_dogfood_identity"), \
                patch("compose_runtime._service_container", return_value=backend), patch.object(prepare, "build_actors", side_effect=build), \
                patch.object(prepare, "member_proof", side_effect=member), patch.object(prepare.subprocess, "run", side_effect=native):
            observed = prepare.prepare_members_onboarding(self.context, result, fixture, prepare.ledger_for(fixture), authorization_mode="console")
        self.assertEqual(observed["boundary"], "ordinary-functional-smoke-required")
        self.assertIs(observed["preparationComplete"], True)
        self.assertEqual(used, [created[0].resolve()])
        self.assertEqual(len(receipts), 2)
        self.assertTrue(all(not path.exists() for path in [*created, *used, *receipts]))
        self.assertEqual(deleted, ["owned-A", "owned-B"])
        self.assertEqual(sentinel.read_text(), "preserve")
        self.assertTrue(alias.is_symlink())
        # Supplied paths receive no such normalization, including aliases that
        # happen to target the exact same operator-owned private directory.
        with self.assertRaises(ContractError): prepare.private_write(alias / "supplied.json", {})
        with self.assertRaises(ContractError): effective_reviewed_env_file(alias / "reviewed-compose.env")
        self.context.tls_root = alias
        with self.assertRaises(ContractError): prepare.public_setup(self.context)

    def test_registration_validates_owned_simulator_and_keeps_link_and_password_out_of_all_command_arguments(self):
        directory = prepare.private_directory(self.directory / "open-registration")
        products = prepare.private_directory(directory / "products")
        (products / "driver.xctestrun").write_bytes(b"fixture")
        identifier = "11111111-2222-3333-4444-555555555555"
        actor = {"products": products, "physical": False, "id": identifier,
                 "destination": "platform=iOS Simulator,id=" + identifier}
        issuer = "https://auth.fixture.invalid/realms/weave"
        link = issuer + "/protocol/openid-connect/registrations?client_id=account&response_type=code&token=PRIVATE"
        fixture = {"schemaVersion": "weave.dogfood-test-registration/v1", "phase": "ordinary-registration",
                   "appBundleIdentifier": "com.massimotter.weave", "issuerUrl": issuer,
                   "activationUrl": link, "password": "PRIVATE"}
        calls = []
        def run(arguments, **kwargs):
            calls.append((arguments, kwargs))
            return "" if arguments[0] == "xcrun" else "NATIVE_IOS_REGISTRATION_RESULT status=passed stage=registration-complete supportSafe=true\n"
        runner = SimpleNamespace(sequence=1, directory=directory, run=run)
        private = products / "private-member.json"
        def transport(_products, value): prepare.private_write(private, value); return private
        public = io.StringIO()
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), patch("sys.stdout", public):
            self.assertEqual(prepare.registration(runner, actor, fixture), "complete")
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][0][0], "xcodebuild")
        for arguments, _kwargs in calls:
            self.assertNotIn(link, json.dumps(arguments))
            self.assertNotIn(fixture["password"], json.dumps(arguments))
        self.assertNotIn(link, public.getvalue()); self.assertNotIn("PRIVATE", public.getvalue())
        self.assertFalse(private.exists())
        invalid = [({**actor, "physical": True}, fixture), ({**actor, "id": "physical-device"}, fixture),
                   ({**actor, "destination": "platform=iOS,id=" + identifier}, fixture),
                   ({**actor, "destination": "platform=iOS Simulator,id=other"}, fixture)]
        for changed in [link.replace("https://", "http://"), link.replace("auth.fixture", "foreign.fixture"),
                        link.replace("https://auth", "https://user:password@auth"),
                        link.replace("auth.fixture.invalid", "auth.fixture.invalid:444"),
                        link.replace("/realms/weave", "/realms/other"),
                        link.replace("/realms/weave/", "/realms/weave/../other/"),
                        link.replace("/realms/weave/", "/realms/weave/%2e%2e/other/"), "not-a-url"]:
            invalid.append((actor, {**fixture, "activationUrl": changed}))
        invalid.append((actor, {**fixture, "issuerUrl": "http://auth.fixture.invalid/realms/weave"}))
        invalid.append((actor, {**fixture, "issuerUrl": "https://@auth.fixture.invalid/realms/weave",
                               "activationUrl": link.replace("https://", "https://@")}))
        invalid.append((actor, {**fixture, "issuerUrl": "\n" + issuer}))
        for port in ("0", "65536", "invalid"):
            invalid.append((actor, {**fixture, "issuerUrl": issuer.replace(".invalid/", ".invalid:" + port + "/"),
                                   "activationUrl": link.replace(".invalid/", ".invalid:" + port + "/")}))
        for bad_actor, bad_fixture in invalid:
            calls.clear()
            with patch.object(smoke, "prepare_browser_fixture") as transfer, self.assertRaisesRegex(ContractError, "^normal-test-member-registration-required$"):
                prepare.registration(runner, bad_actor, bad_fixture)
            transfer.assert_not_called(); self.assertEqual(calls, [])
        # A native invocation failure still removes the private transport.
        runner.run = lambda *a, **k: (_ for _ in ()).throw(smoke.SmokeError("private URL detail"))
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), self.assertRaisesRegex(ContractError, "^normal-test-member-registration-required$"):
            prepare.registration(runner, actor, fixture)
        self.assertFalse(private.exists())

    def test_registration_requires_exact_single_ack_and_always_removes_private_transport(self):
        directory = prepare.private_directory(self.directory / "registration")
        products = prepare.private_directory(directory / "products")
        (products / "driver.xctestrun").write_bytes(b"fixture")
        issuer = "https://auth.fixture.invalid/realms/weave"
        fixture = {"schemaVersion": "weave.dogfood-test-registration/v1", "phase": "ordinary-registration",
                   "appBundleIdentifier": "com.massimotter.weave", "issuerUrl": issuer,
                   "password": "PRIVATE", "activationUrl": issuer + "/protocol/openid-connect/registrations?client_id=account&response_type=code&token=PRIVATE"}
        identifier = "11111111-2222-3333-4444-555555555555"
        actor = {"products": products, "physical": False, "id": identifier,
                 "destination": "platform=iOS Simulator,id=" + identifier}
        for status, result in (("status=incomplete stage=email-verification-required", "verification"),
                               ("status=incomplete stage=ordinary-app-sign-in-required", "sign-in"),
                               ("status=passed stage=registration-complete", "complete")):
            runner = SimpleNamespace(sequence=1, directory=directory, run=lambda *a, **k: "NATIVE_IOS_REGISTRATION_RESULT " + status + " supportSafe=true\n")
            private = products / "private-member.json"
            def transport(_products, value): prepare.private_write(private, value); return private
            with patch.object(smoke, "prepare_browser_fixture", side_effect=transport):
                self.assertEqual(prepare.registration(runner, actor, fixture), result)
            self.assertFalse(private.exists())
        runner.run = lambda *a, **k: "NATIVE_IOS_REGISTRATION_RESULT status=passed stage=registration-complete supportSafe=true\n" * 2
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), self.assertRaises(ContractError):
            prepare.registration(runner, actor, fixture)
        self.assertFalse(private.exists())
        expired = "NATIVE_IOS_REGISTRATION_RESULT status=incomplete stage=authentication-session-expired supportSafe=true\n"
        runner.run = lambda *a, **k: expired
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), self.assertRaisesRegex(ContractError, "^normal-test-member-authentication-session-expired$"):
            prepare.registration(runner, actor, fixture)
        self.assertFalse(private.exists())
        runner.run = lambda *a, **k: expired * 2
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), self.assertRaisesRegex(ContractError, "^normal-test-member-registration-required$"):
            prepare.registration(runner, actor, fixture)
        self.assertFalse(private.exists())

    def test_public_registration_handoff_requires_own_app_auth_and_identity_before_completion_checkpoint(self):
        for failure in (None, "auth", "identity"):
            with self.subTest(failure=failure):
                plan = prepare.new_plan(self.context); member = plan["members"][0]
                actor = {"physical": False, "id": "owned"}
                events = []; states = []
                def record(stage): states.append(stage); events.append(stage)
                def ui(_actor, fixture, method="testOrdinaryProductSmoke"):
                    if method == "testProductionBrowser":
                        events.append("PKCE")
                        if failure == "auth": raise smoke.SmokeError("normal-test-member-browser-authentication-required")
                    elif fixture.get("operation") == "identity":
                        events.append("identity")
                        return {"resources": {} if failure == "identity" else {"account": "a" * 64, "organization": "b" * 64}}
                    return {}
                runner = SimpleNamespace(ui=ui)
                with patch.object(prepare, "await_mail_link", side_effect=["private-invitation", "private-verification"]) as mail, \
                        patch.object(prepare, "registration", side_effect=["verification", "sign-in"]) as registration, \
                        patch.object(smoke, "restart"), patch.object(smoke, "verify_discovered_routing"), patch.object(smoke, "verify_installed_build"):
                    if failure is None:
                        observed = prepare.member_proof(runner, actor, self.context, plan, member, "private-password", {}, record_registration=record)
                        self.assertEqual(observed["accountDigest"], "a" * 64)
                    else:
                        with self.assertRaises((ContractError, smoke.SmokeError)):
                            prepare.member_proof(runner, actor, self.context, plan, member, "private-password", {}, record_registration=record)
                self.assertEqual(mail.call_count, 2); self.assertEqual(registration.call_count, 2)
                self.assertEqual(states[:2], ["activation-started", "email-verification-required"])
                if failure is None:
                    self.assertEqual(states.count("registration-complete"), 1)
                    self.assertLess(events.index("PKCE"), events.index("identity"))
                    self.assertLess(events.index("identity"), events.index("registration-complete"))
                else:
                    self.assertNotIn("registration-complete", states)
                    self.assertEqual(states[-1], "email-verification-required")

    def test_already_registered_member_resume_never_redeems_invitation_or_changes_password(self):
        plan = prepare.new_plan(self.context); member = plan["members"][0]
        actor = {"physical": False, "id": "owned"}
        account = prepare.identity_digest("weave-account", plan["issuerUrl"], "account-A")
        organization = prepare.identity_digest("weave-organization", plan["issuerUrl"], "organization-private-ref")
        def ui(_actor, fixture, method="testOrdinaryProductSmoke"):
            if method == "testOrdinaryProductSmoke":
                self.assertEqual(fixture["operation"], "identity")
                self.assertTrue(fixture["folder"].startswith("weave-smoke-"))
                self.assertTrue(fixture["privateFolder"].startswith("weave-smoke-private-"))
                self.assertTrue(fixture["filename"].startswith("weave-dual-smoke-"))
                self.assertEqual(len(fixture["nonce"]), 32)
            return {"resources": {"account": account, "organization": organization}}
        runner = SimpleNamespace(ui=ui)
        with patch.object(prepare, "await_mail_link") as mail, patch.object(prepare, "registration") as registration, \
                patch.object(smoke, "restart"), patch.object(smoke, "verify_discovered_routing"), patch.object(smoke, "verify_installed_build"):
            observed = prepare.member_proof(runner, actor, self.context, plan, member, "existing-test-password", {}, "registration-complete")
        self.assertEqual(observed["accountDigest"], account)
        mail.assert_not_called(); registration.assert_not_called()

    def interrupted_member(self, auth_fails=False, verification_missing=False, mailbox_failure=False):
        plan = prepare.new_plan(self.context); member = plan["members"][0]
        events = []
        actor = {"physical": False, "id": "owned"}
        account = prepare.identity_digest("weave-account", plan["issuerUrl"], "account-A")
        organization = prepare.identity_digest("weave-organization", plan["issuerUrl"], "organization-private-ref")
        attempts = 0
        def ui(_actor, fixture, method="testOrdinaryProductSmoke"):
            nonlocal attempts
            if method == "testProductionBrowser":
                events.append(["PKCE"]); attempts += 1
                if auth_fails and attempts == 1: raise smoke.SmokeError("normal-test-member-browser-authentication-required")
            return {"resources": {"account": account, "organization": organization}}
        def mail(_context, email, issuer, verification, **kwargs):
            self.assertEqual(email, member["email"]); self.assertEqual(issuer, plan["issuerUrl"])
            events.append(["mail", verification])
            if mailbox_failure: raise ContractError("private-test-member-inbox-unavailable")
            if verification and verification_missing: raise ContractError("test-member-email-verification-required")
            return "private-verification-link" if verification else "same-private-invitation-link"
        def registration(_runner, _actor, fixture):
            events.append(["stock", fixture["activationUrl"]]); return "complete"
        with patch.object(prepare, "await_mail_link", side_effect=mail), patch.object(prepare, "registration", side_effect=registration), \
                patch.object(smoke, "restart"), patch.object(smoke, "verify_discovered_routing"), patch.object(smoke, "verify_installed_build"):
            result = prepare.member_proof(SimpleNamespace(ui=ui), actor, self.context, plan, member, "unchanged-private-password",
                {"invitationHandle": "same-current-invitation"}, "activation-started", lambda state: events.append(["checkpoint", state]))
        return result, events

    def test_interruption_after_registration_before_ack_resumes_normal_pkce_without_invitation_replay(self):
        result, events = self.interrupted_member()
        self.assertEqual(result["slot"], "A")
        self.assertEqual(events, [["PKCE"], ["checkpoint", "registration-complete"]])

    def test_interruption_before_verify_ack_reads_only_exact_verification_mail_then_normal_pkce(self):
        _result, events = self.interrupted_member(auth_fails=True)
        self.assertEqual(events[:4], [["PKCE"], ["mail", True], ["checkpoint", "email-verification-required"], ["stock", "private-verification-link"]])
        self.assertNotIn(["mail", False], events)
        self.assertEqual(events.count(["PKCE"]), 2)

    def test_interruption_immediately_after_checkpoint_retries_only_unchanged_pending_invitation(self):
        _result, events = self.interrupted_member(auth_fails=True, verification_missing=True)
        self.assertEqual(events[:5], [["PKCE"], ["mail", True], ["mail", False], ["checkpoint", "activation-started"], ["stock", "same-private-invitation-link"]])
        with self.assertRaisesRegex(ContractError, "^private-test-member-inbox-unavailable$"):
            self.interrupted_member(auth_fails=True, mailbox_failure=True)

    def test_activation_started_checkpoint_is_durable_before_browser_can_consume_invitation(self):
        plan = prepare.new_plan(self.context); member = plan["members"][0]; events = []
        def browser(*args):
            self.assertEqual(events, ["activation-started"])
            raise ContractError("normal-test-member-registration-required")
        with patch.object(prepare, "await_mail_link", return_value="private-link"), patch.object(prepare, "registration", side_effect=browser):
            with self.assertRaisesRegex(ContractError, "^normal-test-member-registration-required$"):
                prepare.member_proof(None, None, self.context, plan, member, "unchanged-password", {}, record_registration=events.append)

    def operator_candidate(self):
        return {"candidateCommit": "a" * 40, "sourceTree": "b" * 40,
                "specificationCommit": json.loads((prepare.ROOT / "specs/weave-specs.lock.json").read_text())["specCorpus"]["gitCommit"]}

    def operator_backend(self):
        return {"Id": "a" * 64, "Image": "sha256:" + "b" * 64, "State": {"Running": True},
                "Config": {"Env": ["WEAVE_RELEASE_POSTURE=dogfood"], "Labels": {
                    "org.opencontainers.image.revision": "a" * 40, "com.massimotter.weave.module": "server",
                    "com.massimotter.weave.candidate-commit": "a" * 40,
                    "com.massimotter.weave.spec-commit": self.operator_candidate()["specificationCommit"],
                    "com.massimotter.weave.spec-digest": "sha256:" + hashlib.sha256((prepare.ROOT / "specs/weave-specs.lock.json").read_bytes()).hexdigest(),
                    "com.docker.compose.project": self.context.env["WEAVE_COMPOSE_PROJECT"], "com.docker.compose.service": "backend",
                    "com.massimotter.weave.namespace": self.context.env["WEAVE_RESOURCE_PREFIX"]}}}

    def operator_envelope(self, request, *, boundary=None):
        plan = request["plan"]; grants = request["operation"] == "grants"
        receipt = {"schemaVersion": "weave.dogfood-operator-preparation-receipt/v1", "authorizationKind": "dogfood-operator",
            "actorKind": "dogfood-operator", "actorRef": "operator:dogfood-prepare:" + plan["planId"],
            **{key: request[key] for key in ("operation", "nonce", "environment", "candidateCommit", "sourceTree", "specificationCommit", "apiBaseUrl")},
            "status": "passed", "stage": "grants-ready" if grants else "invitations-ready", "boundary": "none", "outcome": "VERIFIED",
            **{key: plan[key] for key in ("planId", "organizationOrigin", "issuerUrl", "spaceRef")}, "organizationId": "tenant-fixture",
            "intendedOwnerAccountRef": "acct_" + "3" * 32, "intendedOwnerMemberHandle": "mem_" + "O" * 32,
            "members": [{"slot": slot, "intentRef": "00000000-0000-0000-0000-00000000000" + str(index),
                         "invitationHandle": "inv_" + slot * 32, "memberHandle": "mem_" + slot * 32 if grants else None,
                         "accountRef": "acct_" + str(index) * 32 if grants else None,
                         "grantVersion": '"sm-1"' if grants else None, "intentState": "APPLIED" if grants else "PENDING",
                         "grantOutcome": "VERIFIED" if grants else "NOT_REQUESTED"} for index, slot in enumerate(("A", "B"), 1)],
            "supportSafe": True, "containsSecretValues": False}
        if boundary:
            receipt.update(status="blocked", stage="incomplete", outcome="PARTIAL", boundary=boundary)
            if grants:
                for member in receipt["members"]: member.update(grantVersion=None, grantOutcome="BLOCKED")
        return {"schemaVersion": "weave.dogfood-operator-preparation-envelope/v1", "receipt": receipt,
                "privateData": {"ownerDisplayName": "Actual private owner"}}

    def test_operator_request_accepts_only_deterministic_plan_and_bound_nonce(self):
        plan = prepare.new_plan(self.context); candidate = self.operator_candidate()
        request = prepare.operator_request(self.context, plan, candidate, "invitations", None, None)
        self.assertEqual(request["nonce"], request["plan"]["nonce"])
        self.assertIsNone(request["proof"]); self.assertIsNone(request["previousReceipt"])
        for key, wrong in (("email", "arbitrary@org.fixture.invalid"), ("displayName", "Another name"),
                           ("invitationIdempotencyKey", "another-key"), ("role", "owner"), ("spaceRole", "ADMIN")):
            broken = copy.deepcopy(plan); broken["members"][0][key] = wrong
            with self.subTest(key=key), self.assertRaises(ContractError): prepare.operator_request(self.context, broken, candidate, "invitations", None, None)
        with self.assertRaises(ContractError): prepare.operator_request(self.context, {**plan, "members": plan["members"][::-1]}, candidate, "invitations", None, None)
        with self.assertRaises(ContractError): prepare.operator_request(self.context, plan, candidate, "grants", None, {"nonce": "f" * 32})
        with self.assertRaises(ContractError): prepare.operator_request(self.context, plan, candidate, "invitations", self.receipt(plan), None)
        old = self.operator_envelope(request)["receipt"]; before = copy.deepcopy(old)
        current_plan = {**plan, "nonce": "f" * 32}; current_candidate = {**candidate, "candidateCommit": "c" * 40}
        current_request = prepare.operator_request(self.context, current_plan, current_candidate, "invitations", old, None)
        self.assertEqual(current_request["previousReceipt"], before)
        self.assertEqual(current_request["previousReceipt"]["nonce"], plan["nonce"])
        prepare.validate_operator_envelope(self.operator_envelope(current_request), current_request, old)

    def test_operator_envelope_rejects_mixed_authority_stale_bindings_and_unknown_fields(self):
        plan = prepare.new_plan(self.context)
        request = prepare.operator_request(self.context, plan, self.operator_candidate(), "invitations", None, None)
        value = self.operator_envelope(request); prepare.validate_operator_envelope(value, request)
        for mutation in ("authority", "actor", "admin", "nonce", "source", "tree", "spec", "plan", "api", "owner", "unknown-member", "duplicate-ref", "unknown-private", "private-control", "name-too-long", "false-safe"):
            broken = copy.deepcopy(value); receipt = broken["receipt"]
            if mutation == "authority": receipt["authorizationKind"] = "human-administrator"
            if mutation == "actor": receipt["actorRef"] = receipt["intendedOwnerAccountRef"]
            if mutation == "admin": receipt["administratorAccountRef"] = "human-admin"
            if mutation == "nonce": receipt["nonce"] = "f" * 32
            if mutation == "source": receipt["candidateCommit"] = "c" * 40
            if mutation == "tree": receipt["sourceTree"] = "d" * 40
            if mutation == "spec": receipt["specificationCommit"] = "e" * 40
            if mutation == "plan": receipt["planId"] = "e" * 32
            if mutation == "api": receipt["apiBaseUrl"] = "https://foreign.invalid/api"
            if mutation == "owner": receipt["intendedOwnerAccountRef"] = "provider-subject"
            if mutation == "unknown-member": receipt["members"][0]["password"] = "private"
            if mutation == "duplicate-ref": receipt["members"][1]["intentRef"] = receipt["members"][0]["intentRef"]
            if mutation == "unknown-private": broken["privateData"]["session"] = "private"
            if mutation == "private-control": broken["privateData"]["ownerDisplayName"] = "Owner\u0085name"
            if mutation == "name-too-long": broken["privateData"]["ownerDisplayName"] = "x" * 201
            if mutation == "false-safe": receipt["containsSecretValues"] = True
            with self.subTest(mutation=mutation), self.assertRaises(ContractError): prepare.validate_operator_envelope(broken, request)

    def test_operator_failure_keeps_typed_partial_references_without_claiming_success(self):
        request = prepare.operator_request(self.context, prepare.new_plan(self.context), self.operator_candidate(), "invitations", None, None)
        partial = self.operator_envelope(request, boundary="invitation-outcome-uncertain")
        self.assertEqual(prepare.validate_operator_envelope(partial, request)["receipt"]["status"], "blocked")
        old = partial["receipt"]
        current = copy.deepcopy(partial); current["receipt"]["members"][0]["intentRef"] = "00000000-0000-0000-0000-000000000003"
        with self.assertRaises(ContractError): prepare.validate_operator_envelope(current, request, old)
        current = copy.deepcopy(partial); current["receipt"]["boundary"] = "reset-and-retry"
        with self.assertRaises(ContractError): prepare.validate_operator_envelope(current, request)

    def test_operator_backend_requires_exact_running_candidate_labels_posture_and_namespace(self):
        runner = SimpleNamespace(run=lambda *a, **k: "b" * 40)
        backend = self.operator_backend(); candidate = self.operator_candidate()
        prepare.operator_backend(self.context, candidate, backend, runner)
        for key in backend["Config"]["Labels"]:
            if key == "org.opencontainers.image.revision": continue
            changed = copy.deepcopy(backend); changed["Config"]["Labels"][key] = "wrong"
            with self.subTest(label=key), self.assertRaises(ContractError): prepare.operator_backend(self.context, candidate, changed, runner)
        for mutation in ("posture", "duplicate-posture", "stopped", "container", "image", "bootstrap"):
            changed = copy.deepcopy(backend)
            if mutation == "posture": changed["Config"]["Env"] = ["WEAVE_RELEASE_POSTURE=stable"]
            if mutation == "duplicate-posture": changed["Config"]["Env"] *= 2
            if mutation == "stopped": changed["State"]["Running"] = False
            if mutation == "container": changed["Id"] = "backend"
            if mutation == "image": changed["Image"] = "backend:latest"
            if mutation == "bootstrap": changed["Config"]["Env"].append("WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED=true")
            with self.subTest(mutation=mutation), self.assertRaises(ContractError): prepare.operator_backend(self.context, candidate, changed, runner)
        self.artifacts.verify_runtime_container.side_effect = self.artifacts.ArtifactError("wrong-component")
        with self.assertRaises(ContractError): prepare.operator_backend(self.context, candidate, backend, runner)
        self.artifacts.verify_runtime_container.side_effect = None
        self.context.environment = "prod"
        with self.assertRaises(ContractError): prepare.operator_backend(self.context, candidate, backend, runner)

    def test_operator_invalid_context_or_foreign_container_blocks_before_build_or_command(self):
        descriptor = self.descriptor()
        for mutation in ("production", "isolated", "foreign-project", "wrong-spec", "wrong-posture"):
            context = copy.copy(self.context); backend = self.operator_backend()
            if mutation == "production": context.environment = "prod"
            if mutation == "isolated": context.isolated_namespace = "owned-e2e"
            if mutation == "foreign-project": backend["Config"]["Labels"]["com.docker.compose.project"] = "foreign"
            if mutation == "wrong-spec": backend["Config"]["Labels"]["com.massimotter.weave.spec-digest"] = "sha256:" + "0" * 64
            if mutation == "wrong-posture": backend["Config"]["Env"] = ["WEAVE_RELEASE_POSTURE=stable"]
            runner = SimpleNamespace(run=lambda command, **kwargs: "a" * 40 if command[-1] == "HEAD" else "b" * 40 if command[-1].endswith("^{tree}") else "")
            fixture = self.directory / (mutation + "/identities.json")
            result = {"trust": descriptor, "localTrustStatus": "passed", "status": "incomplete", "preparationComplete": False}
            with self.subTest(mutation=mutation), patch.object(smoke, "Runner", return_value=runner), \
                    patch("compose_runtime.verify_dogfood_identity"), patch("compose_runtime._service_container", return_value=backend), \
                    patch.object(prepare, "build_actors") as build, patch.object(prepare, "operator_driver") as command:
                result = prepare.prepare_members_onboarding(context, result, fixture, prepare.ledger_for(fixture))
            self.assertFalse(result["preparationComplete"])
            build.assert_not_called(); command.assert_not_called()
            self.assertFalse(fixture.exists()); self.assertFalse(prepare.ledger_for(fixture).exists())

    def test_operator_authority_is_absent_from_ordinary_startup_and_readiness(self):
        scripts = prepare.ROOT / "infra/weave-workspace/scripts"
        for name in ("dogfood_lifecycle.py", "compose_runtime.py", "dogfood_identity_readiness.py"):
            source = (scripts / name).read_text()
            for operation in ("dogfood-prepare-members", "operator_driver(", "dogfood_prepare.prepare_members"):
                with self.subTest(file=name, operation=operation): self.assertNotIn(operation, source)
        tasks = (prepare.ROOT / "gradle/tasks/architecture-lifecycle.gradle").read_text()
        up = tasks.split("tasks.register('dogfoodUp', Exec)", 1)[1].split("\n}\n", 1)[0]
        self.assertNotIn("dogfoodPrepare", up); self.assertNotIn("dogfood_prepare.py", up)

    def test_operator_transport_uses_fixed_stdin_private_envelope_and_exit_agreement(self):
        directory = prepare.private_directory(self.directory / "driver")
        plan = prepare.new_plan(self.context); backend = self.operator_backend(); candidate = self.operator_candidate()
        request = prepare.operator_request(self.context, plan, candidate, "invitations", None, None)
        value = self.operator_envelope(request)
        def execute(command, **kwargs):
            self.assertEqual(command, ["docker", "exec", "-i", backend["Id"], "java", "-jar", "/app/app.jar", "dogfood-prepare-members"])
            self.assertNotIn("https", " ".join(command)); self.assertNotIn("-e", command)
            self.assertEqual(json.loads(kwargs["input"]), request)
            self.assertEqual(kwargs["timeout"], 210)
            self.assertEqual(os.fstat(kwargs["stdout"].fileno()).st_mode & 0o777, 0o600)
            kwargs["stdout"].write(("private framework diagnostics\nWEAVE_DOGFOOD_OPERATOR_PREPARATION_RESULT " + json.dumps(value) + "\n").encode())
            return SimpleNamespace(returncode=0)
        with patch.object(prepare.subprocess, "run", side_effect=execute):
            self.assertEqual(prepare.operator_driver(self.context, backend, plan, candidate, directory, "invitations"), value)
        self.assertEqual(list(directory.iterdir()), [])
        blocked = self.operator_envelope(request, boundary="invitation-outcome-uncertain")
        def blocked_result(command, **kwargs):
            kwargs["stdout"].write(("WEAVE_DOGFOOD_OPERATOR_PREPARATION_RESULT " + json.dumps(blocked) + "\n").encode())
            return SimpleNamespace(returncode=2)
        with patch.object(prepare.subprocess, "run", side_effect=blocked_result):
            self.assertEqual(prepare.operator_driver(self.context, backend, plan, candidate, directory, "invitations"), blocked)
        for mutation in ("zero-blocked", "nonzero-passed", "missing", "duplicate", "duplicate-json-key", "unknown-json-key", "oversize", "timeout"):
            def invalid(command, **kwargs):
                if mutation == "timeout": raise subprocess.TimeoutExpired(command, 210)
                envelope = self.operator_envelope(request, boundary="owner-unavailable") if mutation == "zero-blocked" else copy.deepcopy(value)
                if mutation == "unknown-json-key": envelope["extra"] = "private"
                encoded = json.dumps(envelope)
                if mutation == "duplicate-json-key": encoded = encoded.replace('"authorizationKind": "dogfood-operator"', '"authorizationKind": "human", "authorizationKind": "dogfood-operator"')
                if mutation == "oversize": encoded += " " * 16384
                marker = "WEAVE_DOGFOOD_OPERATOR_PREPARATION_RESULT " + encoded + "\n"
                kwargs["stdout"].write(("missing" if mutation == "missing" else marker * (2 if mutation == "duplicate" else 1)).encode())
                return SimpleNamespace(returncode=1 if mutation == "nonzero-passed" else 0)
            with self.subTest(mutation=mutation), patch.object(prepare.subprocess, "run", side_effect=invalid), self.assertRaises(ContractError):
                prepare.operator_driver(self.context, backend, plan, candidate, directory, "invitations")
            self.assertEqual(list(directory.iterdir()), [])

    def test_operator_ledger_upgrades_only_unenacted_console_plan_without_changing_passwords(self):
        path = self.directory / "private/ledger.json"; fixture = path.with_name("identities.json")
        original = prepare.preparation_ledger(self.context, path, "fingerprint")
        upgraded = prepare.operator_ledger(self.context, path, "fingerprint", fixture)
        self.assertEqual(upgraded["schemaVersion"], "weave.dogfood-operator-preparation-ledger/v1")
        self.assertEqual(upgraded["authorizationKind"], "dogfood-operator")
        self.assertEqual(upgraded["plan"], original["plan"]); self.assertEqual(upgraded["passwords"], original["passwords"])
        self.assertEqual(prepare.operator_ledger(self.context, path, "fingerprint", fixture), upgraded)
        for mutation in ("issued", "owner", "fixture", "activated", "arbitrary-plan"):
            broken = copy.deepcopy(original)
            if mutation == "issued": broken["invitationReceipt"] = self.receipt(original["plan"])
            if mutation == "owner": broken["ownerBinding"] = {"accountDigest": "a" * 64}
            if mutation == "activated": broken["registrationState"]["A"] = "activation-started"
            if mutation == "arbitrary-plan": broken["plan"]["members"][0]["email"] = "someone@org.fixture.invalid"
            if mutation == "fixture": prepare.private_write(fixture, {"existing": "do not adopt"})
            prepare.private_write(path, broken)
            with self.subTest(mutation=mutation), self.assertRaises(ContractError): prepare.operator_ledger(self.context, path, "fingerprint", fixture)
            self.assertEqual(prepare.private_read(path), broken)
            fixture.unlink(missing_ok=True)

    def execute_operator_preparation(self, *, boundary=None, bad_proof=False, cleanup_failure=False,
                                     interrupt_after_invitations=False, stale_version=False, calls=None):
        result = {"trust": self.descriptor(), "localTrustStatus": "passed", "status": "incomplete", "preparationComplete": False}
        fixture = self.directory / "private/operator-identities.json"; ledger_path = prepare.ledger_for(fixture)
        backend = self.operator_backend(); calls = calls if calls is not None else []
        reconciled = set()
        def run(command, **kwargs):
            if command[:3] == ["xcrun", "simctl", "delete"]:
                calls.append(["delete", command[-1]])
                if cleanup_failure: raise smoke.SmokeError("native-command-failed")
            if command[-1] == "HEAD": return "a" * 40
            if command[-1].endswith("^{tree}"): return "b" * 40
            return ""
        def build(_runner, _context, _candidate, devices):
            calls.append(["build"]); devices.extend(["owned-A", "owned-B"])
            return {slot: {"slot": slot} for slot in ("A", "B")}
        def invoke(context, actual_backend, plan, candidate, directory, operation, previous=None, proof=None):
            self.assertEqual(actual_backend["Id"], backend["Id"])
            request = prepare.operator_request(context, plan, candidate, operation, previous, proof)
            if operation == "grants":
                self.assertEqual(request["nonce"], request["plan"]["nonce"])
                self.assertEqual(request["nonce"], request["proof"]["nonce"])
                self.assertNotEqual(previous["nonce"], request["nonce"])
            calls.append([operation, copy.deepcopy(previous)])
            if stale_version and operation == "grants":
                self.assertEqual(previous["stage"], "grants-ready")
                self.assertTrue(all(m["grantVersion"] == '"sm-1"' for m in previous["members"]))
            envelope = self.operator_envelope(request, boundary=("space-version-conflict" if stale_version else boundary) if operation == "grants" else None)
            if operation == "invitations" and previous:
                for old, current in zip(previous["members"], envelope["receipt"]["members"]):
                    if old["accountRef"]:
                        current.update(accountRef=old["accountRef"], memberHandle=old["memberHandle"], intentState="APPLIED")
            if operation == "invitations":
                for current in envelope["receipt"]["members"]:
                    if current["slot"] in reconciled:
                        current.update(accountRef="acct_" + ("1" if current["slot"] == "A" else "2") * 32,
                                       memberHandle="mem_" + current["slot"] * 32, intentState="APPLIED")
            return prepare.validate_operator_envelope(envelope, request, previous)
        def member(_runner, actor, context, plan, planned, password, invitation, state, record):
            calls.append(["identity", actor["slot"]])
            if interrupt_after_invitations:
                raise smoke.SmokeError("native-command-failed")
            self.assertEqual(prepare.private_read(ledger_path, 65536)["passwords"][actor["slot"]], password)
            record("registration-complete")
            reconciled.add(actor["slot"])
            return {"slot": actor["slot"], "accountDigest": "0" * 64 if bad_proof else prepare.identity_digest("weave-account", plan["issuerUrl"], "acct_" + ("1" if actor["slot"] == "A" else "2") * 32),
                    "organizationDigest": prepare.identity_digest("weave-organization", plan["issuerUrl"], "tenant-fixture")}
        def runner(directory): return SimpleNamespace(directory=directory, run=run, deadline=0)
        with patch.object(smoke, "Runner", side_effect=runner), patch("compose_runtime.verify_dogfood_identity"), \
                patch("compose_runtime._service_container", return_value=backend), patch.object(prepare, "build_actors", side_effect=build), \
                patch.object(prepare, "operator_driver", side_effect=invoke), patch.object(prepare, "member_proof", side_effect=member), \
                patch.object(prepare, "admin_driver") as admin:
            result = prepare.prepare_members_onboarding(self.context, result, fixture, ledger_path)
        admin.assert_not_called()
        return result, fixture, ledger_path, calls

    def exercise_auth_only_operator_resume(self, *, states=None, boundary="test-member-not-activated",
                                          fail_auth=None, unstarted=False, bad_proof=False,
                                          pending_final=False, durable=False, events=None):
        events = events if events is not None else []
        directory = prepare.private_directory(self.directory / "auth-only-resume")
        ledger_path = directory / "ledger.json"; fixture_path = directory / "identities.json"
        candidate = self.operator_candidate(); backend = self.operator_backend()
        ledger = prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture_path)
        plan = ledger["plan"]
        request = prepare.operator_request(self.context, plan, candidate, "invitations", None, None)
        retained = self.operator_envelope(request)
        # Build a valid earlier completed operation, including its bound native proof.
        if durable:
            proof = {"schemaVersion": "weave.dogfood-admin-preparation-proof/v1", "planId": plan["planId"],
                     "nonce": plan["nonce"], "members": [{"slot": slot,
                         "accountDigest": prepare.identity_digest("weave-account", plan["issuerUrl"], "acct_" + str(index) * 32),
                         "organizationDigest": prepare.identity_digest("weave-organization", plan["issuerUrl"], "tenant-fixture")}
                         for index, slot in enumerate(("A", "B"), 1)]}
            request = prepare.operator_request(self.context, plan, candidate, "grants", retained["receipt"], proof)
            retained = self.operator_envelope(request)
        # A durable grants envelope binds old strong ETags independently of later readbacks.
        ledger["operatorEnvelope"] = retained
        if durable: ledger["lastGrantsEnvelope"] = copy.deepcopy(retained)
        ledger["registrationState"] = states if states is not None else {"A": "email-verification-required", "B": "registration-complete"}
        if unstarted: ledger["registrationState"] = {"B": "registration-complete"}
        prepare.private_write(ledger_path, ledger)
        ledger = prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture_path)
        before_grants = copy.deepcopy(ledger.get("lastGrantsEnvelope"))
        reconciled = set(); invitation_calls = 0
        def driver(context, actual, current_plan, current_candidate, directory, operation, previous, proof):
            nonlocal invitation_calls
            events.append(operation)
            if operation == "invitations": invitation_calls += 1
            request = prepare.operator_request(context, current_plan, current_candidate, operation, previous, proof)
            envelope = self.operator_envelope(request, boundary=boundary if invitation_calls == 1 and operation == "invitations" else None)
            for row in envelope["receipt"]["members"]:
                old = next((value for value in previous["members"] if value["slot"] == row["slot"]), None) if previous else None
                if (old and old["accountRef"]) or (row["slot"] in reconciled and not pending_final):
                    row.update(accountRef="acct_" + ("1" if row["slot"] == "A" else "2") * 32,
                               memberHandle="mem_" + row["slot"] * 32, intentState="APPLIED")
            if operation == "grants" and durable:
                self.assertEqual(previous, before_grants["receipt"])
            return prepare.validate_operator_envelope(envelope, request, previous)
        def ui(actor, fixture, method="testOrdinaryProductSmoke"):
            slot = actor["slot"]
            if method == "testProductionBrowser":
                events.append("auth-" + slot)
                if fail_auth == slot: raise smoke.SmokeError("normal-test-member-browser-authentication-required")
                reconciled.add(slot)
            if fixture.get("operation") == "identity":
                events.append("identity-" + slot)
                return {"resources": {"account": "0" * 64 if bad_proof else prepare.identity_digest("weave-account", plan["issuerUrl"], "acct_" + ("1" if slot == "A" else "2") * 32),
                                      "organization": prepare.identity_digest("weave-organization", plan["issuerUrl"], "tenant-fixture")}}
            return {}
        def mail(*args, **kwargs): events.append("mail"); return "private-stock-link"
        def register(*args, **kwargs): events.append("registration"); return "complete"
        runner = SimpleNamespace(directory=directory, ui=ui)
        with patch("compose_runtime._service_container", return_value=backend), patch.object(prepare, "operator_backend"), \
                patch.object(prepare, "operator_driver", side_effect=driver), patch.object(smoke, "restart"), \
                patch.object(smoke, "verify_discovered_routing"), patch.object(smoke, "verify_installed_build"), \
                patch.object(prepare, "await_mail_link", side_effect=mail), patch.object(prepare, "registration", side_effect=register):
            result = prepare.operator_phases(self.context, backend, candidate, ledger_path, ledger, runner,
                                             {slot: {"slot": slot} for slot in ("A", "B")})
        return result, ledger, events

    def test_started_operator_members_resume_auth_only_completed_first_then_fresh_readbacks_and_grants(self):
        (envelope, proof), ledger, events = self.exercise_auth_only_operator_resume()
        self.assertEqual(events, ["invitations", "auth-B", "identity-B", "auth-A", "identity-A", "invitations", "invitations", "grants"])
        self.assertEqual(envelope["receipt"]["stage"], "grants-ready")
        self.assertEqual([row["slot"] for row in proof["members"]], ["A", "B"])
        self.assertEqual(ledger["registrationState"], {"A": "registration-complete", "B": "registration-complete"})

    def test_started_auth_failure_never_reads_mail_replays_registration_or_grants(self):
        events = []
        with self.assertRaisesRegex(smoke.SmokeError, "^normal-test-member-browser-authentication-required$"):
            self.exercise_auth_only_operator_resume(fail_auth="A", events=events)
        self.assertEqual(events, ["invitations", "auth-B", "identity-B", "auth-A"])
        ledger = prepare.private_read(self.directory / "auth-only-resume/ledger.json", 65536)
        self.assertEqual(ledger["registrationState"]["B"], "registration-complete")
        self.assertEqual(ledger["registrationState"]["A"], "email-verification-required")

    def test_unstarted_slot_registers_only_after_resumed_slot_proof_and_fresh_passed_readback(self):
        _result, _ledger, events = self.exercise_auth_only_operator_resume(unstarted=True)
        self.assertEqual(events[:6], ["invitations", "auth-B", "identity-B", "invitations", "mail", "registration"])
        self.assertEqual(events[-2:], ["invitations", "grants"])
        self.assertEqual(events.count("auth-B"), 1)

    def test_other_operator_boundaries_and_unstarted_ledgers_never_trigger_auth_resume(self):
        for boundary in ("invitation-expired", "invitation-conflict", "space-access-conflict"):
            events = []
            # Each invocation needs its own private ledger fixture.
            path = self.directory / "auth-only-resume"
            if path.exists(): shutil.rmtree(path)
            with self.subTest(boundary=boundary), self.assertRaisesRegex(ContractError, "^dogfood-operator-" + boundary + "$"):
                self.exercise_auth_only_operator_resume(boundary=boundary, events=events)
            self.assertEqual(events, ["invitations"])
        shutil.rmtree(self.directory / "auth-only-resume")
        events = []
        with self.assertRaisesRegex(ContractError, "^dogfood-operator-test-member-not-activated$"):
            self.exercise_auth_only_operator_resume(states={}, events=events)
        self.assertEqual(events, ["invitations"])

    def test_resumed_proofs_require_current_applied_identity_before_any_grant(self):
        for failure in ("pending", "identity"):
            path = self.directory / "auth-only-resume"
            if path.exists(): shutil.rmtree(path)
            events = []
            with self.subTest(failure=failure), self.assertRaises(ContractError):
                self.exercise_auth_only_operator_resume(pending_final=failure == "pending", bad_proof=failure == "identity", events=events)
            self.assertNotIn("grants", events)
            self.assertEqual(events.count("auth-A"), 1); self.assertEqual(events.count("auth-B"), 1)

    def test_auth_only_resume_preserves_durable_grant_etags(self):
        _result, ledger, events = self.exercise_auth_only_operator_resume(durable=True)
        self.assertEqual(events[-1], "grants")
        self.assertEqual([row["grantVersion"] for row in ledger["lastGrantsEnvelope"]["receipt"]["members"]], ['"sm-1"', '"sm-1"'])

    def test_operator_preparation_requires_two_native_proofs_current_grants_cleanup_and_private_owner_binding(self):
        result, fixture, ledger_path, calls = self.execute_operator_preparation()
        self.assertTrue(result["preparationComplete"])
        self.assertEqual(result["authorizationKind"], "dogfood-operator")
        self.assertEqual([row[0] for row in calls], ["build", "invitations", "identity", "identity", "invitations", "grants", "delete", "delete"])
        ledger = prepare.private_read(ledger_path, 65536)
        self.assertNotIn("administratorAccountRef", json.dumps(ledger))
        self.assertEqual(ledger["schemaVersion"], "weave.dogfood-operator-preparation-ledger/v1")
        self.assertNotIn("Actual private owner", json.dumps(result))
        self.assertNotIn("operatorEnvelope", result)
        binding = smoke.approved_identity_binding(self.context, fixture, prepare.private_read(fixture))
        self.assertNotEqual(binding["owner"]["accountDigest"], binding["members"]["A"]["accountDigest"])
        for mutation in ("human-authority", "mixed-receipt", "revoked", "password", "owner"):
            broken = copy.deepcopy(ledger)
            if mutation == "human-authority": broken["authorizationKind"] = "human-administrator"
            if mutation == "mixed-receipt": broken["invitationReceipt"] = self.receipt(ledger["plan"])
            if mutation == "revoked": broken["operatorEnvelope"]["receipt"].update(status="blocked", stage="incomplete", outcome="BLOCKED", boundary="space-access-conflict")
            if mutation == "password": broken["passwords"]["A"] = "changed-long-enough-private-password"
            if mutation == "owner": broken["operatorEnvelope"]["receipt"]["intendedOwnerAccountRef"] = "acct_" + "4" * 32
            prepare.private_write(ledger_path, broken)
            with self.subTest(mutation=mutation), self.assertRaises(ContractError): prepare.fixture_binding(self.context, fixture, prepare.private_read(fixture))
        prepare.private_write(ledger_path, ledger)

    def test_operator_partial_grant_failure_retains_intent_references_and_never_publishes_fixture(self):
        result, fixture, ledger_path, calls = self.execute_operator_preparation(boundary="space-access-conflict")
        self.assertFalse(result["preparationComplete"]); self.assertFalse(fixture.exists())
        self.assertEqual(result["boundary"], "dogfood-operator-space-access-conflict")
        receipt = prepare.private_read(ledger_path, 65536)["operatorEnvelope"]["receipt"]
        self.assertEqual(receipt["status"], "blocked")
        self.assertTrue(all(m["intentRef"] and m["invitationHandle"] for m in receipt["members"]))
        self.assertEqual(calls[-2:], [["delete", "owned-A"], ["delete", "owned-B"]])

    def test_operator_resume_preserves_passwords_plan_and_previous_receipt_nonce(self):
        _result, fixture, ledger_path, _calls = self.execute_operator_preparation()
        before = prepare.private_read(ledger_path, 65536)
        result, _, _, calls = self.execute_operator_preparation()
        after = prepare.private_read(ledger_path, 65536)
        self.assertTrue(result["preparationComplete"])
        self.assertEqual(before["passwords"], after["passwords"]); self.assertEqual(before["plan"], after["plan"])
        self.assertEqual(calls[1][1], before["operatorEnvelope"]["receipt"])
        self.assertNotEqual(before["operatorEnvelope"]["receipt"]["nonce"], after["operatorEnvelope"]["receipt"]["nonce"])
        self.assertEqual(before["operatorEnvelope"]["receipt"]["members"], after["operatorEnvelope"]["receipt"]["members"])

    def test_durable_grants_survive_invitation_refresh_interruption_reload_and_reach_resumed_grants(self):
        _result, fixture, ledger_path, _calls = self.execute_operator_preparation()
        original = prepare.private_read(ledger_path, 65536)
        durable = copy.deepcopy(original["lastGrantsEnvelope"])
        content = fixture.read_bytes()
        result, _, _, _ = self.execute_operator_preparation(interrupt_after_invitations=True)
        self.assertFalse(result["preparationComplete"])
        interrupted = prepare.private_read(ledger_path, 65536)
        self.assertEqual(interrupted["operatorEnvelope"]["receipt"]["stage"], "invitations-ready")
        self.assertTrue(all(m["grantVersion"] is None for m in interrupted["operatorEnvelope"]["receipt"]["members"]))
        self.assertEqual(interrupted["lastGrantsEnvelope"], durable)
        reloaded = prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture)
        self.assertEqual(reloaded["lastGrantsEnvelope"], durable)
        result, _, _, calls = self.execute_operator_preparation()
        self.assertTrue(result["preparationComplete"])
        grants_call, = [row for row in calls if row[0] == "grants"]
        self.assertEqual(grants_call[1], durable["receipt"])
        self.assertEqual(fixture.read_bytes(), content)
        self.assertEqual(prepare.private_read(ledger_path, 65536)["passwords"], original["passwords"])

    def test_stale_durable_grant_version_blocks_publication_after_invitation_refresh(self):
        _result, fixture, ledger_path, _calls = self.execute_operator_preparation()
        durable = copy.deepcopy(prepare.private_read(ledger_path, 65536)["lastGrantsEnvelope"])
        original = fixture.read_bytes()
        self.execute_operator_preparation(interrupt_after_invitations=True)
        with patch.object(prepare, "publish_fixture", wraps=prepare.publish_fixture) as publish:
            result, _, _, _ = self.execute_operator_preparation(stale_version=True)
        self.assertFalse(result["preparationComplete"])
        self.assertEqual(result["boundary"], "dogfood-operator-space-version-conflict")
        publish.assert_not_called()
        ledger = prepare.private_read(ledger_path, 65536)
        self.assertEqual(ledger["lastGrantsEnvelope"], durable)
        self.assertEqual(ledger["operatorEnvelope"]["receipt"]["boundary"], "space-version-conflict")
        self.assertEqual(fixture.read_bytes(), original)
        with self.assertRaises(smoke.SmokeError):
            smoke.approved_identity_binding(self.context, fixture, prepare.private_read(fixture))

    def test_durable_grants_evidence_rejects_mixed_owner_intent_authority_and_changed_versions(self):
        _result, fixture, ledger_path, _calls = self.execute_operator_preparation()
        original = prepare.private_read(ledger_path, 65536)
        for mutation in ("authority", "owner", "intent", "version", "phase", "orphan", "organization", "api", "issuer", "space"):
            broken = copy.deepcopy(original); receipt = broken["lastGrantsEnvelope"]["receipt"]
            if mutation == "authority": receipt["authorizationKind"] = "human-administrator"
            if mutation == "owner": receipt["intendedOwnerAccountRef"] = "acct_" + "4" * 32
            if mutation == "intent": receipt["members"][0]["intentRef"] = "00000000-0000-0000-0000-000000000003"
            if mutation == "version": receipt["members"][0]["grantVersion"] = '"sm-2"'
            if mutation == "phase": receipt["stage"] = "invitations-ready"
            if mutation == "orphan": del broken["operatorEnvelope"]
            if mutation == "organization": receipt["organizationOrigin"] = "https://foreign.fixture.invalid"
            if mutation == "api": receipt["apiBaseUrl"] = "https://foreign-api.fixture.invalid/api"
            if mutation == "issuer": receipt["issuerUrl"] = "https://foreign-auth.fixture.invalid/realms/weave"
            if mutation == "space": receipt["spaceRef"] = "foreign-space"
            prepare.private_write(ledger_path, broken)
            with self.subTest(mutation=mutation), self.assertRaises(ContractError):
                prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture)
        prepare.private_write(ledger_path, original)

    def test_legacy_operator_ledger_recovers_only_exact_existing_grants_and_blocks_lost_evidence(self):
        _result, fixture, ledger_path, _calls = self.execute_operator_preparation()
        original = prepare.private_read(ledger_path, 65536)
        legacy = copy.deepcopy(original); del legacy["lastGrantsEnvelope"]
        prepare.private_write(ledger_path, legacy)
        restored = prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture)
        self.assertEqual(restored["lastGrantsEnvelope"], original["operatorEnvelope"])
        self.execute_operator_preparation(interrupt_after_invitations=True)
        lost = prepare.private_read(ledger_path, 65536); del lost["lastGrantsEnvelope"]
        prepare.private_write(ledger_path, lost)
        with self.assertRaisesRegex(ContractError, "^dogfood-operator-grant-evidence-required$"):
            prepare.operator_ledger(self.context, ledger_path, self.descriptor()["caSha256Fingerprint"], fixture)
        self.assertEqual(prepare.private_read(ledger_path, 65536), lost)

    def test_operator_invalid_native_proof_or_cleanup_failure_cannot_publish_fixture(self):
        result, fixture, _, _ = self.execute_operator_preparation(bad_proof=True)
        self.assertFalse(result["preparationComplete"]); self.assertFalse(fixture.exists())
        self.assertEqual(result["boundary"], "normal-test-member-identity-proof-conflict")
        result, fixture, _, _ = self.execute_operator_preparation(cleanup_failure=True)
        self.assertFalse(result["preparationComplete"]); self.assertFalse(fixture.exists())
        self.assertEqual(result["boundary"], "owned-preparation-simulator-cleanup-required")

    def test_initial_activation_rejects_existing_user_action_token_without_checkpoint_or_browser(self):
        self.context.env["WEAVE_MAILPIT_WEB_HOST_PORT"] = "8025"
        plan = prepare.new_plan(self.context); member = plan["members"][0]
        action = plan["issuerUrl"] + "/login-actions/action-token?client_id=account&key=private-existing-user-invitation"
        self.assertEqual(prepare.action_links({"HTML": action}, plan["issuerUrl"], False), set())
        class Response:
            status = 200
            def __init__(self, value): self.value = value
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, maximum): return json.dumps(self.value).encode()
        def fetch(url, **kwargs):
            return Response({"messages": [{"ID": "private-mail", "To": [{"Address": member["email"]}]}], "total": 1}
                            if "/messages?" in url else {"To": [{"Address": member["email"]}], "HTML": action})
        clock = 0
        def now():
            nonlocal clock
            clock += 31; return clock
        with patch.object(prepare.urllib.request, "build_opener", return_value=SimpleNamespace(open=fetch)), \
                patch.object(prepare.time, "monotonic", side_effect=now), patch.object(prepare, "registration") as browser, \
                patch.object(smoke, "restart") as restart:
            states = []
            with self.assertRaisesRegex(ContractError, "^test-member-activation-mail-required$"):
                prepare.member_proof(None, None, self.context, plan, member, "private-generated-password", {"invitationHandle": "inv_" + "A" * 32}, record_registration=states.append)
        self.assertEqual(states, []); browser.assert_not_called(); restart.assert_not_called()


if __name__ == "__main__":
    unittest.main()
