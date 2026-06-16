#!/usr/bin/env python3
"""Behavior tests for the shared support-safe redaction classifier."""

from __future__ import annotations

import json
from pathlib import Path

from support_safe_redaction import classify_text, redact_text

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "tools" / "fixtures" / "redaction_corpus" / "support_safe_redaction_corpus.json"


def assert_blocks(sample: str, category: str) -> None:
    findings = classify_text(sample)
    assert findings, f"expected redaction finding for {sample!r}"
    assert any(finding.category == category for finding in findings), findings
    redacted = redact_text(sample)
    assert redacted != sample
    assert "sk_live" not in redacted
    assert "rt_example" not in redacted
    assert "abc123" not in redacted
    assert "user:pass" not in redacted


def assert_allows(sample: str) -> None:
    findings = classify_text(sample)
    assert findings == [], f"expected allow-safe sample, got {findings!r} for {sample!r}"
    assert redact_text(sample) == sample


def test_corpus_drives_classifier() -> None:
    corpus = json.loads(CORPUS.read_text(encoding="utf-8"))
    for case in corpus["cases"]:
        for sample in case["samples"]:
            if case["expectation"] == "allow":
                assert_allows(sample)
            else:
                assert_blocks(sample, case["category"])


def test_negative_allow_cases_avoid_false_positives() -> None:
    for sample in (
        "Chat capability is available for space:control-room",
        "audit://release/evidence/support-safe is disabled_by_policy",
        "provider:chat:selected-by-admin sha256:2222222222222222222222222222222222222222222222222222222222222222",
        "Workspace API facade is ready; no raw service endpoint is shown.",
    ):
        assert_allows(sample)


def test_typed_findings_are_support_safe() -> None:
    secret = "Authorization: Bearer super-secret-token-value"
    finding = classify_text(secret)[0]
    serialized = repr(finding)
    assert finding.category == "bearer_and_api_tokens"
    assert finding.reason == "bearer token"
    assert "super-secret" not in serialized
    assert "Bearer" not in serialized


if __name__ == "__main__":
    test_corpus_drives_classifier()
    test_negative_allow_cases_avoid_false_positives()
    test_typed_findings_are_support_safe()
