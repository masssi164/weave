from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from .schemas.common import McpDenied


@dataclass(frozen=True)
class OpenApiRoute:
    """Explicit MCP allowlist entry backed by a stable server OpenAPI operationId."""

    tool_name: str
    operation_id: str
    method: str
    path: str


# Deny-by-default: only these server-owned OpenAPI operations are exposed to the
# Python MCP adapter. Adding an OpenAPI route is intentionally not enough to make
# it discoverable as an MCP tool; the route must be reviewed and listed here.
OPENAPI_ROUTE_MAP: dict[str, OpenApiRoute] = {
    "admin.get_readiness": OpenApiRoute(
        tool_name="admin.get_readiness",
        operation_id="getAdminControlPlane",
        method="GET",
        path="/api/admin/control-plane",
    ),
    "weaver.get_runtime_profile_projection": OpenApiRoute(
        tool_name="weaver.get_runtime_profile_projection",
        operation_id="weaverRuntimeProfile",
        method="GET",
        path="/api/workspace/weaver/runtime-profile",
    ),
    "boards.comment": OpenApiRoute(
        tool_name="boards.comment",
        operation_id="weaverMcpToolInvoke",
        method="POST",
        path="/api/workspace/weaver/mcp/servers/{serverKey}/tools/{toolName}:invoke",
    ),
}


_PROJECT_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_OPENAPI_CONTRACT = _PROJECT_ROOT / "contracts" / "openapi" / "weave-openapi.json"


def load_openapi_contract(path: Path = DEFAULT_OPENAPI_CONTRACT) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        contract = json.load(handle)
    if not isinstance(contract, dict):
        raise ValueError("OpenAPI contract must be a JSON object")
    return contract


def _openapi_operation(contract: Mapping[str, Any], route: OpenApiRoute) -> Mapping[str, Any]:
    paths = contract.get("paths")
    if not isinstance(paths, Mapping):
        raise ValueError("OpenAPI contract is missing paths")
    path_item = paths.get(route.path)
    if not isinstance(path_item, Mapping):
        raise McpDenied(f"openapi-route-missing-for-{route.tool_name}")
    operation = path_item.get(route.method.lower())
    if not isinstance(operation, Mapping):
        raise McpDenied(f"openapi-method-missing-for-{route.tool_name}")
    return operation


def assert_route_map_matches_openapi(contract: Mapping[str, Any] | None = None) -> None:
    """Fail closed when an allowlisted MCP route drifts from server OpenAPI."""

    openapi = contract if contract is not None else load_openapi_contract()
    seen_tools: set[str] = set()
    for tool_name, route in OPENAPI_ROUTE_MAP.items():
        if tool_name != route.tool_name:
            raise McpDenied(f"openapi-route-tool-mismatch-for-{tool_name}")
        if tool_name in seen_tools:
            raise McpDenied(f"openapi-route-duplicate-tool-{tool_name}")
        seen_tools.add(tool_name)
        operation = _openapi_operation(openapi, route)
        if operation.get("operationId") != route.operation_id:
            raise McpDenied(f"openapi-operationid-drift-for-{tool_name}")


def require_openapi_route(tool_name: str) -> OpenApiRoute:
    route = OPENAPI_ROUTE_MAP.get(tool_name)
    if route is None:
        raise McpDenied("unknown-tool")
    return route
