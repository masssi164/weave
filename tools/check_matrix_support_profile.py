#!/usr/bin/env python3
"""Reject unsupported Matrix compatibility claims in the normative support profile."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


PROFILE = Path("docs/reference/matrix-client-server-support-profile.md")
REQUIRED_ROWS = {
    "discovery",
    "whoami",
    "sync",
    "room-state",
    "room-send",
    "redaction",
    "receipt",
    "typing",
    "account-data",
    "keys",
    "device-signing",
    "send-to-device",
    "room-key-backup",
    "device-revoke",
    "device-continuity",
    "unknown-client-route",
}
STATUSES = {"Supported", "Guarded", "Unsupported"}
REFERENCE = re.compile(r"^`([^#`]+)#([A-Za-z_][A-Za-z_0-9]*)`$")
EVIDENCE = re.compile(r"^evidence: `([^\s`]+)`$")
VERSION = re.compile(r"^Profile version: `weave\.matrix-client-server/v[1-9][0-9]*`$", re.MULTILINE)


def _assertion(reference: str, root: Path, expected_prefix: str) -> str | None:
    match = REFERENCE.fullmatch(reference)
    if match is None:
        return f"expected path#assertion reference, got {reference!r}"
    relative, name = match.groups()
    if not relative.startswith(expected_prefix) or ".." in Path(relative).parts:
        return f"assertion must be under {expected_prefix}: {relative}"
    source = root / relative
    if not source.is_file():
        return f"assertion source is absent: {relative}"
    if re.search(rf"\b(?:void|def)\s+{re.escape(name)}\s*\(", source.read_text()) is None:
        return f"assertion is absent: {relative}#{name}"
    return None


def check_profile(profile: str, root: Path) -> list[str]:
    errors: list[str] = []
    if VERSION.search(profile) is None:
        errors.append("missing versioned weave.matrix-client-server/vN profile")
    section = profile.partition("## Endpoint profile\n")[2].partition("\n## ")[0]
    rows = [line for line in section.splitlines() if line.startswith("| ")]
    header = "| ID | Surface | Status | Protocol assertion | Weave-owned client assertion | Integrated evidence or gap |"
    if len(rows) < 3 or rows[0] != header:
        return [*errors, "endpoint table/header is missing"]

    seen: set[str] = set()
    for raw in rows[2:]:
        cells = [cell.strip() for cell in raw.strip("|").split("|")]
        if len(cells) != 6:
            errors.append(f"endpoint row has {len(cells)} cells: {raw}")
            continue
        row_id, surface, status, protocol, owned_client, evidence = cells
        if row_id in seen:
            errors.append(f"duplicate endpoint row: {row_id}")
        seen.add(row_id)
        if not surface or status not in STATUSES:
            errors.append(f"{row_id}: surface or status is invalid")
        if protocol != "pending":
            problem = _assertion(protocol, root, "server/src/test/")
            if problem:
                errors.append(f"{row_id}: {problem}")
        if status in {"Supported", "Unsupported"} and protocol == "pending":
            errors.append(f"{row_id}: {status} requires a protocol assertion")
        if status == "Supported":
            if owned_client == "pending":
                errors.append(f"{row_id}: Supported requires a Weave-owned client assertion")
            else:
                problem = _assertion(owned_client, root, "weave-product-e2e/")
                if problem:
                    errors.append(f"{row_id}: {problem}")
            proof = EVIDENCE.fullmatch(evidence)
            if (
                proof is None
                or not proof.group(1).startswith("docs/evidence/matrix/")
                or ".." in Path(proof.group(1)).parts
                or not (root / proof.group(1)).is_file()
            ):
                errors.append(f"{row_id}: Supported requires a checked-in integrated evidence file")
        elif owned_client != "pending":
            errors.append(f"{row_id}: unqualified rows must not cite Weave-owned client qualification")
        if status == "Guarded" and (not evidence or evidence.startswith("evidence:")):
            errors.append(f"{row_id}: Guarded requires an explicit qualification gap")
        if status == "Unsupported" and not evidence:
            errors.append(f"{row_id}: Unsupported requires explicit failure behavior")

    for missing in sorted(REQUIRED_ROWS - seen):
        errors.append(f"missing endpoint row: {missing}")
    for extra in sorted(seen - REQUIRED_ROWS):
        errors.append(f"unknown endpoint row: {extra}")
    if not any("| Unsupported |" in row for row in rows[2:]):
        errors.append("profile needs an explicit unsupported-route row")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = args.root.resolve()
    errors = check_profile((root / PROFILE).read_text(), root)
    if errors:
        for error in errors:
            print(f"MATRIX_SUPPORT_PROFILE_ERROR {error}")
        return 1
    print("MATRIX_SUPPORT_PROFILE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
