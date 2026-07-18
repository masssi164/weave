#!/usr/bin/env python3

from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "validate_matrix_to_device_evidence.py"
COUNT_FIELDS = (
    "activeDeviceCount",
    "revokedDeviceCount",
    "queuedEventCount",
    "encryptedEventCount",
    "plaintextRoomKeyEventCount",
    "olmPreKeyEnvelopeCount",
    "olmExistingSessionEnvelopeCount",
    "targetedDeviceCount",
    "transactionCount",
    "projectedEventCount",
    "syncResponseCount",
    "sequenceHighWater",
)


def valid_payload() -> dict[str, object]:
    return {
        "contractVersion": "matrix-to-device-proof-v1",
        **{field: index for index, field in enumerate(COUNT_FIELDS)},
        "supportSafe": True,
    }


class MatrixToDeviceEvidenceValidatorTest(unittest.TestCase):
    def run_validator(
        self, payload: object, *, stale_output: bool = False
    ) -> tuple[
        subprocess.CompletedProcess[str], Path, tempfile.TemporaryDirectory[str]
    ]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        source = root / "private.json"
        output = root / "evidence.json"
        source.write_text(json.dumps(payload), encoding="utf-8")
        if stale_output:
            output.write_text('{"stale":true}\n', encoding="utf-8")
        result = subprocess.run(
            ["python3", str(SCRIPT), "--input", str(source), "--output", str(output)],
            check=False,
            capture_output=True,
            text=True,
        )
        return result, output, temporary

    def test_reconstructs_the_exact_count_only_contract(self) -> None:
        payload = valid_payload()
        result, output, temporary = self.run_validator(payload, stale_output=True)
        self.addCleanup(temporary.cleanup)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(output.read_text(encoding="utf-8")), payload)
        self.assertEqual(result.stdout.strip(), "matrix-to-device-evidence: validated")

    def test_rejects_an_extra_field_without_writing_it(self) -> None:
        payload = valid_payload()
        payload["roomId"] = "private-room-material"
        result, output, temporary = self.run_validator(payload, stale_output=True)
        self.addCleanup(temporary.cleanup)

        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())
        self.assertNotIn("private-room-material", result.stdout + result.stderr)

    def test_rejects_missing_negative_boolean_and_fractional_counts(self) -> None:
        invalid_payloads: list[dict[str, object]] = []
        for replacement in (-1, True, 1.5):
            payload = valid_payload()
            payload["queuedEventCount"] = replacement
            invalid_payloads.append(payload)
        missing = valid_payload()
        missing.pop("queuedEventCount")
        invalid_payloads.append(missing)

        for payload in invalid_payloads:
            with self.subTest(payload=payload):
                result, output, temporary = self.run_validator(payload)
                self.addCleanup(temporary.cleanup)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(output.exists())

    def test_rejects_untrusted_contract_flags(self) -> None:
        for field, value in (
            ("contractVersion", "matrix-to-device-proof-v2"),
            ("supportSafe", False),
        ):
            with self.subTest(field=field):
                payload = valid_payload()
                payload[field] = value
                result, output, temporary = self.run_validator(payload)
                self.addCleanup(temporary.cleanup)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
