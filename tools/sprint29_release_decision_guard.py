#!/usr/bin/env python3
"""Sprint 29 executable guards for pre-human entry and final release decision.

The guard consumes sanitized JSON evidence. It never creates human approval; it
only verifies that the evidence says humans may start, or that a release owner
has completed explicit manual validation steps.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ENTRY = ROOT / "release" / "sprint-29-human-validation" / "pre-human-acceptance-gates.json"
DEFAULT_DECISION = ROOT / "release" / "sprint-29-human-validation" / "release-decision-guard.template.json"


def fail(message: str) -> None:
    print(f"sprint29-release-decision-guard: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing evidence file: {path}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path}: {error}")
    if not isinstance(data, dict):
        fail(f"{path} must contain a JSON object")
    return data


def entry_gate(path: Path) -> None:
    data = load_json(path)
    gates = data.get("automatedGates")
    if not isinstance(gates, list) or not gates:
        fail("entry evidence has no automatedGates")
    failing: list[str] = []
    for gate in gates:
        if not isinstance(gate, dict):
            fail("automated gate entries must be objects")
        gate_id = str(gate.get("id", "<unknown>"))
        if gate.get("requiredBeforeHumanValidation") is True and gate.get("status") != "pass":
            failing.append(gate_id)
        if gate.get("status") == "pass" and not gate.get("evidence"):
            failing.append(f"{gate_id} (missing evidence pointer)")
    if data.get("allAutomatedGatesPassed") is not True:
        failing.append("allAutomatedGatesPassed flag")
    if data.get("humanValidationStartAllowed") is not True:
        failing.append("humanValidationStartAllowed flag")
    if failing:
        fail("human validation must not start; incomplete automated gate(s): " + ", ".join(failing))
    print("sprint29-release-decision-guard: pre-human entry allowed")


def require_signed(section: dict[str, Any], name: str) -> list[str]:
    errors: list[str] = []
    if section.get("humanSignedOff") is not True:
        errors.append(f"{name}.humanSignedOff")
    for key in ("signedBy", "signedAtUtc", "evidencePath"):
        if not str(section.get(key, "")).strip():
            errors.append(f"{name}.{key}")
    findings = section.get("blockerFindings")
    if not isinstance(findings, list):
        errors.append(f"{name}.blockerFindings")
    return errors


def final_decision(path: Path) -> None:
    data = load_json(path)
    missing: list[str] = []
    if data.get("releaseReady") is not True:
        missing.append("releaseReady true")
    if data.get("decision") != "release_ready":
        missing.append("decision release_ready")
    if data.get("allAutomatedGatesPassed") is not True:
        missing.append("allAutomatedGatesPassed true")
    if data.get("openReleaseBlockers") not in (0, "0"):
        missing.append("openReleaseBlockers = 0")
    ux = data.get("humanUxAccessibilityValidation")
    weaver = data.get("humanWeaverValidation")
    if not isinstance(ux, dict):
        missing.append("humanUxAccessibilityValidation")
    else:
        missing.extend(require_signed(ux, "humanUxAccessibilityValidation"))
    if not isinstance(weaver, dict):
        missing.append("humanWeaverValidation")
    else:
        missing.extend(require_signed(weaver, "humanWeaverValidation"))
    if data.get("supportSafe") is not True:
        missing.append("supportSafe true")
    if missing:
        fail("final release readiness is blocked; missing: " + ", ".join(missing))
    print("sprint29-release-decision-guard: final release readiness evidence passed")


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    entry = sub.add_parser("entry", help="require automated gates before human validation starts")
    entry.add_argument("--evidence", type=Path, default=DEFAULT_ENTRY)
    decision = sub.add_parser("final", help="require final signed human validation and no release blockers")
    decision.add_argument("--evidence", type=Path, default=DEFAULT_DECISION)
    args = parser.parse_args()
    if args.command == "entry":
        entry_gate(args.evidence)
    elif args.command == "final":
        final_decision(args.evidence)
    else:  # pragma: no cover
        fail("unknown command")


if __name__ == "__main__":
    main()
