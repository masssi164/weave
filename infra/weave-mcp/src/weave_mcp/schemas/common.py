from __future__ import annotations

from dataclasses import dataclass, field
import base64
import binascii
import hashlib
import hmac
import json
from datetime import datetime, timezone
from typing import Any


GOVERNED_MCP_TOOL_ALLOWLIST = frozenset(
    {
        "admin.get_readiness",
        "weaver.get_runtime_profile_projection",
        "boards.comment",
    }
)


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
    runtime_token: str
    token_ref: str
    capability_grants: frozenset[str]
    allowed_tools: frozenset[str]
    audit_ref: str
    always_allow_grants: frozenset[str] = frozenset()

    @staticmethod
    def from_headers(
        headers: dict[str, str], configured_token: str, projection_hmac_secret: str
    ) -> "RuntimeContext":
        auth = headers.get("authorization", "")
        if not configured_token or auth != f"Bearer {configured_token}":
            raise McpDenied("missing-or-invalid-runtime-token")
        org_id = headers.get("x-weave-org-id", "").strip()
        user_ref = headers.get("x-weave-user-ref", "").strip()
        profile = headers.get("x-weave-runtime-profile", "").strip()
        if not org_id or not user_ref or not profile:
            raise McpDenied("missing-runtime-org-user-or-profile")
        projection = _runtime_profile_projection(headers, profile, projection_hmac_secret)
        grants = frozenset(str(grant) for grant in projection.get("capabilityGrants", []))
        tools = frozenset(str(tool) for tool in projection.get("allowedTools", []))
        audit_ref = str(projection.get("auditRef", "audit://mcp/runtime-profile/support-safe"))
        token_ref = str(projection.get("runtimeTokenRef", "")).strip()
        always_allow_grants = frozenset(str(grant) for grant in projection.get("alwaysAllowGrants", []))
        return RuntimeContext(org_id, user_ref, profile, configured_token, token_ref, grants, tools, audit_ref, always_allow_grants)


def _runtime_profile_projection(
    headers: dict[str, str], runtime_profile_hash: str, projection_hmac_secret: str
) -> dict[str, Any]:
    """Decode the support-safe RuntimeProfile projection used by the MCP gateway.

    The gateway intentionally does not trust caller-supplied capability headers as
    policy. A Weave-generated profile projection is the only source for MCP tool
    discovery/invocation decisions in this local RC evidence path. The
    projection must also carry a backend-generated HMAC signature so runtime
    token holders cannot self-grant tools by editing the projection body.
    """

    raw = headers.get("x-weave-runtime-profile-projection", "").strip()
    if not raw:
        raise McpDenied("missing-runtime-profile-projection")
    try:
        padded = raw + "=" * (-len(raw) % 4)
        projection = json.loads(base64.urlsafe_b64decode(padded.encode("utf-8")).decode("utf-8"))
    except (binascii.Error, UnicodeDecodeError, ValueError, json.JSONDecodeError) as exc:
        raise McpDenied("invalid-runtime-profile-projection") from exc
    if not isinstance(projection, dict):
        raise McpDenied("invalid-runtime-profile-projection")
    if projection.get("runtimeProfileHash") != runtime_profile_hash:
        raise McpDenied("runtime-profile-hash-mismatch")
    _verify_projection_signature(projection, projection_hmac_secret)
    audit_ref = str(projection.get("auditRef", "audit://mcp/runtime-profile/support-safe"))
    if projection.get("enabled") is not True or projection.get("revoked") is True:
        raise McpDenied("runtime-profile-disabled-or-revoked", audit_ref)
    _require_support_safe_fetch_by_hash(projection, runtime_profile_hash, audit_ref)
    if projection.get("transport") != "streamable-http":
        raise McpDenied("unsupported-runtime-profile-transport", audit_ref)
    if projection.get("serverKey") != "weave-domain-tools":
        raise McpDenied("runtime-profile-server-binding-mismatch", audit_ref)
    allowed_tools = projection.get("allowedTools", [])
    if not isinstance(allowed_tools, list) or any(str(tool) not in GOVERNED_MCP_TOOL_ALLOWLIST for tool in allowed_tools):
        raise McpDenied("runtime-profile-overbroad-tool-grant", audit_ref)
    return projection


def _require_support_safe_fetch_by_hash(projection: dict[str, Any], runtime_profile_hash: str, audit_ref: str) -> None:
    fetch_ref = str(projection.get("runtimeProfileFetchRef", "")).strip()
    if fetch_ref != f"weave-runtime-profile://{runtime_profile_hash}":
        raise McpDenied("runtime-profile-fetch-ref-mismatch", audit_ref)
    token_ref = str(projection.get("runtimeTokenRef", "")).strip()
    if not token_ref.startswith("credentialref://weave/runtime/"):
        raise McpDenied("runtime-token-ref-missing-or-unsafe", audit_ref)
    endpoint_ref = str(projection.get("endpointRef", "")).strip()
    if endpoint_ref != "internal://weave-mcp/streamable-http":
        raise McpDenied("runtime-profile-endpoint-ref-unsafe", audit_ref)
    if projection.get("rawEndpointExposed") is not False or projection.get("supportSafe") is not True:
        raise McpDenied("runtime-profile-support-safety-missing", audit_ref)
    expires_at = _parse_support_safe_instant(str(projection.get("expiresAt", "")), "runtime-profile-expired-or-stale", audit_ref)
    token_expires_at = _parse_support_safe_instant(
        str(projection.get("runtimeTokenExpiresAt", "")), "runtime-token-expired-or-stale", audit_ref
    )
    now = datetime.now(timezone.utc)
    if expires_at <= now:
        raise McpDenied("runtime-profile-expired-or-stale", audit_ref)
    if token_expires_at <= now or token_expires_at > expires_at:
        raise McpDenied("runtime-token-expired-or-stale", audit_ref)


def _parse_support_safe_instant(value: str, reason: str, audit_ref: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise McpDenied(reason, audit_ref) from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _verify_projection_signature(projection: dict[str, Any], projection_hmac_secret: str) -> None:
    supplied = str(projection.get("projectionSignature", "")).strip()
    if not projection_hmac_secret or not supplied.startswith("hmac-sha256:"):
        raise McpDenied("missing-runtime-profile-projection-signature")
    signed = dict(projection)
    signed.pop("projectionSignature", None)
    payload = json.dumps(signed, sort_keys=True, separators=(",", ":")).encode("utf-8")
    digest = hmac.new(projection_hmac_secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(supplied, f"hmac-sha256:{digest}"):
        raise McpDenied("invalid-runtime-profile-projection-signature")


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


def require_approval_or_scoped_always_allow(ctx: RuntimeContext, payload: dict[str, Any], action: str) -> str:
    """Require an ApprovalReceipt unless scoped persistent approval is present.

    The persistent path intentionally models the risky but allowed user choice
    "always allow". It is still scoped to the narrow action, auditable, and
    revokable by profile regeneration; broad grants such as "write calendar"
    are not accepted here.
    """

    receipt = str(payload.get("approvalReceiptRef", "")).strip()
    if receipt.startswith("approval://"):
        return receipt
    always_allow = str(payload.get("alwaysAllowGrantRef", "")).strip()
    expected = f"always-allow://weave/{action}/"
    if always_allow.startswith(expected) and always_allow in ctx.always_allow_grants:
        return always_allow
    raise McpDenied(f"approval-required-for-{action}")
