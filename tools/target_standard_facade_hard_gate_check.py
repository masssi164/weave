#!/usr/bin/env python3
"""Validate the Matrix/WebDAV/CalDAV hard-gate acceptance evidence."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = [
    "TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF",
    "TARGET_STANDARDS_CALDAV_CALENDAR_FAIL_CLOSED",
    "TARGET_STANDARDS_MATRIX_CHAT_FAIL_CLOSED",
]


def fail(message: str) -> None:
    print(f"target-standard-facade-hard-gate-check: {message}", file=sys.stderr)
    sys.exit(1)


def read(path: str) -> str:
    file_path = ROOT / path
    if not file_path.exists():
        fail(f"missing required file {path}")
    return file_path.read_text()


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


def require_files_webdav_current_proof() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        "TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF",
        "#969",
        "#1018",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesWebDavControllerTest.java",
        "propfindDepthOneReturnsChildrenAsDavResponses",
        "getDownloadsFileThroughFacadePath",
        "putMkcolAndDeleteUseWebDavFacadeWriteUseCases",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/FilesFacadeServiceTest.java",
        "webDavPutCreateFolderAndDeleteUseFacadePolicyAndPublishMutationAudit",
        "webDavPutResponseEtagChangesForSameSizeOverwriteWhenMetadataDoesNotChange",
        "FILES_WEBDAV_WRITE_COMPLETED",
    )
    require(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "http.Request('PROPFIND'",
        "'dav', 'files'",
        "_httpClient.get(",
        "Files writes are blocked in this client until the Weave WebDAV write cutover is available.",
    )
    require_absent(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "/api/files/upload",
        "/api/files/folders",
        "/api/files/{id}/download",
        "Nextcloud",
    )


def require_caldav_calendar_fail_closed() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        "TARGET_STANDARDS_CALDAV_CALENDAR_FAIL_CLOSED",
        "#967",
        "#1018",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesCalendarFacadeControllerTest.java",
        "calendarClientSetupExposesSecretFreePlatformOptionsWithoutAdapterCredentials",
        'jsonPath("$.endpoints.serverUrl").value("/caldav")',
        "calendarNativeSyncSetupExposesWeaveOwnedOsBoundariesWithoutProviderLeaks",
        "calDavOptionsAndPropfindExposeWeaveCalendarProjectionWithoutProviderLeaks",
        "calDavReportSkeletonRecognizesCalendarQueryAndFreeBusyButFailsClosed",
        "calDavEventReadPutAndDeleteUseCalendarFacadeBoundaryAndStableErrors",
        "calendar-adapter-not-configured",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        "calendarCalDavMethodsRouteThroughCalendarFacadeUseCases",
    )


def require_matrix_chat_fail_closed() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        "TARGET_STANDARDS_MATRIX_CHAT_FAIL_CLOSED",
        "#1017",
        "#1022",
    )
    require(
        "client/lib/features/chat/presentation/providers/chat_repository_provider.dart",
        "MatrixChatRepository",
        "Matrix Client-Server projection",
        "`/api/chat/**` remains a transitional/control facade",
        "normal message sync/send does not",
    )
    require(
        "client/test/architecture/backend_facade_contract_test.dart",
        "primary chat provider is wired through the Matrix Client-Server projection",
        "isNot(contains('BackendChatRepository('))",
        "matrixSessionServiceProvider",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java",
        "matrixClientServerProjectionRequiresWorkspaceToken",
        "matrixClientServerProjectionFailsClosedWithoutProviderPayloads",
        "M_WEAVE_MATRIX_PROJECTION_UNAVAILABLE",
        "northbound-matrix-client-server",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        "matrixClientServerProjectionIsBoundarySkeletonNotBridgeOrRestChatDataPlane",
    )


def main() -> int:
    require_files_webdav_current_proof()
    require_caldav_calendar_fail_closed()
    require_matrix_chat_fail_closed()
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
