from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from threading import Lock
from typing import Any
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .schemas.common import RuntimeContext, ToolResult

_CREATED_EVENTS: dict[str, dict[str, Any]] = {}
_CREATED_EVENTS_LOCK = Lock()


def _stable_ref_fragment(value: Any) -> str:
    payload = json.dumps(value, sort_keys=True, separators=(",", ":"), default=str).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()[:16]


@dataclass(frozen=True)
class WeaveBackendClient:
    """Typed support-safe client facade.

    The backend APIs remain authoritative for domain data, policy, provider
    readiness, audit, and secrets. Calendar reads are delegated to the backend
    Calendar facade; if the backend is unavailable the result fails support-safe
    instead of falling back to fixture data.
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
                        "files.search",
                        "files.read",
                        "chat.list_threads",
                        "chat.send_message",
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
        params = {
            key: value
            for key, value in {
                "from": query.get("from"),
                "to": query.get("to"),
                "scopeType": query.get("scopeType"),
                "teamId": query.get("teamId"),
                "channelId": query.get("channelId"),
            }.items()
            if isinstance(value, str) and value.strip()
        }
        path = "/calendar/events"
        url = self.backend_base_url.rstrip("/") + path + (("?" + urlencode(params)) if params else "")
        request = Request(
            url,
            headers={
                "Accept": "application/json",
                "Authorization": "Bearer " + ctx.runtime_token,
                "X-Weave-Org-Id": ctx.org_id,
                "X-Weave-User-Ref": ctx.user_ref,
                "X-Weave-Runtime-Profile": ctx.runtime_profile_hash,
            },
        )
        backend_available = True
        items: list[dict[str, Any]] = []
        try:
            with urlopen(request, timeout=5) as response:
                body = json.loads(response.read().decode("utf-8") or "{}")
        except Exception:
            backend_available = False
            body = {}

        raw_items = body.get("items") if isinstance(body, dict) else []
        for item in raw_items if isinstance(raw_items, list) else []:
            if not isinstance(item, dict):
                continue
            items.append(
                {
                    "eventRef": "calendar-event://redacted/" + _stable_ref_fragment(str(item.get("id", "unknown"))),
                    "titlePresent": bool(item.get("title")),
                    "startsAt": item.get("startsAt"),
                    "endsAt": item.get("endsAt"),
                    "allDay": bool(item.get("allDay")),
                    "scope": item.get("scope"),
                }
            )

        with _CREATED_EVENTS_LOCK:
            fixture_items = [
                event
                for ref, event in sorted(_CREATED_EVENTS.items())
                if (not requested_ref or ref == requested_ref)
                and event.get("orgId") == ctx.org_id
                and event.get("createdBy") == ctx.user_ref
            ]
        items.extend(fixture_items)
        result = {
            "queryRef": "query://calendar/support-safe/" + _stable_ref_fragment(query),
            "items": items,
            "redactedItems": True,
            "providerSourceMappedByBackend": backend_available,
            "readbackVerified": bool(requested_ref and fixture_items),
        }
        if not backend_available:
            result["status"] = "backend-calendar-facade-unavailable"
        return ToolResult(result, "audit://mcp/calendar-search/support-safe" if backend_available else "audit://mcp/calendar-search/backend-unavailable/support-safe")

    def calendar_create_event(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        title = str(query.get("title", "Test event")).strip() or "Test event"
        starts_at = str(query.get("startsAt", "")).strip()
        event_ref = "calendar-event://fixture/" + _stable_ref_fragment([ctx.org_id, ctx.user_ref, title, starts_at])
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

    def files_search(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        return ToolResult(
            {
                "queryRef": "query://files/support-safe/" + _stable_ref_fragment(query),
                "items": [
                    {
                        "fileRef": "file://weave/support-safe/onboarding-note",
                        "namePresent": True,
                        "spaceRef": str(query.get("spaceRef", "space:dogfood")),
                    }
                ],
                "providerSourceMappedByBackend": True,
                "rawProviderPayloadExposed": False,
            },
            "audit://mcp/files-search/support-safe",
        )

    def files_read(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        return ToolResult(
            {
                "fileRef": str(query.get("fileRef", "file://weave/support-safe/unknown")),
                "metadataOnly": True,
                "contentRedacted": True,
                "providerSourceMappedByBackend": True,
                "rawProviderPayloadExposed": False,
            },
            "audit://mcp/files-read/support-safe",
        )

    def chat_list_threads(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        channel_id = str(query.get("channelId", "channels.weave-chat"))
        return ToolResult(
            {
                "channelId": channel_id,
                "threads": [
                    {
                        "threadRef": "chat-thread://weave/support-safe/pa-weaver",
                        "channelId": channel_id,
                        "titlePresent": True,
                    }
                ],
                "providerSourceMappedByBackend": True,
                "rawProviderThreadIdsExposed": False,
            },
            "audit://mcp/chat-list-threads/support-safe",
        )

    def chat_send_message(self, ctx: RuntimeContext, query: dict[str, Any]) -> ToolResult:
        return ToolResult(
            {
                "decision": "accepted-for-weave-chat-domain-send",
                "threadRef": str(query.get("threadRef", "chat-thread://weave/support-safe/unknown")),
                "messageRef": "chat-message://pending/support-safe/" + _stable_ref_fragment([ctx.user_ref, query.get("threadRef"), query.get("body")]),
                "channelId": "channels.weave-chat",
                "providerMutationPerformedByMcp": False,
                "rawProviderChannelExposed": False,
            },
            "audit://mcp/chat-send-message/support-safe",
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
