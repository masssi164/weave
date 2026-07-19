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

Write-like tools call standard MCP form elicitation through `McpSyncRequestContext`. OpenClaw owns that approval lifecycle and returns bounded correlation evidence; Weave does not create a second approval workflow. The backend records short-lived, one-use action evidence that binds the effective member issuer and subject, organization, `weave-mcp-server` workload, delegated-token audience/correlation, entitlement revision, nonce, current RuntimeProfile hash, canonical domain and exact scopes, normalized arguments, exact tool, MCP contract version, backend policy version, decision time, expiry, and audit ref. Changed arguments, replay, foreign evidence, a reference, tool annotation, caller header, or prompt never grants authority.

## Deployment

The OpenTofu MCP module runs `weave-mcp-server` on the internal Weave network. Weaver runtimes connect to:

```text
http://weave-mcp-server:8091/mcp
```

The loopback host port is for operator health checks only. `/mcp` requires a real member OIDC bearer with the configured issuer, `aud=weave-mcp-server`, `azp=weave-app`, expiry, and `weave:mcp` scope, plus `X-Weave-Runtime-Profile` for governed discovery and invocation. Service-account/client-credentials subjects are rejected.

For each governed operation, the MCP process uses Keycloak standard token exchange (RFC 8693) as confidential client `weave-mcp-server`. It requests only `audience=weave-backend` and `scope=weave:mcp-backend`; the original member bearer, untrusted member/org headers, and the retired static boundary token are never relayed. The backend accepts that delegated scope only on its internal MCP bridge routes, rechecks `azp`, audience, scope, and non-service member subject, and remains the final domain authorization authority. Provider credentials and provider endpoints are never accepted by the MCP process.

Keycloak's experimental delegation feature is deliberately disabled. Keycloak's standard token exchange does not support the RFC 8707 `resource` parameter, and this slice does not expose a complete MCP authorization-server metadata surface. RFC 8707/full MCP authorization conformance is therefore **Guarded** pending a standards-complete provider/edge implementation; this slice claims only the documented audience-bound Keycloak token-exchange contract.
