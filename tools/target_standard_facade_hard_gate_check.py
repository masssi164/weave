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
        "http.StreamedRequest('PUT'",
        "'PUT'",
        "'MKCOL'",
        "'DELETE'",
        "'dav', 'files'",
        "_httpClient.get(",
        "'If-None-Match': '*'",
        "'If-Match': '*'",
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
        "OPENAPI_TRANSITIONAL_DATA_PLANE_DEPRECATED",
        "#967",
        "#1018",
        "#1044",
        "transitional Calendar REST event routes are deprecated",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesCalendarFacadeControllerTest.java",
        "calendarClientSetupExposesSecretFreePlatformOptionsWithoutAdapterCredentials",
        'jsonPath("$.endpoints.serverUrl").value("/caldav")',
        "calendarNativeSyncSetupExposesWeaveOwnedOsBoundariesWithoutProviderLeaks",
        "calendarRestEventDataPlaneIsDeprecatedInFavorOfCaldavFacade",
        "X-Weave-Deprecated-Data-Plane",
        "calendar-rest-compatibility",
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
        "OPENAPI_TRANSITIONAL_DATA_PLANE_DEPRECATED",
        "#1017",
        "#1022",
        "#1044",
        "transitional Chat REST conversation and message routes are deprecated",
    )
    require(
        "client/lib/features/chat/presentation/providers/chat_repository_provider.dart",
        "WeaveMatrixFacadeChatRepository",
        "Matrix Client-Server projection",
        "`/api/chat/**` remains a transitional/control facade",
        "direct Matrix SDK",
    )
    require(
        "client/test/architecture/backend_facade_contract_test.dart",
        "primary chat provider is wired through the Matrix Client-Server projection",
        "isNot(contains('BackendChatRepository('))",
        "isNot(contains('matrixSessionServiceProvider'))",
    )
    require_absent("client/pubspec.yaml", "matrix:", "flutter_vodozemac")
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java",
        "matrixClientServerProjectionRequiresWorkspaceToken",
        "matrixClientServerProjectionVersionsAdvertisesOidcGatedRustCoreFacade",
        "matrixClientServerProjectionSyncsCanonicalChatAsMatrixRoomsWithoutProviderPayloads",
        "matrixClientServerProjectionSendsViaCanonicalChatFacade",
        "matrixClientServerProjectionListsJoinedRoomsAndRoomMessages",
        "!channel-general:weave.local",
        "northbound-matrix-client-server",
        "spring-boot-resource-server",
        "weave_matrix_core",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/ChatControllerTest.java",
        "chatRestMessageDataPlaneIsDeprecatedInFavorOfMatrixFacade",
        "X-Weave-Deprecated-Data-Plane",
        "chat-rest-compatibility",
        "/_matrix/client/**",
        "https://github.com/masssi164/weave/issues/1044",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/OpenApiDocumentationTest.java",
        "deprecated",
        "/_matrix/client/**",
        "/caldav/**",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        "matrixClientServerProjectionUsesChatFacadeNotBridgeOrRestChatDataPlane",
        "matrixProtocolCoreBoundaryDefinesRustJniAndFlutterBridgeTarget",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/matrix/NativeMatrixCore.java",
        "public static native String matrixFacadeDescriptorJson",
        "weave_matrix_core",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/matrix/MatrixProtocolCoreService.java",
        "spring-boot-resource-server",
        "ruma-serde-serde_json-thiserror-tracing",
        "server-jni-wrapper",
        "flutter-rust-bridge",
        "northboundHomeserverDependency",
    )
    require(
        "rust/matrix-core/src/lib.rs",
        "OwnedRoomId",
        "OwnedUserId",
        "matrix_facade_descriptor_json",
        "Java_com_massimotter_weave_backend_matrix_NativeMatrixCore_matrixFacadeDescriptorJson",
        "northbound_homeserver_dependency: false",
    )
    require(
        "build.gradle",
        "matrixRustCoreTest",
        "cargo",
        "weave-matrix-core",
    )
    require(
        "client/test/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge_test.dart",
        "RUST_MATRIX_CORE_BRIDGE_CONTRACT",
        "flutter-rust-bridge",
        "spring-boot-resource-server",
    )
    require(
        "client/lib/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart",
        "RustMatrixCoreBridge",
        "matrix-client-server-facade",
        "northboundHomeserverDependency",
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
