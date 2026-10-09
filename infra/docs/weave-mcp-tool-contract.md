# Weave MCP workload boundary

Status: **Guarded / curated Files and Calendar slices active**. The identity, admission,
token-exchange, and current-context path is implemented. `files.search`, the canonical
`weave://files/{canonicalFileId}` resource, `calendar.agenda`, and the supported Calendar mutation tools consume the generated Weave User API. This is not a
production-ready Weaver or autonomous-action claim.

## Identity and protocol contract

- MCP is a workload protocol surface, not a member API. Human access tokens, browser sessions,
  forwarded user tokens, generic service accounts, and the fixed `weave-mcp-server` account are
  invalid inbound workload identities.
- The current implementation reuses an existing cell-bound workload client and ARC storage.
  The #1470 requirement is a protected workload-to-member/organization binding with current
  authorization, not dynamic client provisioning or a Cell lifecycle. Per-cell provisioning and
  lifecycle are not #1470 release gates.
- The workload may discover the MCP Client Credentials extension
  `io.modelcontextprotocol/oauth-client-credentials`. It presents a short-lived RFC 9068
  `at+jwt` access token with the exact MCP audience, the `weaver-runtime` role, `mcp.tools`, and
  only the domain scopes admitted for that workload.
- The MCP edge configuration names the bounded domain-scope ceiling. Admission requires
  `mcp.tools` and at least one configured domain scope, rejects unknown or repeated scopes, and
  exchanges only the admitted cell token's domain-scope subset. A Files or Calendar tool
  invocation narrows exchange further to its own domain. The exchange never adds a scope
  merely because the edge configuration permits it. The deployment ceiling contains
  `files.read`, `calendar.read`, and `calendar.write`; each request still requires its current member and Space grant.
- The edge publishes OAuth Protected Resource Metadata at
  `/.well-known/oauth-protected-resource/mcp`. Missing bearer tokens receive a discoverable
  challenge. An optional initialize capability marker does not grant authority, and its
  absence does not reject an otherwise valid, currently authorized workload bearer.
- Before Spring AI protocol dispatch, the edge resolves the authenticated workload to its
  protected organization/member binding. The existing implementation uses a Cell and
  RuntimeProfile v2 for that lookup. It uses Keycloak Standard Token Exchange V2 to mint a
  new exact-audience backend token and asks `weave-backend` to revalidate current entitlement,
  membership, policy, resource access, and domain scopes. The inbound token is never relayed
  downstream.

## What is active

- Spring AI 2.0 stateful Streamable HTTP at `/mcp`;
- RFC 9068 token-type, issuer, time, exact-audience, workload-role, and scope validation;
- protected-resource discovery and optional MCP Client Credentials extension advertisement;
- server-owned workload/member binding and current backend context resolution;
- downscoped workload token exchange with no refresh or ID token;
- `files.search` through the generated User list operation, with bounded traversal and
  provider-neutral structured output;
- exact canonical-ID resource resolution through generated User metadata and bounded download;
- `calendar.agenda` through generated User Calendar listing and agenda operations, with
  bounded output and no preview materialization;
- `calendar.create`, `calendar.update`, and `calendar.delete` through generated User Calendar
  operations and models, with current owner/admin capability, Space EDIT, idempotency or strong
  version preconditions, and the same provider mutation path as the User API;
- negative rejection of human tokens, unbound service accounts,
  missing scopes, upscope attempts, stale bindings, and direct workload access to admin routes.

## What remains guarded

Only the Files search/resource and Calendar read/write slices are active. The executable
projection lists exactly those tools and resources; it does not reserve Chat, Admin, identity,
audit, Files writes, or broader ARC capabilities. Each invocation still requires the current
workload grant and member/resource authorization. OpenClaw owns the existing approval
presentation and decision state for Calendar writes; caller-supplied MCP elicitation is never
authority, and Weave does not add a second approval engine. Live supported OpenClaw invocation
and provider readback remain required before #1479 can close.

The removed v1 member runtime profile, `MemberMcp*` catalog, member-token exchange, caller header
binding, fake Scout surface, Python/FastMCP gateway, and handwritten JSON-RPC controller have no
compatibility readers.

The current release contract is the pinned `weave-specs`
`steering/release-2026-10-product-consolidation.md`. The detailed Agent Runtime Control domain
and ADR 0012 describe the historical/later Cell profile; reuse of their existing workload
mechanisms does not make full ARC lifecycle a #1470 release gate. The release tool projection
is the annotated `weave-mcp-server` catalog; the broad ARC contract fixture is later scope.
