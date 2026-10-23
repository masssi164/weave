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
from unittest.mock import patch
import unittest

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
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name).resolve()
        tls = self.directory / "tls"; tls.mkdir()
        shutil.copyfile(self.certificate, tls / "ca.pem")
        self.context = SimpleNamespace(environment="dogfood", isolated_namespace=None,
            generated_root=self.directory / "generated", tls_root=tls, secret_root=self.directory / "secrets",
            env={"WEAVE_PUBLIC_URL": "https://org.fixture.invalid", "WEAVE_API_ORIGIN": "https://api.fixture.invalid",
                 "WEAVE_AUTH_URL": "https://auth.fixture.invalid"})

    def checks(self):
        return [{"origin": self.context.env[key], "status": "passed", "category": "trusted", "nativeCode": 0, "httpStatus": 200}
                for key in ("WEAVE_PUBLIC_URL", "WEAVE_API_ORIGIN", "WEAVE_AUTH_URL")]

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
                patch.object(prepare, "prepare_members", side_effect=lambda _c, result, *_p: result), \
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
            observed = prepare.prepare_members(self.context, result, fixture, prepare.ledger_for(fixture))
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
            observed = prepare.prepare_members(self.context, result, fixture, prepare.ledger_for(fixture))
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

    def test_registration_requires_exact_single_ack_and_always_removes_private_transport(self):
        directory = prepare.private_directory(self.directory / "registration")
        products = prepare.private_directory(directory / "products")
        (products / "driver.xctestrun").write_bytes(b"fixture")
        fixture = {"password": "PRIVATE", "activationUrl": "PRIVATE"}
        for status, result in (("status=incomplete stage=email-verification-required", "verification"),
                               ("status=passed stage=registration-complete", "complete")):
            runner = SimpleNamespace(sequence=1, directory=directory, run=lambda *a, **k: "NATIVE_IOS_REGISTRATION_RESULT " + status + " supportSafe=true\n")
            private = products / "private-member.json"
            def transport(_products, value): prepare.private_write(private, value); return private
            with patch.object(smoke, "prepare_browser_fixture", side_effect=transport):
                self.assertEqual(prepare.registration(runner, {"products": products, "destination": "owned"}, fixture), result)
            self.assertFalse(private.exists())
        runner.run = lambda *a, **k: "NATIVE_IOS_REGISTRATION_RESULT status=passed stage=registration-complete supportSafe=true\n" * 2
        with patch.object(smoke, "prepare_browser_fixture", side_effect=transport), self.assertRaises(ContractError):
            prepare.registration(runner, {"products": products, "destination": "owned"}, fixture)
        self.assertFalse(private.exists())

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


if __name__ == "__main__":
    unittest.main()
