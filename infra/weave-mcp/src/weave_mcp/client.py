from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .schemas.common import RuntimeContext, ToolResult


@dataclass(frozen=True)
class WeaveBackendClient:
    """Typed support-safe client facade.

    Sprint 16 keeps this as a deterministic fixture client. A later adapter may
    call backend endpoints, but backend APIs remain authoritative for domain
    data, policy, provider readiness, audit, and secrets.
    """

    backend_base_url: str

    def admin_readiness(self, ctx: RuntimeContext) -> ToolResult:
        return ToolResult(
            {
                "organizationId": ctx.org_id,
                "state": "disabled-by-default",
                "transport": "streamable-http",
                "backendAuthority": "weave-backend",
                "normalMembersMayConfigureMcpServers": False,
                "providerDiagnosticsRedacted": True,
            },
            "audit://mcp/admin-readiness/support-safe",
        )

    def runtime_profile_projection(self, ctx: RuntimeContext) -> ToolResult:
        return ToolResult(
            {
                "runtimeProfileHash": ctx.runtime_profile_hash,
                "mcpServerBindings": [
                    {
                        "serverKey": "weave-domain-tools",
                        "transport": "streamable-http",
                        "endpointRef": "internal://weave-mcp/streamable-http",
                        "enabled": False,
                        "discoverableTools": [
                            "admin.get_readiness",
                            "weaver.get_runtime_profile_projection",
                            "calendar.search_events",
                            "boards.comment",
                        ],
                        "rawEndpointExposed": False,
                        "credentialRef": "credentialref://weave/mcp/weave-domain-tools/runtime-token",
                    }
                ],
                "supportSafe": True,
            },
            "audit://mcp/runtime-profile-projection/support-safe",
        )

    def calendar_search_events(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        return ToolResult(
            {
                "queryRef": "query://calendar/support-safe/" + str(abs(hash(repr(sorted(query.items()))))),
                "items": [],
                "redactedItems": True,
                "providerSourceMappedByBackend": True,
            },
            "audit://mcp/calendar-search/support-safe",
        )

    def boards_comment(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        return ToolResult(
            {
                "decision": "accepted-for-backend-action-request",
                "taskRef": str(query.get("taskRef", "task://support-safe/unknown")),
                "commentRef": "comment://pending/support-safe",
                "providerMutationPerformedByMcp": False,
            },
            "audit://mcp/boards-comment/support-safe",
        )
