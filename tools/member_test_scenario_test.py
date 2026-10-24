"""Contract regressions for the recipe used by JVM, native and ordinary clients."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from tools.member_test_scenario import (
    ScenarioError, load_scenario, resolve_scenario, validate_member_bindings, validate_resolved_scenario,
)


class MemberScenarioTest(unittest.TestCase):
    def test_one_recipe_resolves_every_consumer_field_deterministically(self):
        first = resolve_scenario("e2e-candidate-1")
        self.assertEqual(first, resolve_scenario("e2e-candidate-1"))
        self.assertEqual(first, validate_resolved_scenario(first, "e2e-candidate-1"))
        self.assertEqual(["chat", "files", "calendar"], first["requiredDomains"])
        self.assertEqual(["member", "member"], [a["organizationRole"] for a in first["actors"]])
        self.assertEqual("weave-dual-initial-e2e-candidate-1.txt", first["content"]["initialFileName"])
        self.assertEqual("Weave actual UI initial e2e-candidate-1\n", first["content"]["initialContent"])
        second = resolve_scenario("e2e-candidate-2")
        self.assertEqual(first["scenarioSha256"], second["scenarioSha256"])
        self.assertNotEqual(first["content"], second["content"])

    def test_rejects_recipe_drift_instead_of_filling_defaults(self):
        for key in ("scenarioSha256", "sharedSpaceRef", "content", "actors"):
            value = resolve_scenario("e2e-candidate-1")
            value.pop(key)
            with self.subTest(key=key), self.assertRaises(ScenarioError):
                validate_resolved_scenario(value, "e2e-candidate-1")
        value = resolve_scenario("e2e-candidate-1")
        value["content"]["initialContent"] = "another driver's private recipe"
        with self.assertRaises(ScenarioError):
            validate_resolved_scenario(value, "e2e-candidate-1")

    def test_rejects_unsafe_execution_and_template_inputs(self):
        for run_id in ("", "../owner-data", "line\nbreak", "x" * 161):
            with self.subTest(run_id=run_id), self.assertRaises(ScenarioError):
                resolve_scenario(run_id)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scenario.json"
            value = load_scenario()
            for template in ("{runId.__class__}", "{runId", "{runId!r}", "../{runId}", "folder\\{runId}"):
                value["content"]["initialFileName"] = template
                path.write_text(json.dumps(value))
                with self.subTest(template=template), self.assertRaises(ScenarioError):
                    resolve_scenario("safe-run", path=path)

    def test_two_current_member_bindings_cannot_be_an_owner_or_the_same_account(self):
        bindings = [{"slot": slot, "organizationId": "organization", "identityRef": "subject-" + slot,
                     "accountRef": "account-" + slot, "organizationRole": "member", "spaceRef": "workspace-default",
                     "spacePermissions": ["VIEW", "EDIT"], "grantVersion": '"version-' + slot + '"'}
                    for slot in ("A", "B")]
        self.assertEqual(bindings, validate_member_bindings(bindings, "organization", "workspace-default"))
        mutations = [
            ("organizationRole", "owner"), ("spacePermissions", ["VIEW", "EDIT", "ADMIN"]),
            ("spacePermissions", ["VIEW"]), ("organizationId", "another-organization"),
            ("spaceRef", "another-space"), ("grantVersion", 'W/"weak"'),
            ("accountRef", "account-B"), ("identityRef", "subject-B"),
        ]
        for key, replacement in mutations:
            changed = copy.deepcopy(bindings)
            changed[0][key] = replacement
            with self.subTest(key=key, replacement=replacement), self.assertRaises(ScenarioError):
                validate_member_bindings(changed, "organization", "workspace-default")


if __name__ == "__main__":
    unittest.main()
