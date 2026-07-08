#!/usr/bin/env python3
"""Deterministic acceptance check for the Files WebDAV facade scenarios."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = [
    "FILES_WEBDAV_FACADE_READ_LIST_DOWNLOAD",
    "FILES_WEBDAV_WRITES_FAIL_CLOSED",
    "FILES_MCP_FACADE_NO_PROVIDER_BYPASS",
]


def fail(message: str) -> None:
    print(f"files-webdav-acceptance-check: {message}", file=sys.stderr)
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


def require_openapi_boundary() -> None:
    openapi = json.loads(read("contracts/openapi/weave-openapi.json"))
    paths = set(openapi.get("paths", {}).keys())
    forbidden_data_plane_paths = {
        "/api/files",
        "/api/files/upload",
        "/api/files/folders",
        "/api/files/{id}",
        "/api/files/{id}/download",
    }
    leaked = sorted(paths.intersection(forbidden_data_plane_paths))
    if leaked:
        fail("OpenAPI still exposes Files member data-plane paths: " + ", ".join(leaked))
    required_control_paths = {
        "/api/files/readiness",
        "/api/files/native-provider-setup",
    }
    missing = sorted(required_control_paths.difference(paths))
    if missing:
        fail("OpenAPI is missing Files control-plane paths: " + ", ".join(missing))


def require_read_list_download() -> None:
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/FilesWebDavController.java",
        'DAV_ROOT = "/dav/files"',
        'case "PROPFIND"',
        'case "GET"',
        'case "HEAD"',
        "filesFacadeService.list(path)",
        "filesFacadeService.download(path)",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesWebDavControllerTest.java",
        "optionsAdvertisesReadOnlyWebdavMethods",
        "propfindDepthOneReturnsChildrenAsDavResponses",
        "getDownloadsFileThroughFacadePath",
        "not(containsString(\"remote.php\"))",
        "not(containsString(\"Bearer\"))",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/FilesFacadeServiceTest.java",
        "mapsProviderNamedAdapterErrorsToSupportSafeStorageErrors",
        "files-storage-unavailable",
        "diagnosticsRedacted",
    )
    require(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "File list/read data-plane operations use the",
        "http.Request('PROPFIND'",
        "'dav', 'files'",
        "_httpClient.get(",
    )
    require(
        "client/test/features/files/presentation/providers/files_backend_facade_provider_test.dart",
        "lists files through the Weave WebDAV data plane with the Weave token",
        "downloads files through the Weave WebDAV data plane",
        "isNot(contains('WebDAV'))",
    )
    require_openapi_boundary()


def require_writes_fail_closed() -> None:
    require(
        "server/src/main/java/com/massimotter/weave/backend/controller/FilesWebDavController.java",
        "webDavWriteBlocked",
        "filesFacadeService.rejectWebDavWrite(method, productPath(request))",
        "#1007",
        "OPTIONS, PROPFIND, GET, HEAD",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/FilesWebDavControllerTest.java",
        "writeAndLockMethodsAreExplicitlyNotImplementedUntilConflictPolicyExists",
        "HttpMethod.valueOf(\"PUT\")",
        "HttpMethod.valueOf(\"LOCK\")",
        "files-webdav-write-policy-required",
        "#1007",
    )
    require(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "_webDavWritesBlocked()",
        "Files writes are blocked until the Weave WebDAV write policy is available.",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/service/FilesFacadeService.java",
        "files-webdav-write-policy-required",
        "Files writes are blocked until the Weave WebDAV write policy is evidenced in #1007.",
        "FILES_WEBDAV_WRITE_BLOCKED",
        '"openApiDataPlaneUsed", false',
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/FilesFacadeServiceTest.java",
        "mutatingOperationsFailClosedBeforeStorageAdapterAccessUntilWebdavWritePolicyExists",
        "webDavWriteRejectionsRequireEditPolicyAndPublishSupportSafeAudit",
        "files-webdav-write-policy-required",
        "openApiDataPlaneUsed",
    )
    require(
        "client/test/features/files/presentation/providers/files_backend_facade_provider_test.dart",
        "fails closed for writes until WebDAV write policy is available",
        "WebDAV write policy",
    )
    require_absent(
        "client/lib/features/files/data/repositories/backend_files_repository.dart",
        "/api/files/upload",
        "/api/files/folders",
        "/api/files/{id}/download",
    )


def require_mcp_facade_boundary() -> None:
    require(
        "weave-contract/src/main/java/com/massimotter/weave/contract/mcp/MemberMcpToolResultProjections.java",
        "webDavFacadePath",
        "openApiDataPlaneUsed",
        "rawProviderPayload",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/weaver/MemberDomainToolDispatcher.java",
        'return "/dav/files"',
        "files_file_ref_requires_weave_webdav_facade_path",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/weaver/MemberDomainToolDispatcherTest.java",
        "filesSearchUsesFilesFacadeAndProjectsWebdavBackedMcpMetadata",
        "filesReadRejectsProviderShapedRefsBeforeProviderAccess",
        "https://files.example.invalid/remote.php/dav/files/readme.md",
    )
    require(
        "infra/weave-workspace/weave-mcp-tool-contract.json",
        '"dataPlaneAuthority": "weave-webdav-facade"',
        '"facadePath": "/dav/files"',
        "rawProviderUrl",
    )


def main() -> int:
    require_read_list_download()
    require_writes_fail_closed()
    require_mcp_facade_boundary()
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
