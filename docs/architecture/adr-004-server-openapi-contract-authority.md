# ADR-004: Server OpenAPI is the control-plane contract authority

Status: accepted for control-plane/generated contracts; superseded for normal Files, Calendar, and Chat data planes by the open-standard projection architecture.

## Context

Weave currently has several overlapping contract surfaces:

- backend domain facades exposed by `server`, including `/v3/api-docs` through Springdoc;
- hand-written shared Java metadata and DTOs in `weave-contract`;
- hand-written Flutter integration models and API clients;
- hand-written Admin Console API types;
- historical Java and Python MCP adapter experiments.

That split made short-term delivery possible, but it creates long-term drift risk. The latest live-stack evidence already showed validation drift at the product boundary: a Flutter live E2E request sent a `followUpRefs[0]` value rejected by server validation (`size must be between 0 and 160`). Similar drift would recur if client, admin, and MCP each keep their own hand-maintained contract truth.

Massimo's architecture decision is to make the backend server the canonical domain and policy authority, then generate control-plane consumer contracts from the server-owned OpenAPI description. Later architecture decisions narrowed the data-plane claim: OpenAPI/REST is not the normal Files, Calendar, or Chat data plane once the corresponding open-standard projection exists.

## Decision

The `server` owns Weave product domains, validation, authorization, approval, audit, provider boundaries, and support-safe error vocabulary.

The server-owned OpenAPI artifact is the canonical external contract for generated control-plane consumers:

- Flutter member client;
- Admin Console;
- MCP setup/control-plane adapter surfaces;
- release/live-stack readiness checks.

Normal collaboration data planes use open standard projections:

- Files: WebDAV under `/dav/files/**`.
- Calendar: CalDAV plus iCalendar under `/caldav/**`.
- Chat: Matrix Client-Server API projection, with Slack/Teams only as southbound bridge/provider adapters.
- Calls: Matrix v1.19 plus the pinned MatrixRTC Profile 0, with WebRTC media
  access behind an internal RTC Authorizer.
- Weaver/Agents: MCP over Weave domain capabilities.

`weave-contract` is no longer the place for new hand-written canonical domain truth. It is transitional and must either be removed or narrowed to generated/compatibility artifacts after consumers move to server OpenAPI.

OpenAPI-to-MCP conversion remains valid only for deny-by-default control-plane or generated-model surfaces:

- use stable `operationId` values as tool-name source;
- expose only explicitly allowlisted operations;
- exclude every unlisted route;
- prefer higher-level domain tools over raw one-endpoint-per-tool mirroring when workflows need multiple REST calls.

MCP tool annotations are UX/risk hints only. They are not enforcement. Approval, authorization, audit, rate limiting, validation, redaction, and destructive-action policy are enforced by the server or trusted gateway/middleware and rechecked by the tool implementation for sensitive actions.

## Consequences

- New client/admin/MCP control-plane work must not add parallel hand-written DTO truth when the server OpenAPI can describe the surface.
- Flutter remains feature-centered under `client/lib/features/<feature>/`. Generated OpenAPI DTOs belong in feature `data/` mappers or shared integration data, then map into feature-owned domain models and repository contracts before presentation/application code consumes them.
- Reusable client feature-adapter primitives may cover OpenAPI-backed resource pages, capability/readiness state, errors, and future realtime watch streams. They must stay small and must not erase feature-specific repository methods such as Chat message sending or Files folder mutation.
- Normal member Flutter surfaces consume Weave repositories over canonical standard projections or server control-plane APIs: `/dav/files` for Files, `/caldav` plus iCalendar for Calendar, Matrix Client-Server plus MatrixRTC Profile 0 for Chat and Calls, and `/api/*` for manifest/readiness/setup/revoke/admin/generated-model control-plane state. Provider SDKs and provider-native IDs remain behind server services or deliberately fenced diagnostic seams.
- OpenAPI quality becomes a build gate: stable `operationId`, stable schema names, validation constraints, support-safe errors, and no provider secret/raw payload leakage.
- The root build orchestrates all consumer checks from the repository root; it does not replace Flutter, npm, or Python tooling.
- Existing `weave-contract` usages remain compatibility debt until migrated. Follow-up PRs must move authority back into server/OpenAPI before deleting the module.
- Superseded by ADR-006/ADR-008 and the pinned corpus ADR-0003: `weave-mcp-server` is the active Spring AI MCP projection over canonical domain use cases. The Python/OpenAPI route-map path and handwritten JSON-RPC controller are removed.

## Migration plan

1. Land this ADR and root orchestration gates.
2. Harden server OpenAPI for the member/admin/MCP slices that consumers need.
3. Generate Admin Console client/types from OpenAPI first; it is the smaller consumer surface.
4. Generate Flutter member API client/models from OpenAPI and remove covered hand-maintained duplicates.
5. Rework MCP to expose domain-first Weave tools over approved domain capabilities; completed for the Files, Calendar, and Chat slice through Spring AI stateful Streamable HTTP and standard form elicitation. OpenAPI remains control-plane authority, not MCP tool truth.
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
