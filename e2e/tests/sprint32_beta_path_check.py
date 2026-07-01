#!/usr/bin/env python3
"""Validate Sprint 32 Admin + User + Weaver Beta path evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "release/provider-lab/weaver-runtime/sprint-32-beta-path-evidence.fixture.json"
DOC = ROOT / "docs/evidence/sprint-32-beta-path-evidence.md"

# Evidence markers used by e2e/scenario_mappings.json.
BETA_ADMIN_READINESS_RESULT = "admin readiness marker"
BETA_WEAVER_ENABLEMENT_RESULT = "admin Weaver enablement marker"
BETA_MEMBER_WORKSPACE_RESULT = "member workspace Weaver marker"
BETA_GOVERNED_RUNTIME_RESULT = "governed runtime/tool marker"
BETA_ADAPTER_CONTINUITY_RESULT = "adapter continuity dry-run marker"
BETA_AUDIT_EVIDENCE_RESULT = "audit evidence marker"

REQUIRED_MARKERS = {
    "BETA_ADMIN_READINESS_RESULT",
    "BETA_WEAVER_ENABLEMENT_RESULT",
    "BETA_MEMBER_WORKSPACE_RESULT",
    "BETA_GOVERNED_RUNTIME_RESULT",
    "BETA_ADAPTER_CONTINUITY_RESULT",
    "BETA_AUDIT_EVIDENCE_RESULT",
}
REQUIRED_STEPS = {
    "admin_readiness",
    "admin_weaver_enablement",
    "member_workspace_weaver_use",
    "governed_runtime_tool_path",
    "adapter_continuity_dry_run",
    "audit_evidence_inspection",
}
FORBIDDEN_SUBSTRINGS = (
    "secret=",
    "token=",
    "bearer ",
    "mxc://",
    "https://matrix.",
    "https://auth.",
    "rawProviderPayload",
    "rawWeaverPrompt",
)


def fail(message: str) -> None:
    print(f"sprint32-beta-path-check: {message}", file=sys.stderr)
    sys.exit(1)


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        fail(f"missing {path.relative_to(ROOT)}")
    try:
        data = json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {exc}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must be an object")
    return data


def main() -> int:
    data = load_json(FIXTURE)
    if data.get("schemaVersion") != "sprint32.beta-path-evidence.v1":
        fail("unexpected schemaVersion")
    if data.get("issue") != 835:
        fail("fixture must be bound to issue 835")

    support = data.get("supportSafety")
    if not isinstance(support, dict):
        fail("supportSafety must be an object")
    for key in ("containsSecrets", "containsRawProviderPayloads", "containsRawWeaverPrompts", "containsEndpointUrls"):
        if support.get(key) is not False:
            fail(f"supportSafety.{key} must be false")

    story = data.get("story")
    if not isinstance(story, list):
        fail("story must be a list")
    steps = {item.get("step") for item in story if isinstance(item, dict)}
    missing_steps = REQUIRED_STEPS - steps
    if missing_steps:
        fail("missing story steps: " + ", ".join(sorted(missing_steps)))

    markers = {item.get("evidenceMarker") for item in story if isinstance(item, dict)}
    missing_markers = REQUIRED_MARKERS - markers
    if missing_markers:
        fail("missing markers: " + ", ".join(sorted(missing_markers)))

    for item in story:
        if not isinstance(item, dict):
            fail("each story item must be an object")
        assertions = item.get("assertions")
        if not isinstance(assertions, list) or len(assertions) < 2:
            fail(f"{item.get('step')} needs at least two assertions")
        if item.get("status") != "passed_offline_contract":
            fail(f"{item.get('step')} must be passed_offline_contract")

    gate = data.get("liveStackGate")
    if not isinstance(gate, dict):
        fail("liveStackGate must be an object")
    if gate.get("status") != "blocked_environment_unavailable":
        fail("liveStackGate must state the exact environment blocker for this PR-safe run")
    if gate.get("requiredBeforeRcPromotion") is not True:
        fail("live stack evidence must remain required before RC promotion")

    text = FIXTURE.read_text().lower()
    for forbidden in FORBIDDEN_SUBSTRINGS:
        if forbidden in text:
            fail(f"support-unsafe substring found: {forbidden}")

    doc_text = DOC.read_text() if DOC.exists() else ""
    for fragment in (
        "Admin prepares and validates organization/provider/category readiness",
        "User opens the Client in workspace context and uses Weaver",
        "No secrets, raw provider payloads, raw Weaver prompts, endpoint URLs, room IDs, event IDs, usernames, or member content are recorded",
    ):
        if fragment not in doc_text:
            fail(f"missing documentation fragment: {fragment}")

    print("sprint32-beta-path-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
