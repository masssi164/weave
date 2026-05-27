#!/usr/bin/env python3
"""Validate Weave's enterprise release gate contract stays explicit and support-safe."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "release" / "enterprise-release-gates.json"
DOC = ROOT / "docs" / "enterprise-release-foundation.md"
FORBIDDEN_FRAGMENTS = (
    "authorization:",
    "bearer ",
    "access_token",
    "refresh_token",
    "client_secret=",
    "password=",
    "BEGIN PRIVATE KEY",
)
REQUIRED_LANES = {
    "pr-safe-ci",
    "release-candidate-live-evidence",
    "release-promotion",
}
REQUIRED_GATES = {
    "gradle-ci",
    "release-notes-label-check",
    "credentialed-live-stack-e2e",
    "release-draft-review",
    "release-owner-signoff",
}
REQUIRED_LIVE_ARTIFACTS = {
    "weave-live-stack-acceptance-evidence/acceptance-summary.md",
    "weave-live-stack-acceptance-evidence/scenario-mapping-results.json",
    "weave-live-stack-acceptance-evidence/evidence-markers.json",
    "weave-live-stack-acceptance-evidence/release-evidence-manifest.json",
}
REQUIRED_MARKERS = {
    "AUTH_RESULT",
    "PROFILE_RESULT",
    "CHAT_RESULT",
    "MATRIX_RESULT",
    "E2EE_RESULT",
    "FILES_RESULT",
    "PROVIDER_STACK_RESULT",
    "CALENDAR_RESULT",
    "BOARDS_RESULT",
}


def fail(message: str) -> None:
    print(f"release-gate-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing contract: {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(data, dict):
        fail("release gate contract root must be an object")
    return data


def all_gates(contract: dict[str, Any]) -> list[dict[str, Any]]:
    lanes = contract.get("lanes")
    if not isinstance(lanes, list):
        fail("contract must contain a lanes list")
    gates: list[dict[str, Any]] = []
    for lane in lanes:
        if not isinstance(lane, dict):
            fail("each lane must be an object")
        lane_gates = lane.get("gates")
        if not isinstance(lane_gates, list) or not lane_gates:
            fail(f"lane {lane.get('id', '<missing>')} must contain gates")
        for gate in lane_gates:
            if not isinstance(gate, dict):
                fail(f"lane {lane.get('id', '<missing>')} contains a non-object gate")
            gates.append(gate)
    return gates


def check_contract(contract: dict[str, Any]) -> None:
    if contract.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    promotion = contract.get("promotionModel")
    if not isinstance(promotion, dict):
        fail("promotionModel must be present")
    rule = str(promotion.get("promotionRule", ""))
    if "green-credentialed-live-stack-e2e" not in rule or "waiver" not in rule:
        fail("promotion rule must require green credentialed Live Stack E2E or waiver")

    lanes = contract["lanes"]
    lane_ids = {lane.get("id") for lane in lanes if isinstance(lane, dict)}
    missing_lanes = REQUIRED_LANES - lane_ids
    if missing_lanes:
        fail(f"missing release lanes: {', '.join(sorted(missing_lanes))}")

    gates = all_gates(contract)
    gate_ids = {gate.get("id") for gate in gates}
    missing_gates = REQUIRED_GATES - gate_ids
    if missing_gates:
        fail(f"missing release gates: {', '.join(sorted(missing_gates))}")

    for gate in gates:
        if not gate.get("required", False):
            fail(f"gate {gate.get('id', '<missing>')} must declare required=true")
        evidence = gate.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            fail(f"gate {gate.get('id', '<missing>')} must name evidence artifacts")

    live_gate = next(gate for gate in gates if gate.get("id") == "credentialed-live-stack-e2e")
    live_artifacts = set(live_gate.get("evidence", []))
    if REQUIRED_LIVE_ARTIFACTS - live_artifacts:
        fail("credentialed-live-stack-e2e is missing required support-safe artifacts")
    live_markers = set(live_gate.get("mustObserveMarkers", []))
    if REQUIRED_MARKERS - live_markers:
        fail("credentialed-live-stack-e2e is missing required runtime markers")


def check_support_safe_text() -> None:
    for path in (CONTRACT, DOC):
        text = path.read_text(encoding="utf-8")
        lowered = text.lower()
        for fragment in FORBIDDEN_FRAGMENTS:
            if fragment.lower() in lowered:
                fail(f"support-safety forbidden fragment in {path.relative_to(ROOT)}: {fragment}")


def check_docs(contract: dict[str, Any]) -> None:
    try:
        doc = DOC.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing doc: {DOC.relative_to(ROOT)}")
    for lane in REQUIRED_LANES:
        if lane not in doc:
            fail(f"enterprise release foundation doc does not mention lane {lane}")
    for gate in REQUIRED_GATES:
        if gate not in doc:
            fail(f"enterprise release foundation doc does not mention gate {gate}")
    for marker in REQUIRED_MARKERS:
        if marker not in doc:
            fail(f"enterprise release foundation doc does not mention marker {marker}")
    contract_rel = str(CONTRACT.relative_to(ROOT))
    if contract_rel not in doc:
        fail(f"enterprise release foundation doc must link {contract_rel}")


def main() -> None:
    contract = read_json(CONTRACT)
    check_contract(contract)
    check_docs(contract)
    check_support_safe_text()
    print("release-gate-check: ok")


if __name__ == "__main__":
    main()
