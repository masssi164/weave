# ADR-004: Server OpenAPI is the contract authority

Status: accepted

## Context

Weave currently has several overlapping contract surfaces:

- backend domain facades exposed by `server`, including `/v3/api-docs` through Springdoc;
- hand-written shared Java metadata and DTOs in `weave-contract`;
- hand-written Flutter integration models and API clients;
- hand-written Admin Console API types;
- Java and Python MCP adapter experiments.

That split made short-term delivery possible, but it creates long-term drift risk. The latest live-stack evidence already showed validation drift at the product boundary: a Flutter live E2E request sent a `followUpRefs[0]` value rejected by server validation (`size must be between 0 and 160`). Similar drift would recur if client, admin, and MCP each keep their own hand-maintained contract truth.

Massimo's architecture decision is to make the backend server the canonical domain and policy authority, then generate consumer contracts from the server-owned OpenAPI description.

## Decision

The `server` owns Weave product domains, validation, authorization, approval, audit, provider boundaries, and support-safe error vocabulary.

The server-owned OpenAPI artifact is the canonical external contract for generated consumers:

- Flutter member client;
- Admin Console;
- Python MCP adapter;
- release/live-stack readiness checks.

`weave-contract` is no longer the place for new hand-written canonical domain truth. It is transitional and must either be removed or narrowed to generated/compatibility artifacts after consumers move to server OpenAPI.

The MCP runtime direction is Python-first for the OpenAPI-consuming adapter. OpenAPI-to-MCP conversion is deny-by-default:

- use stable `operationId` values as tool-name source;
- expose only explicitly allowlisted operations;
- exclude every unlisted route;
- prefer higher-level domain tools over raw one-endpoint-per-tool mirroring when workflows need multiple REST calls.

MCP tool annotations are UX/risk hints only. They are not enforcement. Approval, authorization, audit, rate limiting, validation, redaction, and destructive-action policy are enforced by the server or trusted gateway/middleware and rechecked by the tool implementation for sensitive actions.

## Consequences

- New client/admin/MCP work must not add parallel hand-written DTO truth when the server OpenAPI can describe the surface.
- Flutter remains feature-centered under `client/lib/features/<feature>/`. Generated OpenAPI DTOs belong in feature `data/` mappers or shared integration data, then map into feature-owned domain models and repository contracts before presentation/application code consumes them.
- Reusable client feature-adapter primitives may cover OpenAPI-backed resource pages, capability/readiness state, errors, and future realtime watch streams. They must stay small and must not erase feature-specific repository methods such as Chat message sending or Files folder mutation.
- Normal member Flutter surfaces consume canonical server feature APIs such as `/api/chat/*` and `/api/files/*`; provider SDKs and provider-native IDs remain behind server services or deliberately fenced diagnostic seams.
- OpenAPI quality becomes a build gate: stable `operationId`, stable schema names, validation constraints, support-safe errors, and no provider secret/raw payload leakage.
- The root build orchestrates all consumer checks from the repository root; it does not replace Flutter, npm, or Python tooling.
- Existing `weave-contract` usages remain compatibility debt until migrated. Follow-up PRs must move authority back into server/OpenAPI before deleting the module.
- Java `weave-mcp-server` remains a transitional adapter until the Python OpenAPI-consuming MCP path is implemented or the architecture is explicitly revised again.

## Migration plan

1. Land this ADR and root orchestration gates.
2. Harden server OpenAPI for the member/admin/MCP slices that consumers need.
3. Generate Admin Console client/types from OpenAPI first; it is the smaller consumer surface.
4. Generate Flutter member API client/models from OpenAPI and remove covered hand-maintained duplicates.
5. Rework Python MCP to consume server OpenAPI with explicit allowlist route maps and fail-closed approval behavior.
6. Remove or narrow hand-written `weave-contract` contract truth once no consumer depends on it as authority.
7. Update live-stack readiness so server health, OpenAPI, admin, MCP initialize/tools-list, and approval smoke checks run before Flutter E2E.

## Non-goals

- Do not mirror every REST endpoint as an MCP tool.
- Do not use OpenAPI vendor extensions or MCP annotations as the security boundary.
- Do not migrate Flutter, Admin Console, MCP, and `weave-contract` removal in one PR.
- Do not move provider-native payloads, secrets, or admin-only diagnostics into generated member contracts.

## Evidence

- Server already exposes OpenAPI via Springdoc and permits `/v3/api-docs`.
- Root Gradle already orchestrates cross-stack checks and can add architecture gates without replacing native toolchains.
- FastMCP OpenAPI integration supports route maps, explicit excludes, and operation-based naming; its own guidance treats raw OpenAPI conversion as bootstrapping rather than production mirroring.
- MCP tool annotations are documented as hints, not enforcement.
