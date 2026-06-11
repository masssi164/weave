from __future__ import annotations

from dataclasses import dataclass
from threading import Lock
from typing import Any

from .schemas.common import RuntimeContext, ToolResult

_CREATED_EVENTS: dict[str, dict[str, Any]] = {}
_CREATED_EVENTS_LOCK = Lock()


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
                            "calendar.create_event",
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
        requested_ref = str(query.get("eventRef", "")).strip()
        with _CREATED_EVENTS_LOCK:
            items = [
                event
                for ref, event in sorted(_CREATED_EVENTS.items())
                if (not requested_ref or ref == requested_ref)
                and event.get("orgId") == ctx.org_id
                and event.get("createdBy") == ctx.user_ref
            ]
        return ToolResult(
            {
                "queryRef": "query://calendar/support-safe/" + str(abs(hash(repr(sorted(query.items()))))),
                "items": items,
                "redactedItems": True,
                "providerSourceMappedByBackend": True,
                "readbackVerified": bool(requested_ref and items),
            },
            "audit://mcp/calendar-search/support-safe",
        )

    def calendar_create_event(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        title = str(query.get("title", "Test event")).strip() or "Test event"
        starts_at = str(query.get("startsAt", "")).strip()
        event_ref = "calendar-event://fixture/" + str(abs(hash((ctx.org_id, ctx.user_ref, title, starts_at))))
        event = {
            "eventRef": event_ref,
            "title": title,
            "startsAt": starts_at,
            "orgId": ctx.org_id,
            "createdBy": ctx.user_ref,
            "calendarRef": "calendar://fixture/weave-governed-tool-proof",
            "providerMutationPerformedByMcp": False,
            "stateChangeFixtureOnly": True,
        }
        with _CREATED_EVENTS_LOCK:
            _CREATED_EVENTS[event_ref] = event
        readback = self.calendar_search_events(ctx, {"eventRef": event_ref}).support_safe()
        return ToolResult(
            {
                "decision": "created-test-fixture-event",
                "eventRef": event_ref,
                "approvalRef": str(query.get("approvalRef", "")),
                "readbackVerified": readback.get("readbackVerified") is True,
                "finalChatAnswer": f"Ich habe das Testereignis um {starts_at} angelegt. Audit: audit://mcp/calendar-create/support-safe",
                "providerMutationPerformedByMcp": False,
            },
            "audit://mcp/calendar-create/support-safe",
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
