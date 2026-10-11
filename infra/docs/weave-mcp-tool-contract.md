# Weave MCP workload boundary

Status: **Guarded / curated Files and Calendar slices active**. The identity, admission,
token-exchange, and current-context path is implemented. `files.search`, the canonical
`weave://files/{canonicalFileId}` resource, `calendar.agenda`, and the supported Calendar mutation tools consume the generated Weave User API. This is not a
production-ready Weaver or autonomous-action claim.

## Identity and protocol contract

- MCP is a workload protocol surface, not a member API. Human access tokens, browser sessions,
  forwarded user tokens, generic service accounts, and the fixed `weave-mcp-server` account are
  invalid inbound workload identities.
- The bounded release mode accepts a separately registered `weaver-mcp-*` workload, or a
  `weaver-cell-*` client issued through the existing guarded Keycloak registration policy
  without creating a Cell, and reads a
  private workload-to-member/organization binding on every Server call. A Cell, signed profile,
  dynamic client provisioning, and per-cell lifecycle are not #1470 release prerequisites.
  The earlier Cell path remains a separate later profile when release mode is not configured.
- The workload may discover the MCP Client Credentials extension
  `io.modelcontextprotocol/oauth-client-credentials`. It presents a short-lived RFC 9068
  `at+jwt` access token with the exact MCP audience, the `weaver-runtime` role, `mcp.tools`, and
  only the domain scopes admitted for that workload.
- The MCP edge configuration names the bounded domain-scope ceiling. Admission requires
  `mcp.tools` and at least one configured domain scope, rejects unknown or repeated scopes, and
  exchanges only the admitted workload token's domain-scope subset. A Files or Calendar tool
  invocation narrows exchange further to its own domain. The exchange never adds a scope
  merely because the edge configuration permits it. The deployment ceiling contains
  `files.read`, `calendar.read`, and `calendar.write`; each request still requires its current member and Space grant.
- The edge publishes OAuth Protected Resource Metadata at
  `/.well-known/oauth-protected-resource/mcp`. Missing bearer tokens receive a discoverable
  challenge. An optional initialize capability marker does not grant authority, and its
  absence does not reject an otherwise valid, currently authorized workload bearer.
- Before Spring AI protocol dispatch, the edge validates the authenticated workload and uses
  Keycloak Standard Token Exchange V2 to mint a new exact-audience backend token. The Server
  resolves its private binding and revalidates current entitlement, membership, policy,
  resource access, and domain scopes. The inbound token is never relayed downstream.

## Bounded release binding configuration

Set `WEAVE_MCP_RELEASE_BINDING_FILE` **only on Server** to an absolute path to a regular JSON
file. Its parent must not be group/world writable, and the file must be accessible to the
Server service account with mode `0600`; symlinks, malformed files and unavailable state fail closed. The
Server rereads it on every invocation, so an atomic file replacement can revoke a binding
without waiting for a process restart. Keep the file and its member identifiers out of source,
MCP edge configuration, logs and support bundles. A configured release file is authoritative:
an absent or revoked entry cannot fall back to a Cell.

The file has `schemaVersion: 1` and a `bindings` array. Each entry has `bindingRef`,
`workloadIssuer`, `workloadSubject`, `workloadClientId`, `organizationRef`, `personRef`,
`memberIssuer`, `memberSubject`, `expiresAt`, `allowedToolClasses`, and `active`.
`personRef` must be the Weave immutable account reference derived from member issuer and
subject. Tool classes are limited to `files.read`, `calendar.read`, and `calendar.write`.
Set the existing `weave.agent-runtime.entitlement.enabled` current-Keycloak observation
policy to `true` in the release installation, with explicit issuer/organization settings; the Server, not the MCP edge, uses its
existing private identity-admin credential to recheck membership and Weaver entitlement.
Calendar writes additionally require a current owner/admin role and Space EDIT access.

For the supported OpenClaw invocation, use the existing guarded Keycloak registration
policy to create a distinct `weaver-cell-*` confidential client without provisioning a
Cell, or pre-register an equivalently bounded `weaver-mcp-*` client. Require the exact
MCP audience, workload role and admitted scopes, then issue a
client-credentials token whose lifetime is at most 60 seconds. Hand that token to an
isolated OpenClaw process through a protected environment value; its `mcp.servers.weave`
entry uses `transport: "streamable-http"`, the Weave `/mcp` URL and
`headers.Authorization: "Bearer ${WEAVE_MCP_WORKLOAD_TOKEN}"`. Refresh the protected
value and reconnect before expiry; a cached or expired bearer must fail closed. Run
`openclaw mcp probe weave` and invoke Files/Calendar with provider readback for release
evidence. The OpenClaw configuration contains no member token, provider credential or
Keycloak admin credential. Automated client-credentials acquisition remains guarded.

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
