# Weave MCP implementation boundary

Status: **Guarded / workload boundary active**. The previous member-oriented v1 runtime was
removed without compatibility readers. The replacement admits only an ARC-bound cell workload;
the domain tool catalog remains empty until its authorization and evidence gates are complete.

## Implemented path

1. A dedicated `weaver-cell-{cellId}` Keycloak service account obtains a short-lived RFC 9068
   access token for the exact MCP resource through the MCP Client Credentials extension.
2. Spring Security validates token type, issuer, lifetime, exact audiences, workload identity,
   role, and required scopes before Spring AI sees the request.
3. `weave-mcp-server` exchanges that token with Keycloak Standard Token Exchange V2 for a new,
   short-lived, exact-audience backend token. It never forwards the inbound bearer.
4. `weave-backend` resolves the immutable service-account-to-cell mapping and revalidates the
   current entitlement, lifecycle, RuntimeProfile v2 hash, policy, and domain scopes.
5. Only then may the framework-native stateful Streamable HTTP transport initialize. The current
   catalogs contain no tools, resources, or prompts.

The edge publishes protected-resource metadata and a discoverable bearer challenge. Human tokens,
generic service accounts, the fixed MCP edge account, missing extension negotiation, scope
escalation, stale profiles, and direct workload calls to member/admin APIs fail closed.

## Removed code and contracts

- `MemberMcp*` DTOs and the `member-mcp-contract-v1` catalog;
- member-oriented admission, forwarded-member-token exchange, and caller-supplied profile headers;
- caller-supplied elicitation as approval authority;
- `/api[/v1]/workspace/weaver/**` runtime-profile, discovery, and invocation routes;
- the fake Weaver Scout response and UI;
- the old backend runtime, registry, dispatcher, bridge, and receipt classes;
- the Python/FastMCP gateway and handwritten Java JSON-RPC controller.

## Next activation gate

Read tools may be exposed only from the intersection of the canonical catalog, the current signed
RuntimeProfile, current product-domain authorization, and runtime availability. Write-like tools
also require signed, single-use, argument-bound ApprovalDecisionEvidence v2 and immutable
ActionEvidence v2. OpenClaw owns the native approval lifecycle; Weave remains the final
authorization and side-effect authority.

See the pinned `weave-specs` corpus and
`infra/docs/weave-mcp-tool-contract.md` for the normative and executable contracts.
