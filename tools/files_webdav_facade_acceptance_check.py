#!/usr/bin/env python3
"""Guard generated Files User consumption and classify retained DAV evidence.

This source-level check is not live provider, permission, or E2E proof.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKERS = (
    "FILES_WEBDAV_FACADE_READ_LIST_DOWNLOAD",
    "FILES_WEBDAV_WRITE_MVP",
    "FILES_MCP_FACADE_NO_PROVIDER_BYPASS",
    "FILES_USER_API_GENERATED_CLIENT",
)


def fail(message: str) -> None:
    print(f"files-boundary-acceptance-check: {message}", file=sys.stderr)
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
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesWebDavControllerTest.java",
        "propfindDepthOneReturnsChildrenAsDavResponses",
        "getDownloadsFileThroughFacadePath",
        "putMkcolAndDeleteUseWebDavFacadeWriteUseCases",
        "preconditionFailuresReturnStableWebDavErrorWithoutProviderLeakage",
        "not(containsString(\"remote.php\"))",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/FilesFacadeServiceTest.java",
        "webDavWritePreconditionsFailBeforeStorageMutationButAfterAttemptAudit",
        "webDavWriteRejectionsRequireEditPolicyAndPublishSupportSafeAudit",
    )
    require(
        "infra/weave-workspace/weave-mcp-tool-contract.json",
        '"runtimeDirectProviderAccessAllowed": false',
        '"rawProviderInternalsReturned": false',
        '"credentialBearingUrlsReturned": false',
    )
    require(
        "e2e/features/files_webdav_facade.feature",
        "Historical Files WebDAV facade evidence",
        "public DAV as the current Files product boundary",
    )


def check_generated_user_boundary() -> None:
    paths = json.loads(read("contracts/openapi/weave-user-openapi.json"))["paths"]
    required = {
        ("/api/files/items", "get"): "listFilesItems",
        ("/api/files/items/{fileId}", "get"): "getFilesItem",
        ("/api/files/items/{fileId}/content", "get"): "downloadFilesItemContent",
        ("/api/files/items/folders", "post"): "createFilesFolder",
        ("/api/files/items/uploads", "post"): "uploadFilesItemContent",
        ("/api/files/items/{fileId}/content", "put"): "updateFilesItemContent",
    }
    for (path, method), operation in required.items():
        actual = paths.get(path, {}).get(method, {}).get("operationId")
        if actual != operation:
            fail(f"{method.upper()} {path} expected {operation}, found {actual}")
    require(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "package:weave/generated/user_api/api.dart",
        "user_api.FilesUserApi(",
        "api.listFilesItems(",
        "api.downloadFilesItemContentWithHttpInfo(",
        "api.createFilesFolder(",
        "api.uploadFilesItemContent(",
        "maxTransferBytes = 25 * 1024 * 1024",
    )
    require(
        "client/test/features/files/presentation/providers/files_backend_facade_provider_test.dart",
        "uses the generated User API repository for release members",
        "download validates exact bytes, length, digest and strong ETag",
        "upload sends exact bounded binary body and idempotency headers",
        "source stream failure stops upload without sending a partial body",
        "unsupported mutations and denied actions never use a fallback route",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/files/FilesUserApiServiceTest.java",
        "otherMemberCannotInspectOrDownloadOwnerResource",
        "anotherOrganizationCannotResolveTheSameOpaqueFileId",
        "observedPathReplacementDuringDownloadFailsClosed",
        "currentReadOnlyGrantDoesNotAdvertiseMutationOrShareActions",
    )


def main() -> int:
    check_historical_dav()
    check_generated_user_boundary()
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
