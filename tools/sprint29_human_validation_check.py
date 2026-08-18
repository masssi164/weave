#!/usr/bin/env python3
"""Validate Sprint 29 human-in-the-loop release validation artifacts.

This check is intentionally split from real human signoff. Checked-in Sprint 29
artifacts may prepare templates and guards, but they must not claim that human
validation or final release readiness has already happened.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SPRINT_DIR = ROOT / "release" / "sprint-29-human-validation"
DOCS_DIR = ROOT / "docs" / "evidence" / "sprint-29-human-validation"

REQUIRED_ISSUES = {651, 652, 653, 654}
REQUIRED_AUTOMATED_GATES = {
    "admin-bootstrap",
    "user-login",
    "space-opens",
    "chat-works",
    "provider-switch",
    "history-report",
    "rollback-receipt",
    "files",
    "calendar",
    "agent-runtime-control",
    "runtime-external-state",
    "mcp-workload-identity",
    "runtime-revoke-delete",
    "backup-restore",
    "redacted-support-bundle",
}
REQUIRED_FILES = [
    SPRINT_DIR / "pre-human-acceptance-gates.json",
    SPRINT_DIR / "release-decision-guard.template.json",
    DOCS_DIR / "human-ux-accessibility-validation.md",
    DOCS_DIR / "human-weaver-validation.md",
    DOCS_DIR / "final-release-readiness-decision.md",
]
FORBIDDEN_PATTERNS = (
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"), "GitHub token"),
    (re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{12,}\b", re.IGNORECASE), "bearer token"),
    (re.compile(r"\b(access_token|refresh_token|client_secret|password|api[_-]?key)=[^\s)]+", re.IGNORECASE), "credential value"),
    (re.compile(r"BEGIN PRIVATE KEY"), "private key"),
    (re.compile(r"rawProviderPayload", re.IGNORECASE), "raw provider payload marker"),
)


def fail(message: str) -> None:
    print(f"sprint29-human-validation-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {rel(path)}")


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {rel(path)}: {error}")
    if not isinstance(data, dict):
        fail(f"{rel(path)} must be a JSON object")
    return data


def assert_support_safe() -> None:
    for path in REQUIRED_FILES:
        text = read(path)
        for pattern, label in FORBIDDEN_PATTERNS:
            if pattern.search(text):
                fail(f"{rel(path)} contains {label}")


def assert_issue_coverage(data: dict[str, Any]) -> None:
    raw = data.get("coversIssues")
    if not isinstance(raw, list):
        fail("pre-human acceptance gates must list coversIssues")
    issues = {int(issue) for issue in raw if isinstance(issue, int) or str(issue).isdigit()}
    missing = REQUIRED_ISSUES - issues
    if missing:
        fail("missing issue coverage: " + ", ".join(f"#{issue}" for issue in sorted(missing)))


def assert_automated_gate_manifest(data: dict[str, Any]) -> None:
    if data.get("schemaVersion") != 1:
        fail("pre-human acceptance gate schemaVersion must be 1")
    if data.get("humanValidationStartAllowed") is not False:
        fail("checked-in pre-human artifact must not allow human validation start")
    if str(data.get("humanValidationStartRule", "")).strip() != "all automated gates must pass with current sanitized evidence":
        fail("humanValidationStartRule changed or missing")
    gates = data.get("automatedGates")
    if not isinstance(gates, list):
        fail("automatedGates must be a list")
    seen: set[str] = set()
    for gate in gates:
        if not isinstance(gate, dict):
            fail("automated gate entries must be objects")
        gate_id = str(gate.get("id", "")).strip()
        if not gate_id:
            fail("automated gate entries must have non-empty id")
        if gate_id in seen:
            fail(f"duplicate automated gate id: {gate_id}")
        seen.add(gate_id)
        if gate.get("requiredBeforeHumanValidation") is not True:
            fail(f"{gate_id} must be required before human validation")
        status = str(gate.get("status", ""))
        if status not in {"pending-current-evidence", "blocked", "pass"}:
            fail(f"{gate_id} has unsupported status {status!r}")
        if status == "pass" and not gate.get("evidence"):
            fail(f"{gate_id} cannot pass without evidence pointers")
        if not isinstance(gate.get("commands"), list) or not gate["commands"]:
            fail(f"{gate_id} must list executable command(s)")
    missing = REQUIRED_AUTOMATED_GATES - seen
    extra = seen - REQUIRED_AUTOMATED_GATES
    if missing:
        fail("missing automated gate(s): " + ", ".join(sorted(missing)))
    if extra:
        fail("unexpected automated gate(s): " + ", ".join(sorted(extra)))
    if data.get("allAutomatedGatesPassed") is not False:
        fail("checked-in pre-human artifact must keep allAutomatedGatesPassed false until generated evidence proves it")


def assert_decision_guard_template(data: dict[str, Any]) -> None:
    if data.get("schemaVersion") != 1:
        fail("release decision guard schemaVersion must be 1")
    if data.get("decision") != "blocked_pending_human_validation":
        fail("checked-in decision guard template must remain blocked_pending_human_validation")
    required = data.get("requiredInputs")
    if not isinstance(required, list):
        fail("release decision guard template must list requiredInputs")
    for required_input in [
        "pre-human automated acceptance report with all gates pass",
        "human UX/accessibility validation signed by Massimo or release owner",
        "human Weaver validation signed by Massimo or release owner",
        "open release-blocker issue check",
        "support-safe final readiness decision",
    ]:
        if required_input not in required:
            fail(f"release decision guard missing required input: {required_input}")
    if data.get("releaseReady") is not False:
        fail("checked-in decision guard template must not mark releaseReady true")


def assert_manual_templates() -> None:
    for path in REQUIRED_FILES[2:]:
        text = read(path)
        for phrase in [
            "Do not paste secrets, tokens, raw provider payloads, raw provider error bodies, or private user content",
            "Human signoff status: pending",
            "Release blocker handling",
        ]:
            if phrase not in text:
                fail(f"{rel(path)} missing required phrase: {phrase}")
        if re.search(r"Human signoff status:\s*(passed|approved|signed)", text, re.IGNORECASE):
            fail(f"{rel(path)} appears to fake completed human signoff")


def main() -> None:
    assert_support_safe()
    gates = load_json(SPRINT_DIR / "pre-human-acceptance-gates.json")
    assert_issue_coverage(gates)
    assert_automated_gate_manifest(gates)
    guard = load_json(SPRINT_DIR / "release-decision-guard.template.json")
    assert_decision_guard_template(guard)
    assert_manual_templates()
    print("sprint29-human-validation-check: ok")


if __name__ == "__main__":
    main()
