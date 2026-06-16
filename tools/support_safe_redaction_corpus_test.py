#!/usr/bin/env python3
"""Sanity-check the shared redaction corpus scaffold for issue #793."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "tools" / "fixtures" / "redaction_corpus" / "support_safe_redaction_corpus.json"
REQUIRED_CATEGORIES = {
    "bearer_and_api_tokens",
    "private_keys_and_certificate_material",
    "credential_urls_and_service_endpoints",
    "secretref_payload_like_values",
    "provider_response_bodies_and_diagnostic_blobs",
    "homeserver_service_urls_and_provider_ids_when_forbidden",
    "allowlisted_product_safe_refs_and_states",
}


def main() -> int:
    corpus = json.loads(CORPUS.read_text(encoding="utf-8"))
    assert corpus["artifactKind"] == "weave-support-safe-redaction-corpus-v1"
    assert corpus["issue"] == 793
    assert set(corpus["requiredCategories"]) == REQUIRED_CATEGORIES
    cases = corpus["cases"]
    assert len(cases) == len(REQUIRED_CATEGORIES)
    categories = {case["category"] for case in cases}
    assert categories == REQUIRED_CATEGORIES
    assert any(case["expectation"] == "allow" for case in cases)
    assert all(case["samples"] for case in cases)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
