#!/usr/bin/env python3
"""Validate and reconstruct support-safe Matrix to-device live evidence."""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Any


CONTRACT_VERSION = "matrix-to-device-proof-v1"
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
EXPECTED_FIELDS = frozenset(("contractVersion", *COUNT_FIELDS, "supportSafe"))


class EvidenceValidationError(ValueError):
    """The private response is not the bounded support-safe contract."""


def validated_evidence(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict) or set(payload) != EXPECTED_FIELDS:
        raise EvidenceValidationError("unexpected evidence fields")
    if payload.get("contractVersion") != CONTRACT_VERSION:
        raise EvidenceValidationError("unexpected contract version")
    if payload.get("supportSafe") is not True:
        raise EvidenceValidationError("support-safe assertion is missing")

    for field in COUNT_FIELDS:
        value = payload.get(field)
        if type(value) is not int or value < 0:  # bool must not pass as an int
            raise EvidenceValidationError(f"invalid count field: {field}")

    return {
        "contractVersion": CONTRACT_VERSION,
        **{field: payload[field] for field in COUNT_FIELDS},
        "supportSafe": True,
    }


def load_validated(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise EvidenceValidationError("evidence is not readable JSON") from error
    return validated_evidence(payload)


def write_atomic(path: Path, evidence: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary_path = Path(handle.name)
            json.dump(evidence, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.input.resolve() == args.output.resolve():
        print("matrix-to-device-evidence: invalid path contract", file=sys.stderr)
        return 1
    # A failed refresh must never leave evidence from an earlier attempt.
    args.output.unlink(missing_ok=True)
    try:
        evidence = load_validated(args.input)
        write_atomic(args.output, evidence)
    except EvidenceValidationError:
        # Never echo the private response or provider-derived values.
        print(
            "matrix-to-device-evidence: invalid support-safe contract",
            file=sys.stderr,
        )
        return 1
    print("matrix-to-device-evidence: validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
