#!/usr/bin/env python3
"""Verify the MCP transport remains canonical and dark until workload binding exists."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = (
    "SPRING_AI_MCP_STATEFUL_TRANSPORT",
    "MCP_WORKLOAD_EDGE_DENY_ALL",
    "MCP_CANONICAL_DOMAIN_DISPATCH",
    "MCP_PROVIDER_NEUTRAL_OUTPUT",
    "MCP_RUNTIME_DISCOVERY_DARK_UNTIL_ARC",
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
        '.requestMatchers("/mcp", "/mcp/**").denyAll()',
        "service-account -> cell -> RuntimeProfile v2 binding",
        ".oauth2ResourceServer",
    )
    require(
        "weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpTransportConfiguration.java",
        "WebMvcStreamableServerTransportProvider",
        ".mcpEndpoint(endpoint)",
    )
    require(
        "weave-mcp-server/src/test/java/com/massimotter/weave/mcp/SpringAiMcpTransportTest.java",
        "oidcIsTheGatekeeperForSpringAiMcpTransport",
        "mcpRemainsDarkForHumansAndUnboundWorkloadsUntilArcBindingExists",
        '"member", "weave-mcp-server", "generic-service-account", "weaver-cell-test"',
    )
    require(
        "infra/weave-workspace/01-infrastructure/modules/mcp/main.tf",
        '"WEAVE_OIDC_ISSUER_URI=${var.oidc_issuer_uri}"',
        "127.0.0.1",
    )
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpJsonRpcController.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/CanonicalMcpFeatures.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/McpToolApprovalService.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/WeaveTokenExchangeClient.java")
    require_absent("weave-mcp-server/src/main/java/com/massimotter/weave/mcp/WeaveServerClient.java")
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
