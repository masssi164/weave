#!/usr/bin/env python3
"""Validate Sprint 27 local Forgejo deployed-stack E2E handoff evidence for issue #665."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-e2e" / "local-stack-e2e-handoff.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-forgejo-e2e-handoff.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_forgejo_e2e_handoff.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
RUNNER_FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-runner-readiness" / "runner-readiness.fixture.json"

FORBIDDEN_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"gh[pousr]_[a-z0-9_]{12,}",
        r"(token|secret|password|private[_-]?key)\s*[:=]\s*[^\s,}\"]+",
        r"https?://[^\s)\"]+",
        r"ssh://[^\s)\"]+",
        r"-----begin\s+(rsa|dsa|ec|openssh|private)\s+private\s+key-----",
    ]
]
REQUIRED_SIGNALS = {
    "service_exists",
    "config_path_exists",
    "registered",
    "running",
    "secret_refs_present",
    "pipeline_terminal_success",
    "stack_readiness_passed",
    "weave_e2e_passed",
}


def fail(message: str) -> None:
    print(f"local-forgejo-e2e-handoff-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return data


def assert_support_safe(value: Any, label: str, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            assert_support_safe(child, label, (*path, key))
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            assert_support_safe(child, label, (*path, str(index)))
        return
    if isinstance(value, str):
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains forbidden support-unsafe pattern {pattern.pattern!r} at {'.'.join(path)}")


def assert_fragments(path: Path, fragments: list[str]) -> None:
    content = read(path)
    for fragment in fragments:
        if fragment not in content:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def main() -> None:
    fixture = load(FIXTURE)
    if fixture.get("artifactKind") != "weave-local-forgejo-e2e-handoff-v1" or fixture.get("issue") != 665:
        fail("fixture kind/issue mismatch")
    if fixture.get("status") != "blocked_awaiting_local_runner_and_pipeline_signal":
        fail("#665 fixture must remain blocked until live local signals exist")
    if set(fixture.get("requiredConciseLocalSignals", [])) != REQUIRED_SIGNALS:
        fail("fixture required concise signals mismatch")
    upstream = fixture.get("requiredUpstreamEvidence", {})
    if upstream.get("runnerReadinessRef") != str(RUNNER_FIXTURE.relative_to(ROOT)):
        fail("#665 fixture must depend on #662 runner readiness")
    boundary = fixture.get("currentClaimBoundary", {})
    for name in ["localRunnerRequired", "pipelineDispatchRequired", "deployedStackRequired", "weaveE2eRequired"]:
        if boundary.get(name) is not True:
            fail(f"claim boundary missing {name}=true")
    if boundary.get("releaseReadyClaimAllowed") is not False:
        fail("#665 blocked handoff must not allow release-ready claim")
    assert_support_safe(fixture, "e2e handoff fixture")
    assert_fragments(DOC, ["blocked_awaiting_local_runner_and_pipeline_signal", "concise `~/server` signal", "weave_e2e_passed"])
    assert_fragments(FEATURE, ["@sprint27-local-forgejo-e2e-handoff", "blocked_awaiting_local_runner_and_pipeline_signal", "pipeline_terminal_success"])
    assert_fragments(MAPPING, ["@sprint27-local-forgejo-e2e-handoff", "LOCAL_FORGEJO_E2E_HANDOFF_PROOF", str(FIXTURE.relative_to(ROOT))])
    print("local-forgejo-e2e-handoff-check: ok issue=665 status=blocked_awaiting_local_runner_and_pipeline_signal")
    print("LOCAL_FORGEJO_E2E_HANDOFF_PROOF")


if __name__ == "__main__":
    main()
