#!/usr/bin/env python3
"""Shared support-safe redaction classifier for release/support evidence.

The classifier is intentionally conservative: it returns typed findings with
support-safe labels, and callers choose whether to block, redact, or allow an
artifact. It never returns matched secret values.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Iterable


class RedactionAction(str, Enum):
    ALLOW = "allow"
    REDACT = "redact"


@dataclass(frozen=True)
class RedactionFinding:
    category: str
    action: RedactionAction
    reason: str
    start: int
    end: int


@dataclass(frozen=True)
class RedactionRule:
    category: str
    reason: str
    pattern: re.Pattern[str]


SAFE_REF_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"\baudit://[A-Za-z0-9._~:/-]+\b"),
    re.compile(r"\bspace:[A-Za-z0-9._~-]+\b"),
    re.compile(r"\bdecision:[A-Za-z0-9._~-]+\b"),
    re.compile(r"\bboard-task:[A-Za-z0-9._~-]+\b"),
    re.compile(r"\bprovider:[a-z0-9._-]+:selected-by-admin\b"),
    re.compile(r"\bsha256:[a-fA-F0-9]{64}\b"),
)

SAFE_STATE_PATTERN = re.compile(
    r"\b(available|disabled_by_policy|not_configured|degraded|unavailable|coming_later|ready|policy-blocked|admin-action-required|disabled)\b"
)

REDACTION_RULES: tuple[RedactionRule, ...] = (
    RedactionRule(
        "bearer_and_api_tokens",
        "bearer token",
        re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}\b", re.IGNORECASE),
    ),
    RedactionRule(
        "bearer_and_api_tokens",
        "token or api key assignment",
        re.compile(r"\b(access_token|refresh_token|api[_-]?key|client_secret|password)\s*[=:]\s*[^\s)\]}]+", re.IGNORECASE),
    ),
    RedactionRule(
        "private_keys_and_certificate_material",
        "private key material",
        re.compile(r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----"),
    ),
    RedactionRule(
        "credential_urls_and_service_endpoints",
        "credential-bearing url",
        re.compile(r"https?://[^\s/@]+:[^\s/@]+@[^\s)]+", re.IGNORECASE),
    ),
    RedactionRule(
        "credential_urls_and_service_endpoints",
        "raw service endpoint",
        re.compile(r"https?://[^\s)]+/(?:_matrix|api/v\d|realms|remote\.php|dav)(?:/[^\s)]*)?", re.IGNORECASE),
    ),
    RedactionRule(
        "secretref_payload_like_values",
        "secret reference payload",
        re.compile(r"\bsecretref://[^\s)]+\b", re.IGNORECASE),
    ),
    RedactionRule(
        "secretref_payload_like_values",
        "secret-shaped field name",
        re.compile(r"\b(credentialBearingDownloadUrl|secretValue)\b", re.IGNORECASE),
    ),
    RedactionRule(
        "provider_response_bodies_and_diagnostic_blobs",
        "raw provider diagnostic payload",
        re.compile(r"\b(rawProviderPayload|providerResponse|downstreamBody|openclaw\.json)\b", re.IGNORECASE),
    ),
    RedactionRule(
        "provider_response_bodies_and_diagnostic_blobs",
        "private member memory reference",
        re.compile(r"\bmemory://member/[^\s)]+\b", re.IGNORECASE),
    ),
    RedactionRule(
        "homeserver_service_urls_and_provider_ids_when_forbidden",
        "raw provider media or service reference",
        re.compile(r"\b(?:mxc://[^\s)]+|https?://(?:matrix|zulip|nextcloud|keycloak)\.[^\s)]+|[A-Za-z0-9.-]*provider\.[A-Za-z0-9.-]+)\b", re.IGNORECASE),
    ),
)


def _inside_safe_span(start: int, end: int, safe_spans: Iterable[tuple[int, int]]) -> bool:
    return any(start >= safe_start and end <= safe_end for safe_start, safe_end in safe_spans)


def classify_text(text: str) -> list[RedactionFinding]:
    """Return typed redaction findings without exposing matched values."""
    safe_spans = [match.span() for pattern in SAFE_REF_PATTERNS for match in pattern.finditer(text)]
    safe_spans.extend(match.span() for match in SAFE_STATE_PATTERN.finditer(text))
    findings: list[RedactionFinding] = []
    for rule in REDACTION_RULES:
        for match in rule.pattern.finditer(text):
            if _inside_safe_span(match.start(), match.end(), safe_spans):
                continue
            findings.append(
                RedactionFinding(
                    category=rule.category,
                    action=RedactionAction.REDACT,
                    reason=rule.reason,
                    start=match.start(),
                    end=match.end(),
                )
            )
    findings.sort(key=lambda finding: (finding.start, finding.end, finding.category))
    return findings


def redact_text(text: str, marker: str = "[REDACTED]") -> str:
    """Replace sensitive spans with a marker while preserving safe refs/states."""
    findings = classify_text(text)
    if not findings:
        return text
    chunks: list[str] = []
    cursor = 0
    for finding in findings:
        if finding.start < cursor:
            continue
        chunks.append(text[cursor:finding.start])
        chunks.append(marker)
        cursor = finding.end
    chunks.append(text[cursor:])
    return "".join(chunks)


def classify_file(path: Path) -> list[RedactionFinding]:
    return classify_text(path.read_text(encoding="utf-8", errors="replace"))
