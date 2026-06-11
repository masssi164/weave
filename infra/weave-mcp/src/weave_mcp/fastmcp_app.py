from __future__ import annotations

from typing import Any

from .app import WeaveMcpGateway
from .config import WeaveMcpConfig
from .schemas.common import McpDenied

try:  # pragma: no cover - optional dependency adapter
    from fastmcp import FastMCP
except ImportError:  # pragma: no cover - exercised by dependency-free tests
    FastMCP = None  # type: ignore[assignment]


def build_fastmcp(config: WeaveMcpConfig | None = None) -> Any:
    """Build the optional FastMCP app without making FastMCP a test dependency.

    The stdlib Streamable HTTP app in ``weave_mcp.app`` is the deterministic
    local test harness. This adapter is the implementation candidate used when
    the optional ``fastmcp`` extra is installed. Both paths share the same
    gateway, auth, discovery, fail-closed invocation, and support-safe redaction.
    """

    if FastMCP is None:
        raise RuntimeError("Install weave-mcp[fastmcp] to run the FastMCP adapter")

    gateway = WeaveMcpGateway(config or WeaveMcpConfig.from_env())
    mcp = FastMCP("Weave Governed Domain Tools")

    def _headers(ctx: dict[str, str] | None) -> dict[str, str]:
        return {str(key).lower(): str(value) for key, value in (ctx or {}).items()}

    @mcp.tool(name="admin.get_readiness")
    def admin_get_readiness(context_headers: dict[str, str]) -> dict[str, Any]:
        return gateway.invoke_tool(_headers(context_headers), {"tool": "admin.get_readiness", "input": {}})

    @mcp.tool(name="weaver.get_runtime_profile_projection")
    def weaver_get_runtime_profile_projection(context_headers: dict[str, str]) -> dict[str, Any]:
        return gateway.invoke_tool(_headers(context_headers), {"tool": "weaver.get_runtime_profile_projection", "input": {}})

    @mcp.tool(name="calendar.search_events")
    def calendar_search_events(context_headers: dict[str, str], query: str = "") -> dict[str, Any]:
        return gateway.invoke_tool(_headers(context_headers), {"tool": "calendar.search_events", "input": {"query": query}})

    @mcp.tool(name="calendar.create_event")
    def calendar_create_event(
        context_headers: dict[str, str],
        title: str,
        starts_at: str,
        approval_receipt_ref: str | None = None,
        always_allow_grant_ref: str | None = None,
    ) -> dict[str, Any]:
        payload = {"title": title, "startsAt": starts_at}
        if approval_receipt_ref:
            payload["approvalReceiptRef"] = approval_receipt_ref
        if always_allow_grant_ref:
            payload["alwaysAllowGrantRef"] = always_allow_grant_ref
        return gateway.invoke_tool(_headers(context_headers), {"tool": "calendar.create_event", "input": payload})

    @mcp.tool(name="boards.comment")
    def boards_comment(
        context_headers: dict[str, str],
        task_ref: str,
        body: str,
        approval_receipt_ref: str | None = None,
    ) -> dict[str, Any]:
        payload = {"taskRef": task_ref, "body": body}
        if approval_receipt_ref:
            payload["approvalReceiptRef"] = approval_receipt_ref
        return gateway.invoke_tool(_headers(context_headers), {"tool": "boards.comment", "input": payload})

    return mcp


def main() -> None:  # pragma: no cover - optional runtime adapter
    mcp = build_fastmcp()
    try:
        mcp.run(transport="streamable-http")
    except McpDenied:
        raise


if __name__ == "__main__":  # pragma: no cover
    main()
