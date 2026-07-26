# Weave MCP workload boundary

Status: **Guarded / first read-only Files slice active**. The identity, admission,
token-exchange, and current-context path is implemented. `files.search` and the canonical
`weave://files/{canonicalFileId}` resource are active over the Weave WebDAV facade. This is not a
production-ready Weaver or autonomous-action claim.

## Identity and protocol contract

- MCP is a workload protocol surface, not a member API. Human access tokens, browser sessions,
  forwarded user tokens, generic service accounts, and the fixed `weave-mcp-server` account are
  invalid inbound cell identities.
- Each enabled Weaver cell receives its own confidential Keycloak workload client,
  `weaver-cell-{cellId}`, through Agent Runtime Control (ARC). The protected Compose/Keycloak
  reconciler owns the fixed realm baseline; ARC owns dynamic client creation, rotation, suspension, deletion, and restore
  reconciliation.
- The cell uses the MCP Client Credentials extension
  `io.modelcontextprotocol/oauth-client-credentials`. It presents a short-lived RFC 9068
  `at+jwt` access token with the exact MCP audience, the `weaver-runtime` role, `mcp:tools`, and
  only the domain scopes granted by its current RuntimeProfile.
- The edge publishes OAuth Protected Resource Metadata at
  `/.well-known/oauth-protected-resource/mcp`. Missing bearer tokens receive a discoverable
  challenge; initialization without the client-credentials extension fails closed.
- Before Spring AI protocol dispatch, the edge resolves the authenticated workload through
  `client -> cell -> organization -> immutable person owner -> current RuntimeProfile v2`.
  It uses Keycloak Standard Token Exchange V2 to mint a new exact-audience backend token and
  asks `weave-backend` to revalidate current entitlement, lifecycle, profile, policy, and domain
  scopes. The inbound token is never relayed downstream.

## What is active

- Spring AI 2.0 stateful Streamable HTTP at `/mcp`;
- RFC 9068 token-type, issuer, time, exact-audience, workload-role, and scope validation;
- protected-resource discovery and the MCP Client Credentials extension handshake;
- server-owned ARC binding and current backend context resolution;
- downscoped workload token exchange with no refresh or ID token;
- `files.search` through bounded WebDAV `SEARCH`, with provider-neutral structured output;
- exact canonical-ID resource resolution followed by a bounded WebDAV `GET`;
- negative rejection of human tokens, unbound service accounts, missing extension negotiation,
  missing scopes, upscope attempts, stale profiles, and direct workload access to admin routes.

## What remains guarded

The fixed canonical domain catalog is a capability ceiling, not an authorization grant. Only the
Files read slice is advertised. Further discovery may open only as the intersection of the catalog,
the current RuntimeProfile, current domain authorization, and runtime availability. Write-like
tools additionally require argument-bound, signed, single-use ApprovalDecisionEvidence v2 and
must emit immutable ActionEvidence v2. OpenClaw owns approval presentation and decision state;
caller-supplied MCP elicitation is never authority.

The removed v1 member runtime profile, `MemberMcp*` catalog, member-token exchange, caller header
binding, fake Scout surface, Python/FastMCP gateway, and handwritten JSON-RPC controller have no
compatibility readers.

The authoritative contracts are the pinned `weave-specs` Agent Runtime Control domain and
ADR 0012. The executable projection is
`infra/weave-workspace/weave-mcp-tool-contract.json`.
