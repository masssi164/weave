# Weave MCP workload boundary

Status: **Guarded / dark**. The Spring AI 2.0 stateful Streamable HTTP transport is installed, but `/mcp` denies every caller until Agent Runtime Control (ARC) can validate the workload contract in the pinned specification corpus.

## Non-negotiable identity contract

- MCP is an AI/workload protocol surface. It is not a member-facing API.
- Human access tokens, member sessions, browser grants, forwarded user tokens, generic service accounts, and the fixed `weave-mcp-server` service account are not valid inbound Weaver cell identities.
- Each enabled Weaver cell receives its own confidential Keycloak workload client, `weaver-cell-{cellId}`, through ARC reconciliation.
- Authorization binds the authenticated workload subject and client to the server-owned cell, organization, immutable human owner, and exact RuntimeProfile v2 hash. Caller-supplied headers cannot create that binding.
- MCP and downstream access tokens have exact audiences, minimum scopes, short lifetimes, and are never relayed to a different audience.

The fixed OpenTofu Keycloak configuration is only the platform baseline. Dynamic per-cell clients, secret rotation, revocation, and restore reconciliation belong to ARC; they must not be modeled as an ever-growing OpenTofu client set.

## Current deployment

`weave-mcp-server` keeps the framework-native transport and OIDC resource-server support so health, packaging, and topology remain testable. Its deployment receives issuer, JWKS, and audience configuration, but no backend token-exchange secret while the edge is dark. Tool, resource, and prompt capabilities are disabled.

The old v1 member runtime profile, `MemberMcp*` catalog, member-token exchange, caller-driven elicitation approval, and backend member bridge routes were removed without compatibility readers.

## Activation gates

ARC may open the edge only after automated evidence proves all of the following:

1. per-cell client creation, rotation, revocation, cleanup, and restore reconciliation are idempotent;
2. issuer, signature, time, exact MCP audience, workload subject/client, cell binding, organization, profile v2, policy version, and current state are validated server-side;
3. discovery is the intersection of the fixed domain catalog, RuntimeProfile v2, current domain authorization, and runtime/tool availability;
4. writes require signed, single-use ApprovalDecisionEvidence v2 and produce immutable ActionEvidence v2;
5. human tokens and unbound service accounts remain rejected by negative tests;
6. support bundles, logs, metrics, and traces contain no client secret, bearer token, CredentialRef value, or provider payload.

The authoritative contracts are the pinned `weave-specs` corpus, especially Agent Runtime Control and ADR 0012.
