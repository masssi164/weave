#!/usr/bin/env python3
"""Validate the Matrix/WebDAV/CalDAV hard-gate acceptance evidence."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = [
    "TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF",
    "TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP",
    "TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP",
]


def fail(message: str) -> None:
    print(f"target-standard-facade-hard-gate-check: {message}", file=sys.stderr)
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
        "'PUT'",
        "'MKCOL'",
        "'DELETE'",
        "'dav', 'files'",
        "_httpClient.get(",
        "'If-None-Match': '*'",
    )
    require_absent(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "/api/files/upload",
        "/api/files/folders",
        "/api/files/{id}/download",
        "Nextcloud",
    )


def require_caldav_calendar_server_mvp() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        "TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP",
        "#967",
        "#1018",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesCalendarFacadeControllerTest.java",
        "calendarClientSetupExposesSecretFreePlatformOptionsWithoutAdapterCredentials",
        'jsonPath("$.endpoints.serverUrl").value("/caldav")',
        "calendarNativeSyncSetupExposesWeaveOwnedOsBoundariesWithoutProviderLeaks",
        "calDavOptionsAndPropfindExposeWeaveCalendarProjectionWithoutProviderLeaks",
        "calDavReportCalendarQueryAndFreeBusyReturnFacadeBackedCalendarData",
        "BEGIN:VFREEBUSY",
        "FREEBUSY:20260708T100000Z/20260708T110000Z",
        "calDavEventReadPutAndDeleteUseCalendarFacadeBoundaryAndStableErrors",
        "calendar-adapter-not-configured",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        "calendarCalDavMethodsRouteThroughCalendarFacadeUseCases",
    )


def require_matrix_chat_server_mvp() -> None:
    require(
        "e2e/features/target_standard_facade_hard_gate.feature",
        "TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP",
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
        "matrixClientServerProjectionSyncsCanonicalChatAsMatrixRoomsWithoutProviderPayloads",
        "matrixClientServerProjectionSendsViaCanonicalChatFacade",
        "matrixClientServerProjectionListsJoinedRoomsAndRoomMessages",
        "!channel-general:weave.local",
        "northbound-matrix-client-server",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        "matrixClientServerProjectionUsesChatFacadeNotBridgeOrRestChatDataPlane",
    )


def main() -> int:
    require_files_webdav_current_proof()
    require_caldav_calendar_server_mvp()
    require_matrix_chat_server_mvp()
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
