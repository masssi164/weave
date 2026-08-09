#!/usr/bin/env python3
"""Validate the full open-standards gateway archive is tracked honestly.

The July 2026 archive describes the final northbound target. This gate is
deliberately stricter than the current MVP hard gate: it proves the required
feature inventory is checked in and records which target slices are still not
complete so PRs cannot close the umbrella by relying on partial WebDAV,
CalDAV, Matrix, or Calls evidence.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKER = "OPEN_STANDARDS_GATEWAY_ARCHIVE_FULL_TARGET_TRACKED"

REQUIRED_FEATURES = {
    "open_standards_gateway_manifest.feature": 3,
    "oidc_protocol_access.feature": 5,
    "files_webdav_full_facade.feature": 11,
    "calendar_caldav_facade.feature": 10,
    "chat_matrix_facade.feature": 13,
    "calls_webrtc_join_grants.feature": 9,
    "mcp_domain_facade_boundary.feature": 9,
    "provider_neutral_no_leakage.feature": 5,
    "flutter_protocol_boundary.feature": 5,
    "native_os_integration_readiness.feature": 4,
}

REQUIRED_EVIDENCE_BOUNDARIES = {
    "files-full-webdav": [
        "MOVE",
        "COPY",
        "LOCK",
        "UNLOCK",
        "streaming PUT",
        "quota 507",
    ],
    "calendar-caldav-parity": [
        "calendar-multiget",
        "sync-collection",
        "MKCALENDAR",
        "recurrence",
        "DST",
        "Flutter CalDAV",
    ],
    "chat-matrix-parity": [
        "OIDC-gated Weave Matrix Client-Server facade",
        "Rust/Ruma Matrix core",
        "server JNI",
        "flutter_rust_bridge",
        "southbound provider/fixture",
        "device revocation",
        "raw Chat API-first member data-plane",
    ],
    "calls-matrixrtc-profile-zero": [
        "MatrixRTC Profile 0",
        "RTC Authorizer",
        "room/slot/member/device/policy/nonce/audience/expiry",
        "MatrixRTC media E2EE",
        "replaceable southbound SFU adapter",
    ],
    "protocol-credentials": [
        "WEBDAV_FILES",
        "CALDAV_CALENDAR",
        "scoped",
        "expiring",
        "revocable",
    ],
}


def fail(message: str) -> None:
    print(f"open-standards-gateway-archive-gap-check: {message}", file=sys.stderr)
    sys.exit(1)


def read(path: str) -> str:
    file_path = ROOT / path
    if not file_path.exists():
        fail(f"missing required file {path}")
    return file_path.read_text(encoding="utf-8")


def require(path: str, *fragments: str) -> str:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path} is missing required fragment: {fragment}")
    return text


def scenario_count(feature_text: str) -> int:
    return len(re.findall(r"(?m)^\s*Scenario:", feature_text))


def require_archive_features() -> None:
    for filename, expected_count in REQUIRED_FEATURES.items():
        path = f"e2e/features/{filename}"
        text = require(path, "Feature:")
        actual_count = scenario_count(text)
        if actual_count != expected_count:
            fail(f"{path} expected {expected_count} scenarios but found {actual_count}")


def require_gap_inventory() -> None:
    docs = require(
        "docs/open-standards-gateway-archive-integration.md",
        MARKER,
        "Spring AI MCP is the only runtime MCP path",
        "not complete",
    )
    for gap_id, fragments in REQUIRED_EVIDENCE_BOUNDARIES.items():
        if gap_id not in docs:
            fail(f"gap inventory is missing {gap_id}")
        for fragment in fragments:
            if fragment not in docs:
                fail(f"gap inventory {gap_id} is missing fragment: {fragment}")


def require_current_evidence_boundaries() -> None:
    require(
        "tools/target_standard_facade_hard_gate_check.py",
        "TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF",
        "TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP",
        "TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/FilesWebDavController.java",
        'case "MOVE" -> move(request)',
        'case "COPY" -> copy(request)',
        'case "LOCK" -> lock(request)',
        'case "UNLOCK" -> unlock(request)',
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/service/FilesFacadeService.java",
        "copyWebDavPath(",
        "moveWebDavPath(",
        "lockWebDavPath(",
        "unlockWebDavPath(",
        "files-locked",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/CalDavCalendarController.java",
        'case "MKCALENDAR", "COPY", "MOVE", "LOCK", "UNLOCK" -> unsupportedMethod(method)',
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionController.java",
        '"X-Weave-Matrix-Core", "rust-ruma-jni"',
        "chatDomainFacadeService.conversations(jwt)",
        "chatDomainFacadeService.sendEvent(",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/matrix/MatrixProtocolCodec.java",
        "Map<String, Object> project(MatrixProtocolOperation operation, String inputJson)",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/matrix/MatrixProtocolCoreService.java",
        "implements MatrixProtocolCodec",
        "NativeMatrixCore.projectJson(operation.wireName(), inputJson, serverName)",
        'public static final String FLUTTER_BRIDGE_BOUNDARY = "flutter-rust-bridge"',
    )
    forbidden_calls_path = ROOT / "server/src/main/java/com/massimotter/weave/backend/controller/CallsController.java"
    if forbidden_calls_path.exists():
        fail("legacy member Calls controller must be removed")
    for path in ROOT.glob("server/src/main/java/**/*.java"):
        text = path.read_text(encoding="utf-8")
        if '"/api/calls' in text or "com.weave.call." in text:
            fail(f"legacy Calls contract remains in {path.relative_to(ROOT)}")
    require(
        "tools/spring_ai_mcp_facade_acceptance_check.py",
        "SPRING_AI_MCP_STATEFUL_TRANSPORT",
        "MCP_WORKLOAD_EDGE_BOUND_CELL_ONLY",
        "MCP_FILES_READ_SLICE_ACTIVE",
        "MCP_CALENDAR_CATALOG_GUARDED",
        "MCP_CHAT_CATALOG_GUARDED",
        "MCP_APPROVAL_EVIDENCE_FAILS_CLOSED",
        "MCP_LEGACY_RUNTIME_REMOVED",
    )


def main() -> int:
    require_archive_features()
    require_gap_inventory()
    require_current_evidence_boundaries()
    print(MARKER)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
