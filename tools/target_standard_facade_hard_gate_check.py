#!/usr/bin/env python3
"""Classify legacy DAV proof and guard the current Matrix product boundary.

This is offline source/test evidence. It does not qualify a live user journey.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FILES_MARKER = "TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF"
CALENDAR_MARKER = "TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP"
MATRIX_MARKER = "TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP"


def fail(message: str) -> None:
    print(f"target-standard-facade-hard-gate-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: str) -> str:
    source = ROOT / path
    if not source.is_file():
        fail(f"missing required file {path}")
    return source.read_text(encoding="utf-8")


def require(path: str, *fragments: str) -> None:
    source = read(path)
    for fragment in fragments:
        if fragment not in source:
            fail(f"{path} is missing required fragment: {fragment}")


def check_historical_dav() -> None:
    feature = "e2e/features/target_standard_facade_hard_gate.feature"
    require(
        feature,
        FILES_MARKER,
        CALENDAR_MARKER,
        "historical Core Files implementation",
        "historical Core Calendar implementation",
        "does not qualify public DAV as the #1470 Files product boundary",
        "does not qualify public CalDAV as the #1470 Calendar product boundary",
        "generated User API Files operations require independent #1473 implementation and tests",
        "generated User API Calendar operations require independent #1473 implementation and tests",
    )
    # Older protocol tests still protect integrity and failure behavior.
    # They do not prove the generated User API replacement.
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesWebDavControllerTest.java",
        "propfindDepthOneReturnsChildrenAsDavResponses",
        "putMkcolAndDeleteUseWebDavFacadeWriteUseCases",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/FilesFacadeServiceTest.java",
        "webDavPutCreateFolderAndDeleteUseFacadePolicyAndPublishMutationAudit",
        "webDavPutResponseEtagChangesForSameSizeOverwriteWhenMetadataDoesNotChange",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesCalendarFacadeControllerTest.java",
        "calDavReportCalendarQueryAndFreeBusyReturnFacadeBackedCalendarData",
        "calDavReportMultigetAndSyncCollectionUseScopedFacadeCalendars",
        "calDavEventReadPutAndDeleteUseCalendarFacadeBoundaryAndStableErrors",
    )


def check_matrix_boundary() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        MATRIX_MARKER,
        "Flutter uses its native Rust/Matrix SDK against the Weave facade",
        "versioned support profile guards every capability",
    )
    require(
        "client/lib/features/chat/presentation/providers/chat_repository_provider.dart",
        "NativeMatrixChatRepository",
        "native Rust/Matrix SDK",
        "Weave User API room bindings remain separate from Matrix message transport",
    )
    require(
        "client/test/architecture/backend_facade_contract_test.dart",
        "FLUTTER_MATRIX_BOUNDARY_CONTRACT",
        "NativeMatrixChatRepository",
        "isNot(contains('BackendChatRepository('))",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java",
        "whoamiUsesRumaValidatedIdentityDerivedFromOidcSubject",
        "syncProjectsCanonicalChatThroughRustWithStableWeaveCursor",
        "sendParsesInRustAndForwardsTransactionForCanonicalIdempotency",
        "joinedRoomsAndMessagesUseCanonicalIdentifiers",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionController.java",
        '"/_matrix/client/**"',
        "MatrixProtocolCoreService",
        "ChatDomainFacadeService",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/ChatControllerTest.java",
        "chatLegacyRestDataPlaneRoutesAreRemovedInFavorOfMatrixFacade",
    )
    for artifact in ("weave-user-openapi", "weave-admin-openapi"):
        contract = json.loads(read(f"contracts/openapi/{artifact}.json"))
        if any(
            path.startswith("/api/chat/conversations/") and "/messages" in path
            for path in contract.get("paths", {})
        ):
            fail(f"{artifact} exposes a proprietary Chat message data plane")


def main() -> int:
    check_historical_dav()
    check_matrix_boundary()
    print(f"{FILES_MARKER}:historical")
    print(f"{CALENDAR_MARKER}:historical")
    print(f"{MATRIX_MARKER}:guarded")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
