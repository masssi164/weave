"""Managed member setup emits private observed bindings, never an authentication pass."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dogfood_prepare as preparation
import prepare_member_scenario as managed
import dogfood_functional_smoke as smoke
from compose_env import ContractError


class PreparedMemberScenarioTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name).resolve()
        self.context = types.SimpleNamespace(environment="dogfood", isolated_namespace=None,
            generated_root=self.directory / "generated", tls_root=self.directory / "tls",
            env={"WEAVE_PUBLIC_URL": "https://org.fixture.invalid", "WEAVE_API_ORIGIN": "https://api.fixture.invalid",
                 "WEAVE_API_URL": "https://api.fixture.invalid/api", "WEAVE_AUTH_URL": "https://auth.fixture.invalid",
                 "WEAVE_COMPOSE_PROJECT": "owned", "WEAVE_RESOURCE_PREFIX": "owned"})
        self.candidate = {"candidateCommit": "a" * 40, "sourceTree": "b" * 40, "specificationCommit": "c" * 40}
        self.fixture = self.directory / "private/identities.json"
        self.collaboration = self.directory / "private/collaboration.json"
        self.output = self.directory / "public/preparation.json"
        self.fingerprint = ":".join(["AA"] * 32)
        self.artifacts = types.ModuleType("artifact_consumers")
        self.artifacts.ArtifactError = type("ArtifactError", (ValueError,), {})
        self.artifacts.verify_runtime_container = Mock(return_value={"sourceCommit": "d" * 40})
        self.artifacts.candidate_composition = Mock(return_value={"compositionDigest": "sha256:" + "e" * 64})
        self.enterContext(patch.dict(sys.modules, {"artifact_consumers": self.artifacts}))

    def envelope(self, plan, *, status="passed", boundary="none"):
        members = []
        for slot, digit in (("A", "1"), ("B", "2")):
            subject = "subject-" + slot
            account = "acct_" + hashlib.sha256(("issuer+subject:" + plan["issuerUrl"] + "#" + subject).encode()).hexdigest()[:32]
            members.append({"slot": slot, "intentRef": digit * 8 + "-" + digit * 4 + "-" + digit * 4 + "-" + digit * 4 + "-" + digit * 12,
                            "invitationHandle": None, "memberHandle": "mem_" + digit * 32, "accountRef": account,
                            "grantVersion": '"sm-3"' if status == "passed" else None,
                            "intentState": "APPLIED", "grantOutcome": "VERIFIED" if status == "passed" else "NOT_REQUESTED"})
        receipt = {"schemaVersion": "weave.dogfood-operator-preparation-receipt/v1", "authorizationKind": "dogfood-operator",
            "actorKind": "dogfood-operator", "actorRef": "operator:dogfood-prepare:" + plan["planId"],
            "operation": "provision-members", "status": status, "stage": "provision-members-ready" if status == "passed" else "incomplete",
            "boundary": boundary, "outcome": "VERIFIED" if status == "passed" else "PARTIAL", "nonce": plan["nonce"],
            "planId": plan["planId"], "environment": "dogfood", **self.candidate,
            "organizationOrigin": plan["organizationOrigin"], "apiBaseUrl": self.context.env["WEAVE_API_URL"], "issuerUrl": plan["issuerUrl"],
            "organizationId": "tenant-default", "spaceRef": "workspace-default", "intendedOwnerAccountRef": "acct_" + "0" * 32,
            "intendedOwnerMemberHandle": "mem_" + "o" * 32, "members": members, "supportSafe": True, "containsSecretValues": False}
        private = {"ownerDisplayName": "Actual retained owner"}
        if status == "passed": private["members"] = [{"slot": slot, "identityRef": "subject-" + slot} for slot in ("A", "B")]
        return {"schemaVersion": "weave.dogfood-operator-preparation-envelope/v1", "receipt": receipt, "privateData": private}

    def run_preparation(self, *, status="passed", boundary="none", isolated=False):
        if isolated:
            self.context.environment = "e2e"
            self.context.isolated_namespace = "weave-e2e-owned"
            self.context.env["WEAVE_E2E_RUN_ID"] = "member-fixture-run"
        self.seen_requests = []
        def invoke(context, backend, plan, candidate, directory, operation, previous=None, initial_credentials=None):
            self.seen_requests.append(preparation.operator_request(context, plan, candidate, operation, previous, None, initial_credentials))
            return self.envelope(plan, status=status, boundary=boundary)
        backend = {"Id": "1" * 64, "Image": "sha256:" + "2" * 64}
        with patch.object(managed, "candidate_identity", return_value=self.candidate), \
                patch.object(managed, "ca_fingerprint", return_value=self.fingerprint), \
                patch.object(managed, "operator_backend") as verify, patch.object(managed, "operator_driver", side_effect=invoke), \
                patch("compose_runtime._service_container", return_value=backend), \
                patch("compose_runtime.verify_dogfood_identity") as persistent, \
                patch("compose_runtime.require_existing_owned_resources") as owned:
            result = managed.prepare_context(self.context, self.fixture, collaboration_output=self.collaboration,
                                             run_id="member-fixture-run", output=self.output)
        if isolated: owned.assert_called_once_with(self.context); persistent.assert_not_called()
        else: persistent.assert_called_once_with(self.context); owned.assert_not_called()
        return result

    def test_explicit_setup_emits_compatible_private_fixture_and_one_shared_scenario(self):
        result = self.run_preparation()
        self.assertTrue(result["preparationComplete"])
        self.assertFalse(result["authenticationEvidence"])
        self.assertFalse(result["humanAcceptance"])
        self.assertEqual(result["compositionDigest"], "sha256:" + "e" * 64)
        approved = smoke.identities(self.fixture, smoke.canonical_origin(self.context.env["WEAVE_PUBLIC_URL"]), False)
        with patch.object(preparation, "public_setup", return_value=(b"", {"caSha256Fingerprint": self.fingerprint})):
            binding = smoke.approved_identity_binding(self.context, self.fixture, approved)
        self.assertEqual(set(binding["members"]), {"A", "B"})
        shared = preparation.private_read(self.collaboration)
        self.assertEqual(set(shared), {"schemaVersion", "runId", "organizationId", "publicOrigin", "issuer", "memberScenario", "actors", "memberBindings"})
        self.assertEqual(shared["organizationId"], "tenant-default")
        self.assertEqual([actor["identityRef"] for actor in shared["actors"]], ["subject-A", "subject-B"])
        self.assertEqual([actor["organizationRole"] for actor in shared["memberBindings"]], ["member", "member"])
        self.assertEqual(result["scenarioDigest"], "sha256:" + shared["memberScenario"]["scenarioSha256"])
        self.assertEqual(self.fixture.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.fixture.parent.stat().st_mode & 0o777, 0o700)
        public = self.output.read_text()
        for member in approved["members"]:
            self.assertNotIn(member["password"], public)
            self.assertNotIn(member["email"], public)
        self.assertNotIn("subject-A", public)

    def test_repeat_preparation_keeps_exact_account_plan_credentials_and_grant_fence(self):
        self.run_preparation()
        first = preparation.private_read(self.fixture)
        before = preparation.private_read(preparation.ledger_for(self.fixture))
        result = self.run_preparation()
        self.assertTrue(result["preparationComplete"])
        after = preparation.private_read(preparation.ledger_for(self.fixture))
        self.assertEqual(before["plan"], after["plan"])
        self.assertEqual(before["passwords"], after["passwords"])
        self.assertEqual(first, preparation.private_read(self.fixture))
        self.assertEqual(self.seen_requests[0]["previousReceipt"]["members"][0]["grantVersion"], '"sm-3"')

    def test_partial_setup_retains_ledger_and_never_emits_usable_fixture_or_authentication_pass(self):
        result = self.run_preparation(status="incomplete", boundary="space-access-conflict")
        self.assertFalse(result["preparationComplete"])
        self.assertEqual(result["boundary"], "space-access-conflict")
        self.assertFalse(self.fixture.exists())
        self.assertFalse(self.collaboration.exists())
        ledger = preparation.private_read(preparation.ledger_for(self.fixture))
        self.assertEqual(ledger["operatorEnvelope"]["receipt"]["members"][0]["intentState"], "APPLIED")

    def test_owned_isolated_setup_uses_the_same_private_product_recipe(self):
        result = self.run_preparation(isolated=True)
        self.assertTrue(result["preparationComplete"])
        self.assertEqual(result["environment"], "e2e")
        self.assertEqual(preparation.private_read(self.collaboration)["runId"], "member-fixture-run")

    def test_mismatched_subject_account_binding_is_rejected_before_fixture_publication(self):
        plan = preparation.new_plan(self.context)
        envelope = self.envelope(plan)
        envelope["privateData"]["members"][0]["identityRef"] = "another-subject"
        with self.assertRaisesRegex(ContractError, "identity-binding-conflict"):
            managed.collaboration_input(envelope, plan, {"A": "A" * 32, "B": "B" * 32}, "member-fixture-run")

    def test_initial_credentials_require_explicit_v2_operation_and_distinct_plan_slots(self):
        plan = preparation.new_plan(self.context)
        request = preparation.operator_request(self.context, plan, self.candidate, "provision-members", None, None,
                                               {"A": "A" * 32, "B": "B" * 32})
        self.assertEqual(request["schemaVersion"], "weave.dogfood-operator-preparation-request/v2")
        for credentials in ({"A": "A" * 32}, {"A": "A" * 32, "B": "A" * 32}, {"A": "short", "B": "B" * 32}):
            with self.assertRaises(ContractError):
                preparation.operator_request(self.context, plan, self.candidate, "provision-members", None, None, credentials)
        with self.assertRaises(ContractError):
            preparation.operator_request(self.context, plan, self.candidate, "invitations", None, None,
                                         {"A": "A" * 32, "B": "B" * 32})

    def test_normal_preparation_enters_managed_setup_without_browser_registration(self):
        result = {"localTrustStatus": "incomplete"}
        with patch.object(managed, "prepare_context", return_value={"preparationComplete": True}) as current, \
                patch.object(preparation, "prepare_members_onboarding") as onboarding:
            observed = preparation.prepare_members(self.context, result, self.fixture, preparation.ledger_for(self.fixture))
        current.assert_called_once_with(self.context, self.fixture)
        onboarding.assert_not_called()
        self.assertTrue(observed["preparationComplete"])


if __name__ == "__main__":
    unittest.main()
