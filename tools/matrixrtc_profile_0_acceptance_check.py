#!/usr/bin/env python3
"""Deterministic offline guard for the strict MatrixRTC Profile 0 cutover."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = (
    "MATRIXRTC_PROFILE_0_STRICT_CUTOVER",
    "MATRIX_NATIVE_OAUTH_CONTRACT",
    "MATRIXRTC_RTC_AUTHORIZER_CONTRACT",
    "MATRIXRTC_MEDIA_E2EE_GUARD",
    "MATRIXRTC_NATIVE_OS_BOUNDARY",
    "MATRIXRTC_CONSENT_BOUNDARY",
)


def fail(message: str) -> None:
    print(f"matrixrtc-profile-0-acceptance-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: str) -> str:
    candidate = ROOT / path
    if not candidate.is_file():
        fail(f"missing required file {path}")
    return candidate.read_text(encoding="utf-8")


def require(path: str, *fragments: str) -> str:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path} is missing required fragment: {fragment}")
    return text


def require_absent(path: str, *fragments: str) -> None:
    text = read(path)
    for fragment in fragments:
        if fragment in text:
            fail(f"{path} still contains forbidden fragment: {fragment}")


def require_no_live_calls_sources() -> None:
    source_root = ROOT / "server/src/main/java"
    sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(source_root.rglob("*.java"))
    )
    for fragment in (
        '@GetMapping("/api/calls',
        '@PostMapping("/api/calls',
        '@RequestMapping("/api/calls',
        '@GetMapping("/api/weave/calls',
        '@PostMapping("/api/weave/calls',
        "com.weave.call.",
        "class CallsController",
        "class CallsFacadeService",
    ):
        if fragment in sources:
            fail(f"server runtime still contains proprietary Calls fragment: {fragment}")


def main() -> int:
    require(
        "docs/architecture/matrixrtc-profile-0.yaml",
        "weave.matrixrtc/profile-0",
        "specification: v1.19",
        "compatibility_policy: strict-cutover",
        "read_policy: strict-profile-0-only",
        "write_policy: strict-profile-0-only",
        "reject_unknown_or_legacy_shapes: true",
        "visible_authorization_server: matrix-authentication-service",
        "upstream_identity_backbone: keycloak",
        "identity_input: matrix-openid-credential",
        "matrixrtc_media_e2ee: required-for-private-calls",
        "dtls_srtp_only_is_e2ee: false",
    )
    require_absent(
        "docs/architecture/matrixrtc-profile-0.yaml",
        "compatibility_reads:",
        "dual_read_single_write: true",
        "unstable_fallback_endpoint:",
    )
    require(
        "docs/meeting-architecture-decision.md",
        "There is no member `/api/calls`",
        "no compatibility reader",
        "Matrix Authentication Service (MAS)",
        "Keycloak remains the mandatory organization identity backbone",
        "Matrix OpenID",
        "current room membership",
        "MatrixRTC media E2EE",
        "WCAG 2.2 AA",
        "EN 301 549",
    )
    require(
        "e2e/features/calls_matrixrtc_profile_0.feature",
        "Foreign client discovers Matrix Native OAuth",
        "Legacy MatrixRTC shape fails closed",
        "RTC Authorizer separates identity from room authorization",
        "Private media requires MatrixRTC media E2EE",
        "Member Calls contract has no proprietary route or event",
    )
    require_no_live_calls_sources()
    require_absent(
        "contracts/openapi/weave-openapi.json",
        '"/api/calls',
        '"CallCreateRequest"',
        '"CallJoinResponse"',
        '"CallNativeBoundarySetupResponse"',
    )
    require_absent(
        "client/lib/generated/openapi_models.dart",
        "class CallCreateRequest",
        "class CallJoinResponse",
        "class CallNativeBoundarySetupResponse",
    )
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
