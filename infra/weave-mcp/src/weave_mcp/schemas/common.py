from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


class McpDenied(PermissionError):
    """Fail-closed MCP denial with a support-safe reason."""

    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


@dataclass(frozen=True)
class RuntimeContext:
    org_id: str
    user_ref: str
    runtime_profile_hash: str
    token_ref: str
    capability_grants: frozenset[str]

    @staticmethod
    def from_headers(headers: dict[str, str], configured_token: str) -> "RuntimeContext":
        auth = headers.get("authorization", "")
        if not configured_token or auth != f"Bearer {configured_token}":
            raise McpDenied("missing-or-invalid-runtime-token")
        org_id = headers.get("x-weave-org-id", "").strip()
        user_ref = headers.get("x-weave-user-ref", "").strip()
        profile = headers.get("x-weave-runtime-profile", "").strip()
        grants = frozenset(
            grant.strip()
            for grant in headers.get("x-weave-capabilities", "").split(",")
            if grant.strip()
        )
        if not org_id or not user_ref or not profile:
            raise McpDenied("missing-runtime-org-user-or-profile")
        return RuntimeContext(org_id, user_ref, profile, "credentialref://weave/runtime/short-lived", grants)


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


def require_approval(payload: dict[str, Any], action: str) -> str:
    receipt = str(payload.get("approvalReceiptRef", "")).strip()
    if not receipt.startswith("approval://"):
        raise McpDenied(f"approval-required-for-{action}")
    return receipt
