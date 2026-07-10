# Weave canonical contract and Spring AI MCP

Status: active projection contract. The former handwritten Java JSON-RPC and Python/FastMCP transition paths are retired.

## Authority

- Canonical domain models and application use cases own Files, Calendar, Chat, and governed Weaver semantics.
- `weave-contract` currently carries the shared MCP catalog DTOs and exact JSON schemas consumed by both `server` and `weave-mcp-server`. It is a projection module, not a provider or product-domain authority.
- `server` owns RuntimeProfile policy, effective capability grants, approval enforcement, validation, canonical dispatch, provider selection, audit, and support-safe result projection.
- `weave-mcp-server` owns only the OIDC-protected Spring AI 2.0 stateful Streamable HTTP transport at `/mcp`, standard form elicitation, and MCP protocol projection.
- OpenAPI remains the control-plane/generated-model authority. MCP does not mirror OpenAPI routes.

## Active surface

The fixed protocol catalog ceiling contains:

- `files.search`
- `files.read`
- `calendar.search_events`
- `calendar.create_event`
- `chat.send_message`

The read-only resource `weave://runtime/approved-tools` reports the backend-approved subset for the current signed RuntimeProfile. The prompt `weave.workspace.plan` names only that approved subset. Listing a tool from the fixed catalog is not authorization; every call performs backend discovery again before dispatch.

Write-like tools call standard MCP form elicitation through `McpSyncRequestContext`. OpenClaw routes that elicitation through plugin approvals and returns bounded evidence; the trusted MCP boundary exchanges it for a short-lived, one-use Weave receipt. The receipt binds the actor, current RuntimeProfile hash, canonical domain and exact scopes, normalized arguments, exact tool, MCP contract version, backend policy version, decision time, expiry, and audit ref. Changed arguments, replay, foreign evidence, a reference, tool annotation, caller header, or prompt never grants authority.

## Deployment

The OpenTofu MCP module runs `weave-mcp-server` on the internal Weave network. Weaver runtimes connect to:

```text
http://weave-mcp-server:8091/mcp
```

The loopback host port is for operator health checks only. `/mcp` requires a valid OIDC bearer token with the configured issuer, audience, expiry, and `weave:workspace` scope, plus `X-Weave-Runtime-Profile` for governed discovery and invocation. Provider credentials and provider endpoints are never accepted by the MCP process.
