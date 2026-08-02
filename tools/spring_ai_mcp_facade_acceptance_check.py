#!/usr/bin/env python3
"""Verify the canonical MCP transport admits only a current ARC-bound workload."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = (
    "SPRING_AI_MCP_STATEFUL_TRANSPORT",
    "MCP_WORKLOAD_EDGE_BOUND_CELL_ONLY",
    "MCP_FILES_READ_SLICE_ACTIVE",
    "MCP_CALENDAR_CATALOG_GUARDED",
    "MCP_CHAT_CATALOG_GUARDED",
    "MCP_PROVIDER_NEUTRAL_OUTPUT",
    "MCP_STANDARD_SERVER_PROJECTION_ACTIVE",
    "MCP_APPROVAL_EVIDENCE_FAILS_CLOSED",
    "MCP_LEGACY_RUNTIME_REMOVED",
)


def fail(message: str) -> None:
    print(f"spring-ai-mcp-facade-acceptance: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(path: str, *fragments: str) -> str:
    file_path = ROOT / path
    if not file_path.is_file():
        fail(f"missing required file {path}")
    text = file_path.read_text(encoding="utf-8")
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path} is missing required fragment: {fragment}")
    return text


def require_absent(path: str) -> None:
    if (ROOT / path).exists():
        fail(f"obsolete runtime file still exists: {path}")


def main() -> int:
    require(
        "weave-mcp-server/build.gradle",
        "alias(libs.plugins.spring.boot)",
        'apply from: "${projectDir}/gradle/scripts/java-and-dependencies.gradle"',
        'apply from: "${projectDir}/gradle/tasks/verification.gradle"',
    )
    require(
        "weave-mcp-server/gradle/scripts/java-and-dependencies.gradle",
        "spring-ai-bom:${libs.versions.spring.ai.get()}",
        "spring-ai-starter-mcp-server-webmvc",
        "spring-boot-starter-restclient",
        "spring-boot-starter-security-oauth2-client",
        "spring-boot-starter-security-oauth2-resource-server",
    )
    require(
        "gradle/libs.versions.toml",
        'spring-boot = "4.1.0"',
        'spring-ai = "2.0.0"',
    )
    require(
        "weave-mcp-server/src/main/resources/application.yml",
        "protocol: STREAMABLE",
        "mcp-endpoint: /mcp",
        "issuer-uri:",
        "resource-uri:",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpSecurityConfiguration.java",
        '.requestMatchers("/mcp", "/mcp/**")',
        ".authenticated()",
        "McpAccessTokenTypeValidator",
        "PROTECTED_RESOURCE_METADATA_PATH",
        ".oauth2ResourceServer",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpRequestAdmissionFilter.java",
        "CLIENT_CREDENTIALS_EXTENSION",
        "exchange.exchange(",
        "EXCHANGED_TOKEN_ATTRIBUTE",
        "Set.copyOf(properties.exchangeScopes())",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/FilesMcpProjection.java",
        'name = "files.search"',
        'uri = "weave://files/{canonicalFileRef}"',
        "readOnlyHint = true",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/FilesWebDavClient.java",
        'HttpMethod.valueOf("SEARCH")',
        "<w:canonical-id/>",
        "<d:eq>",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpTransportConfiguration.java",
        "WebMvcStreamableServerTransportProvider",
        ".mcpEndpoint(endpoint)",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/SpringSecurityMcpBackendTokenExchange.java",
        "TokenExchangeOAuth2AuthorizedClientProvider",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpAuthorizationConfiguration.java",
        "RestClientTokenExchangeTokenResponseClient",
        "NimbusJwtClientAuthenticationParametersConverter",
        "ClientAuthenticationMethod.PRIVATE_KEY_JWT",
        "AuthorizationGrantType.TOKEN_EXCHANGE",
        "OAuth2ParameterNames.AUDIENCE",
    )
    require(
        "weave-mcp-server/src/test/java/com/massimotter/weave/mcp/SpringAiMcpTransportTest.java",
        "publishesProtectedResourceMetadataWithoutAuthentication",
        "humanBearerCannotDiscoverTheMcpCatalog",
        "extensionNegotiationIsMandatoryForWorkloadClientCredentials",
        "boundCellIsExchangedAndDispatchedThroughTheFrameworkTransport",
        "discoversTheCuratedFilesToolAndCanonicalResourceTemplate",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/config/FilesWebDavSecurityConfiguration.java",
        '@Qualifier("filesMcpWorkloadJwtDecoder")',
        '.securityMatcher("/dav/files", "/dav/files/**")',
        "SCOPE_files.read",
    )
    require(
        "infra/weave-workspace/scripts/render_config.py",
        '"WEAVE_OIDC_ISSUER_URI": issuer',
        '"WEAVE_MCP_REQUIRED_SCOPES": "mcp.tools,files.read"',
        '"WEAVE_MCP_EXCHANGE_CLIENT_JWK_FILE": "/run/secrets/weave/mcp-private-jwk.json"',
        '"WEAVE_MCP_BACKEND_FILES_URI": "http://backend:8080/dav/files"',
        '"WEAVE_MCP_EXCHANGE_SCOPES": "files.read"',
    )
    require(
        "infra/weave-workspace/compose.yaml",
        "${WEAVE_GENERATED_ROOT:-./.generated/dev}/mcp/public.env",
        '"127.0.0.1:${WEAVE_MCP_HOST_PORT:-48085}:8091"',
        "/run/secrets/weave/mcp-private-jwk.json",
    )
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpJsonRpcController.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/CanonicalMcpFeatures.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpToolApprovalService.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/WeaveTokenExchangeClient.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/WeaveServerClient.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/HttpMcpBackendContextResolver.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/HttpMcpBackendTokenExchange.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/config/McpWorkloadBridgeSecurityConfiguration.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/controller/AgentRuntimeMcpContextController.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/service/WeaverMcpBridgeService.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/weaver/MemberDomainToolDispatcher.java")
    require_absent("server/src/test/java/com/massimotter/weave/backend/service/WeaverMcpBridgeServiceTest.java")
    require_absent("server/src/test/java/com/massimotter/weave/backend/weaver/MemberDomainToolDispatcherTest.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/weaver/WeaverMcpApprovalReceiptService.java")
    require_absent("infra/weave-mcp/pyproject.toml")
    require_absent("infra/weave-mcp/src/weave_mcp/fastmcp_app.py")
    require_absent("infra/weave-mcp/src/weave_mcp/app.py")

    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
