from __future__ import annotations

from typing import Any, Callable

from ..client import WeaveBackendClient
from ..schemas.common import (
    McpDenied,
    RuntimeContext,
    ToolDefinition,
    require_approval,
    require_approval_or_scoped_always_allow,
    require_capability,
    require_tool_allowed,
)
from ..redaction import assert_support_safe

Handler = Callable[[RuntimeContext, dict[str, Any], WeaveBackendClient], dict[str, Any]]

TOOL_DEFINITIONS: dict[str, ToolDefinition] = {
    "admin.get_readiness": ToolDefinition(
        name="admin.get_readiness",
        capability="weaver.admin_readiness_read",
        domain="admin_setup_providers",
        read_only=True,
        approval_required=False,
        description="Read support-safe admin readiness from the Weave backend control plane.",
    ),
    "weaver.get_runtime_profile_projection": ToolDefinition(
        name="weaver.get_runtime_profile_projection",
        capability="weaver.runtime_profile_read",
        domain="weaver_runtime_governance",
        read_only=True,
        approval_required=False,
        description="Read support-safe MCP bindings projected into the Weaver RuntimeProfile.",
    ),
    "calendar.search_events": ToolDefinition(
        name="calendar.search_events",
        capability="weaver.calendar_read",
        domain="calendar",
        read_only=True,
        approval_required=False,
        description="Search calendar events via the backend Calendar facade with redacted support-safe output.",
        input_schema={"type": "object", "properties": {"query": {"type": "string"}, "eventRef": {"type": "string"}}},
    ),
    "calendar.create_event": ToolDefinition(
        name="calendar.create_event",
        capability="weaver.calendar_create_event",
        domain="calendar",
        read_only=False,
        approval_required=True,
        description="Create a support-safe test calendar event through the backend Calendar facade and return a readback reference.",
        input_schema={
            "type": "object",
            "required": ["title", "startsAt"],
            "properties": {
                "title": {"type": "string"},
                "startsAt": {"type": "string"},
                "approvalReceiptRef": {"type": "string"},
                "alwaysAllowGrantRef": {"type": "string"},
            },
        },
    ),
    "boards.comment": ToolDefinition(
        name="boards.comment",
        capability="weaver.boards_write",
        domain="boards_tasks",
        read_only=False,
        approval_required=True,
        description="Approval-required write stub for adding a board/task comment through backend action requests.",
        input_schema={"type": "object", "required": ["taskRef", "body", "approvalReceiptRef"]},
    ),
}


def _admin_get_readiness(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    return client.admin_readiness(ctx).support_safe()


def _runtime_profile(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    return client.runtime_profile_projection(ctx).support_safe()


def _calendar_search(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    return client.calendar_search_events(ctx, payload).support_safe()


def _calendar_create_event(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    approval_ref = require_approval_or_scoped_always_allow(ctx, payload, "calendar.create_event")
    return client.calendar_create_event(ctx, {**payload, "approvalRef": approval_ref}).support_safe()


def _boards_comment(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    require_approval(payload, "boards.comment")
    return client.boards_comment(ctx, payload).support_safe()


HANDLERS: dict[str, Handler] = {
    "admin.get_readiness": _admin_get_readiness,
    "weaver.get_runtime_profile_projection": _runtime_profile,
    "calendar.search_events": _calendar_search,
    "calendar.create_event": _calendar_create_event,
    "boards.comment": _boards_comment,
}


def discover(ctx: RuntimeContext | None) -> list[dict[str, Any]]:
    grants = ctx.capability_grants if ctx is not None else frozenset()
    allowed_tools = ctx.allowed_tools if ctx is not None else frozenset()
    return [
        definition.discovery(definition.capability in grants and definition.name in allowed_tools)
        for definition in TOOL_DEFINITIONS.values()
    ]


def invoke(name: str, ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    definition = TOOL_DEFINITIONS.get(name)
    if definition is None:
        raise McpDenied("unknown-tool")
    require_capability(ctx, definition.capability)
    require_tool_allowed(ctx, definition.name)
    result = HANDLERS[name](ctx, payload, client)
    assert_support_safe(result)
    return result
