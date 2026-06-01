from __future__ import annotations

from dataclasses import dataclass, field
import base64
import json
from typing import Any


class McpDenied(PermissionError):
    """Fail-closed MCP denial with a support-safe reason."""

    def __init__(self, reason: str, audit_ref: str = "audit://mcp/denied/support-safe"):
        super().__init__(reason)
        self.reason = reason
        self.audit_ref = audit_ref


@dataclass(frozen=True)
class RuntimeContext:
    org_id: str
    user_ref: str
    runtime_profile_hash: str
    token_ref: str
    capability_grants: frozenset[str]
    allowed_tools: frozenset[str]
    audit_ref: str

    @staticmethod
    def from_headers(headers: dict[str, str], configured_token: str) -> "RuntimeContext":
        auth = headers.get("authorization", "")
        if not configured_token or auth != f"Bearer {configured_token}":
            raise McpDenied("missing-or-invalid-runtime-token")
        org_id = headers.get("x-weave-org-id", "").strip()
        user_ref = headers.get("x-weave-user-ref", "").strip()
        profile = headers.get("x-weave-runtime-profile", "").strip()
        if not org_id or not user_ref or not profile:
            raise McpDenied("missing-runtime-org-user-or-profile")
        projection = _runtime_profile_projection(headers, profile)
        grants = frozenset(str(grant) for grant in projection.get("capabilityGrants", []))
        tools = frozenset(str(tool) for tool in projection.get("allowedTools", []))
        audit_ref = str(projection.get("auditRef", "audit://mcp/runtime-profile/support-safe"))
        return RuntimeContext(org_id, user_ref, profile, "credentialref://weave/runtime/short-lived", grants, tools, audit_ref)


def _runtime_profile_projection(headers: dict[str, str], runtime_profile_hash: str) -> dict[str, Any]:
    """Decode the support-safe RuntimeProfile projection used by the MCP gateway.

    The gateway intentionally does not trust caller-supplied capability headers as
    policy. A Weave-generated profile projection is the only source for MCP tool
    discovery/invocation decisions in this local RC evidence path.
    """

    raw = headers.get("x-weave-runtime-profile-projection", "").strip()
    if not raw:
        raise McpDenied("missing-runtime-profile-projection")
    try:
        padded = raw + "=" * (-len(raw) % 4)
        projection = json.loads(base64.urlsafe_b64decode(padded.encode("utf-8")).decode("utf-8"))
    except (ValueError, json.JSONDecodeError) as exc:
        raise McpDenied("invalid-runtime-profile-projection") from exc
    if not isinstance(projection, dict):
        raise McpDenied("invalid-runtime-profile-projection")
    if projection.get("runtimeProfileHash") != runtime_profile_hash:
        raise McpDenied("runtime-profile-hash-mismatch")
    if projection.get("enabled") is not True or projection.get("revoked") is True:
        raise McpDenied("runtime-profile-disabled-or-revoked")
    if projection.get("transport") != "streamable-http":
        raise McpDenied("unsupported-runtime-profile-transport")
    if projection.get("serverKey") != "weave-domain-tools":
        raise McpDenied("runtime-profile-server-binding-mismatch")
    return projection


@dataclass(frozen=True)
class ToolResult:
    data: dict[str, Any]
    audit_ref: str

    def support_safe(self) -> dict[str, Any]:
        return {
            "supportSafe": True,
            "rawProviderInternalsReturned": False,
            "credentialBearingUrlsReturned": False,
            "auditRef": self.audit_ref,
            **self.data,
        }


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    capability: str
    domain: str
    read_only: bool
    approval_required: bool
    description: str
    input_schema: dict[str, Any] = field(default_factory=dict)

    def discovery(self, granted: bool) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "enabledForRuntime": granted,
            "annotations": {
                "readOnlyHint": self.read_only,
                "destructiveHint": False,
                "openWorldHint": False,
            },
            "meta": {
                "domain": self.domain,
                "capability": self.capability,
                "transport": "streamable-http",
                "approval": "required" if self.approval_required else "not-required-for-read",
                "version": "v1",
            },
            "inputSchema": self.input_schema,
        }


def require_capability(ctx: RuntimeContext, capability: str) -> None:
    if capability not in ctx.capability_grants:
        raise McpDenied("capability-not-granted")


def require_tool_allowed(ctx: RuntimeContext, tool: str) -> None:
    if tool not in ctx.allowed_tools:
        raise McpDenied("tool-not-allowed-by-runtime-profile")


def require_approval(payload: dict[str, Any], action: str) -> str:
    receipt = str(payload.get("approvalReceiptRef", "")).strip()
    if not receipt.startswith("approval://"):
        raise McpDenied(f"approval-required-for-{action}")
    return receipt
