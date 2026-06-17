# Non-chat domain facade contract

Sprint 32 issue #788 defines the shared implementation contract for provider-neutral Files, Calendar, and Boards facades. Chat remains the strongest existing example, but non-chat domains must not be forced into one leaky mega-abstraction.

## Required boundary

Each canonical non-chat domain facade must:

1. Evaluate workspace capability and policy before any provider access.
2. Enforce Space/context authorization at the product boundary.
3. Emit audited write/delete decisions with actor, context, domain, operation, result, and support-safe diagnostics.
4. Return canonical object IDs and provenance/mapping references instead of provider-native IDs as the public product contract.
5. Use support-safe failure states such as `not_configured`, `degraded`, `policy_blocked`, `unavailable`, and `provider_failure` without exposing secrets, raw provider payloads, or internal endpoints.
6. Keep provider adapters behind server/domain seams; product callers and Weaver tools consume canonical domain APIs only.
7. Provide dry-run or preview hooks for replacement/mapping work before any destructive apply/cutover path exists.

## Domain-specific semantics stay explicit

The shared facade is a contract, not a generic data model. Files, Calendar, and Boards keep their own nouns, invariants, and tests:

- Files: drives, folders, files, versions, checksums, share/link policy, document sessions, and attachment refs.
- Calendar: calendars, events, recurrence, attendees, resources, reminders, meeting join grants, artifacts, and consent/retention refs.
- Boards: boards, lists, tasks, statuses, assignees, dependencies, labels, estimates, workflow rules, and decision/file/chat refs.

## Child implementation order

1. #789 Files: prove audited write/delete and provenance/mapping for file/folder operations.
2. #790 Calendar: prove event/meeting authorization, audit, recurrence-safe failures, and mapping hooks.
3. #791 Boards: prove board/task status/write parity and lossy mapping/conflict reporting.

## Review and evidence gates

- Architecture reviews the boundary and rejects over-generic abstraction.
- Server/Domain reviews child domain semantics and adapter isolation.
- Security reviews audit fields and support-safe errors.
- QA/Evidence owns contract tests and release evidence wording.
- DevOps/Ops reviews support bundle and deploy/readiness implications.
- Docs and Marketing/Product Messaging review claim hygiene: release wording may claim facade/contract parity only, not production provider replacement, until live replacement evidence exists.

Small PRs are required. A child PR should either satisfy a complete vertical slice for one domain operation family or explicitly name the unsupported remainder in tests/docs.

## Issue #815 integration: one canonical truth, scoped surfaces

The canonical contract vocabulary is the source of truth for Files, Calendar, Boards, readiness, and Weaver/MCP exposure. Domain-specific services remain the fachliche seams, but they must use the same canonical domain keys, capability names, operation names, object kinds, support-safe states, mapping refs, and audit metadata as `CanonicalDomainDefinition` / `CanonicalDomainContract`.

Role-specific surfaces are deliberately different:

- Member/client APIs expose canonical domain concepts only. They must not expose provider-native domains, raw provider IDs, raw provider payloads, raw endpoints, adapter class names, SecretRef values, or operational diagnostics that belong to admin/support.
- Weaver/MCP tools are a governed projection over the same canonical domain vocabulary. Backend `WeaverToolRegistry` and the infra MCP contract must validate non-chat domain tools against canonical domain keys such as `files-docs`, `calendar-meetings`, and `boards-tasks`.
- Admin/Weave-Control is the explicit control-plane surface for adapter assignment, selected provider mappings, readiness, provenance, SecretRefs, lossy replacement notes, and support-safe diagnostics. Admin visibility does not make those fields valid in member or MCP schemas.

No implementation may introduce a parallel `calendar-events`, `files_documents`, or `boards_tasks` tool/domain vocabulary for these canonical non-chat domains. Drift tests should fail when a member facade, Weaver tool registry, or infra MCP contract invents names outside the canonical contract.


## Contract-first Java MCP boundary

Member/Weaver-facing MCP DTOs, canonical domain identifiers, capability names, and tool metadata are owned by `weave-contract`. The backend consumes that metadata for governed Weaver tool discovery while remaining the authority for policy, authorization, audit, provider selection, and business logic. `weave-mcp-server` is a Java/Spring adapter over MCP JSON-RPC that exposes schemas from the shared DTO metadata and delegates invocation to `weave-server`; it must not call provider adapters directly or define an independent capability vocabulary. Admin/control-plane DTOs remain server-local.
