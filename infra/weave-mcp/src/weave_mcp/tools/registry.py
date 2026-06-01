from __future__ import annotations

from typing import Any, Callable

from ..client import WeaveBackendClient
from ..schemas.common import McpDenied, RuntimeContext, ToolDefinition, require_approval, require_capability
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
        input_schema={"type": "object", "properties": {"query": {"type": "string"}}},
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


def _boards_comment(ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    require_approval(payload, "boards.comment")
    return client.boards_comment(ctx, payload).support_safe()


HANDLERS: dict[str, Handler] = {
    "admin.get_readiness": _admin_get_readiness,
    "weaver.get_runtime_profile_projection": _runtime_profile,
    "calendar.search_events": _calendar_search,
    "boards.comment": _boards_comment,
}


def discover(ctx: RuntimeContext | None) -> list[dict[str, Any]]:
    grants = ctx.capability_grants if ctx is not None else frozenset()
    return [definition.discovery(definition.capability in grants) for definition in TOOL_DEFINITIONS.values()]


def invoke(name: str, ctx: RuntimeContext, payload: dict[str, Any], client: WeaveBackendClient) -> dict[str, Any]:
    definition = TOOL_DEFINITIONS.get(name)
    if definition is None:
        raise McpDenied("unknown-tool")
    require_capability(ctx, definition.capability)
    result = HANDLERS[name](ctx, payload, client)
    assert_support_safe(result)
    return result
