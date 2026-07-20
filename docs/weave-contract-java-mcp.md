# Weave MCP implementation boundary

Status: **Guarded / dark**. The previous v1 member-oriented Java projection has been removed. There is deliberately no compatibility API.

## What exists

- `weave-mcp-server` packages Spring AI 2.0 stateful Streamable HTTP at `/mcp`.
- Spring Security validates OIDC bearer tokens, but the MCP path currently applies `denyAll` before protocol dispatch.
- Health and packaging remain available for operator checks; tool, resource, and prompt capabilities are disabled.
- The host port is loopback-bound. No human-facing product discovery advertises MCP.

## What was removed

- `MemberMcp*` DTOs and the `member-mcp-contract-v1` catalog;
- member-token admission and member-token exchange;
- `X-Weave-Runtime-Profile` as an authorization input;
- caller-supplied elicitation as approval authority;
- `/api[/v1]/workspace/weaver/**` runtime-profile, discovery, and invocation routes;
- the fake Weaver Scout response and UI;
- the old backend runtime, registry, dispatcher, bridge, and receipt classes.

## Replacement contract

ARC provisions one Keycloak confidential service-account client per Weaver cell. A future MCP authorization manager must resolve the authenticated workload to a server-owned `client -> cell -> organization -> immutable human owner -> RuntimeProfile v2` binding and revalidate current authorization on every operation. Humans never call MCP and human access tokens are never transported through Weaver or MCP.

Read discovery must be policy-derived and support-safe. Write-like tools additionally require signed, single-use ApprovalDecisionEvidence v2 and immutable ActionEvidence v2. V1 readers, fallback headers, static shared secrets, and generic service-account acceptance are forbidden.

See the pinned `weave-specs` corpus and `infra/docs/weave-mcp-tool-contract.md` for activation and operations gates.
