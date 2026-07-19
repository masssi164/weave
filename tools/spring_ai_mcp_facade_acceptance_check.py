#!/usr/bin/env python3
"""Verify the Spring AI MCP projection remains canonical and OIDC-gated."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = (
    "SPRING_AI_MCP_STATEFUL_TRANSPORT",
    "MCP_OIDC_GATEKEEPER",
    "MCP_CANONICAL_DOMAIN_DISPATCH",
    "MCP_PROVIDER_NEUTRAL_OUTPUT",
    "MCP_RUNTIME_APPROVED_DISCOVERY",
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
        "org.springframework.boot' version '4.1.0",
        "spring-ai-bom:2.0.0",
        "spring-ai-starter-mcp-server-webmvc",
        "spring-boot-starter-security-oauth2-resource-server",
    )
    require(
        "weave-mcp-server/src/main/resources/application.yml",
        "protocol: STREAMABLE",
        "mcp-endpoint: /mcp",
        "issuer-uri:",
        "audiences:",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpSecurityConfiguration.java",
        '.requestMatchers("/mcp", "/mcp/**").access(memberMcpAccess)',
        "validMemberToken",
        ".oauth2ResourceServer",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpTransportConfiguration.java",
        "WebMvcStreamableServerTransportProvider",
        ".contextExtractor",
        ".mcpEndpoint(endpoint)",
    )
    features = require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/CanonicalMcpFeatures.java",
        "@McpTool(",
        "McpSyncRequestContext",
        'APPROVED_TOOLS_RESOURCE = "weave://runtime/approved-tools"',
        'WORKSPACE_PROMPT = "weave.workspace.plan"',
        "approvalService.requireApproval(",
    )
    for forbidden in ("Nextcloud", "Synapse", "SlackClient", "TeamsClient", "ProviderAdapter"):
        if forbidden in features:
            fail(f"CanonicalMcpFeatures.java leaks provider implementation term: {forbidden}")
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpToolApprovalService.java",
        "context.elicit(",
        '"mcp-elicitation/v1"',
        '"elicitation://openclaw/"',
        '"allow-always"',
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/service/WeaverMcpBridgeService.java",
        ".filter(definition -> !definition.approvalRequired())",
        'auditRef(toolName, "trusted_approval_evidence_unavailable")',
        "without a parallel Weave approval workflow",
    )
    require(
        "server/src/main/java/com/massimotter/weave/backend/weaver/MemberDomainToolDispatcher.java",
        'case "files.search" -> filesSearch',
        'case "calendar.search_events" -> calendarSearchEvents',
        'case "chat.send_message" -> chatSendMessage',
        "chatDomainFacadeService.sendMessage(",
    )
    require(
        "weave-mcp-server/src/test/java/com/massimotter/weave/mcp/SpringAiMcpTransportTest.java",
        "oidcIsTheGatekeeperForSpringAiMcpTransport",
        "springAiInitializesAndAdvertisesOnlyCanonicalDomainToolNames",
        "approvedToolsResourceIsResolvedThroughTheOidcBoundBackendProfile",
        "statefulRequestsRejectUnknownSessions",
    )
    require(
        "weave-mcp-server/src/test/java/com/massimotter/weave/mcp/CanonicalMcpFeaturesTest.java",
        "springAiToolsAreGeneratedFromAnnotatedCanonicalMethods",
        "toolInvocationPreflightsRuntimeGrantAndReturnsSupportSafeStructuredContent",
        "approvedToolsResourceAndWorkspacePromptUseBackendGrantedCatalogOnly",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/service/WeaverMcpBridgeServiceTest.java",
        "writeToolRequiresApprovalBeforeDispatch",
        "callerSuppliedElicitationCannotMintWeaveAuthority",
        "trusted_approval_evidence_unavailable",
    )
    require(
        "infra/weave-workspace/01-infrastructure/modules/mcp/main.tf",
        '"WEAVE_SERVER_BASE_URL=${var.backend_base_url}"',
        '"WEAVE_OIDC_ISSUER_URI=${var.oidc_issuer_uri}"',
        "127.0.0.1",
    )
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpJsonRpcController.java")
    require_absent("server/src/main/java/com/massimotter/weave/backend/weaver/WeaverMcpApprovalReceiptService.java")
    require_absent("infra/weave-mcp/pyproject.toml")
    require_absent("infra/weave-mcp/src/weave_mcp/fastmcp_app.py")
    require_absent("infra/weave-mcp/src/weave_mcp/app.py")

    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
