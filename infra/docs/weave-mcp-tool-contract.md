# Weave MCP runtime contract

Status: implemented as an internal, OIDC-protected Spring AI 2.0 stateful Streamable HTTP service with standard form elicitation.

The runnable MCP projection is `weave-mcp-server`. OpenTofu deploys it as `weave-mcp-server` on the internal Weave network through `01-infrastructure/modules/mcp`. The earlier `infra/weave-mcp` Python/FastMCP gateway and handwritten Java JSON-RPC controller are removed; they are not compatibility paths.

## Authority boundary

- Spring Security validates issuer, audience, expiry, and the `weave:workspace` scope at `/mcp`.
- `weave-backend` remains authoritative for RuntimeProfile lookup, capability intersection, authorization, approval receipts, audit, canonical domain commands, and provider selection.
- The MCP process forwards the same bearer identity to the backend and cannot call a provider adapter.
- MCP exposes governed actions for approved runtimes; it does not replace backend APIs.
- Normal members never configure raw endpoints, secrets, provider credentials, or runtime policy through MCP.

The protocol catalog is a fixed canonical capability ceiling generated from `MemberMcpToolCatalog`; listing a tool is not authorization. Before every invocation, `WeaveServerClient` fetches the caller's backend-owned RuntimeProfile projection. A tool absent from that approved catalog fails before dispatch. The resource `weave://runtime/approved-tools` exposes only the runtime-approved, support-safe subset.

## Spring AI surface

The server uses the official `spring-ai-starter-mcp-server-webmvc` 2.0 runtime with `spring.ai.mcp.server.protocol=STREAMABLE`, a stateful WebMVC transport at `/mcp`, and standard form elicitation for approval-required writes.

Tools currently projected end to end:

- `files.search` and `files.read` through the canonical Files service and WebDAV-backed projection;
- `calendar.search_events` and `calendar.create_event` through the canonical Calendar service and CalDAV-backed projection;
- `chat.send_message` through `ChatDomainFacadeService`, the canonical chat provider port, and the shared Matrix/Rust projection.

Resources and prompts:

- `weave://runtime/approved-tools` returns approved domain names, capabilities, and approval posture without runtime token or CredentialRef values;
- `weave.workspace.plan` creates a bounded prompt containing only approved Weave tool names and explicit approval constraints.

The full domain inventory remains in `../weave-workspace/weave-mcp-tool-contract.json`. Additional domains become executable only after their canonical backend port and conformance evidence exist.

## Security and audit

- Tool inputs use exact shared JSON schemas with `additionalProperties=false`.
- Unknown, ungranted, malformed, or unavailable calls fail closed with support-safe MCP results.
- Caller-supplied capability headers are never policy input.
- Read operations require a valid runtime grant.
- Write, delete, external-send, provider-switch, and admin-risk operations require a verifiable Weave approval receipt in the individual `tools/call` request's `weave/approvalReceipt` MCP `_meta` entry.
- The MCP adapter derives `approvalReceiptRef` from that receipt and sends both to the backend. An HTTP header or receipt reference alone cannot authorize a write.
- Receipt validation binds actor, current RuntimeProfile hash, canonical domain, exact tool, canonical scope refs, MCP contract version, backend policy version, approved decision, approval time, expiry, and audit ref. Profile, policy, scope, domain, or contract drift fails closed before canonical dispatch.
- The backend emits the authoritative audit result. MCP returns only its support-safe audit reference.
- SecretRef/CredentialRef handling stays backend-owned; values never appear in tools, resources, prompts, or errors.

Forbidden output includes bearer tokens, cookies, OAuth tokens, private keys, raw downstream bodies, raw provider errors, credential-bearing URLs, provider admin endpoints, raw Matrix/CalDAV/WebDAV payloads, and `openclaw.json`.

## Operations

The service is internal-only. Its host port is loopback-bound for operator health checks; Weaver runtimes use:

```text
http://weave-mcp-server:8091/mcp
```

Operator health is available at `http://127.0.0.1:${TF_VAR_mcp_host_port}/actuator/health`. The endpoint itself still requires OIDC; health reveals no tool, policy, user, or provider data.
