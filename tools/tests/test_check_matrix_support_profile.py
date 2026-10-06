import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/check_matrix_support_profile.py"
SPEC = importlib.util.spec_from_file_location("check_matrix_support_profile", SOURCE)
assert SPEC is not None and SPEC.loader is not None
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class MatrixSupportProfileCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profile = (ROOT / checker.PROFILE).read_text()

    def test_current_profile_has_valid_assertion_refs_and_explicit_unsupported_row(self):
        self.assertEqual(checker.check_profile(self.profile, ROOT), [])

    def test_promotion_without_weave_client_and_integrated_evidence_fails(self):
        promoted = self.profile.replace(" | Guarded | ", " | Supported | ", 1)
        errors = checker.check_profile(promoted, ROOT)
        self.assertTrue(any("Weave-owned client assertion" in error for error in errors), errors)
        self.assertTrue(any("integrated evidence file" in error for error in errors), errors)

    def test_missing_negative_route_row_fails(self):
        omitted = "\n".join(
            line for line in self.profile.splitlines()
            if not line.startswith("| unknown-client-route |")
        )
        self.assertIn(
            "missing endpoint row: unknown-client-route",
            checker.check_profile(omitted, ROOT),
        )

    def test_stale_protocol_assertion_fails(self):
        stale = self.profile.replace(
            "#matrixDiscoveryIsPublicAndPointsAtTheWeaveFacade",
            "#nonexistentDiscoveryAssertion",
        )
        self.assertTrue(
            any("assertion is absent" in error for error in checker.check_profile(stale, ROOT))
        )


if __name__ == "__main__":
    unittest.main()
