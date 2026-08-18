# ADR-008: No Transitional Compatibility as Architecture

Status: accepted

Date: 2026-07-07

Markers: ENTERPRISE_TARGET_NO_TRANSITIONAL_COMPATIBILITY

## Context

#1011 now targets direct implementation of the hard-plan architecture. The old
plan language could be read as preserving historical JSON/file runtime behavior,
OpenAPI data-plane authority, Python/OpenAPI-derived MCP truth, Chat API-first
shells, provider-shaped client/native surfaces, or broad package drift until a
large parity milestone.

That reading is wrong for the target architecture. Historical behavior may help
one focused import or fixture proof, but it is not a product compatibility
contract and must not force broad migration or adapter layers into the new
architecture.

## Decision

Transitional behavior is not architecture. Each vertical domain slice implements
the target projection first, then leaves the old path deleted, blocked, or
fixture-fenced as soon as the new projection covers that behavior.

The target surfaces are:

- Files: Weave-owned `/dav/files` WebDAV data plane.
- Calendar: Weave-owned CalDAV/iCalendar plus setup, status, and revoke control.
- People/Contacts: Weave-owned CardDAV/vCard plus setup, status, and revoke
  control over canonical People-domain contacts and address books.
- Chat: Weave-owned Matrix Client-Server core before any federation readiness
  claim.
- MCP: Spring AI semantic Weave tools over domain use cases with policy,
  approval, redaction, and audit.
- Flutter/native: Weave product repositories, Weave projections, and Weave
  control contracts only.
- OpenAPI: control, admin, setup, revoke, manifest, and generated convenience.
- Providers: southbound ports/adapters only.

Strategic JSON/file runtime truth is not preserved as the target. Strategic
runtime state has one JPA authority and no selectable file-store fallback. A
file-backed path may remain only when it is one of:

- a deterministic fixture used by tests or evidence;
- a dev-only local scaffold that production and dogfood profiles cannot select;
- a focused one-shot import source for real dogfood data, with backup,
  verification, rollback, and deletion/fencing evidence;
- a mounted cryptographic or policy `SecretRef`, which is configuration material
  and not an alternative persistence authority.

## Boundary rules

- Protocol packages must not call provider packages directly.
- Flutter, native setup payloads, MCP contracts, WebDAV, CalDAV, CardDAV,
  Matrix, support bundles, and member-visible settings must not expose provider
  URLs, provider credentials, bearer tokens, tenant IDs, SecretRefs, app
  passwords, downstream payloads, raw diagnostics, or provider DTOs.
- MCP must not become a raw OpenAPI route mirror or raw provider tool surface.
- OpenAPI must not define canonical Files, Calendar, People/Contacts, Chat,
  Matrix, MCP, provider-switch, or domain-kernel data-plane semantics once the
  target projection exists.
- Matrix federation must not be claimed until Matrix Client-Server core and the
  identity, signing, moderation, retention, E2EE, and support-safe gates are
  implemented and evidenced.

## Consequences

PRs should stay vertical: one target projection slice plus the removal, blocking,
or fixture fencing of the old path it replaces. Broad compatibility layers,
strategic legacy adapters, and provider-shaped member/native/MCP fallbacks are
rejected unless a focused issue proves a real dogfood one-shot import need.

Provider-switch/no-drift work remains important, but it is supporting evidence
for provider isolation and migration safety. It does not lead the architecture
sequence and must not preserve provider-shaped surfaces as product contracts.

## Evidence

The target-lock slice is proved by:

- #1011 issue plan update/comment;
- #1031 People/Contacts CardDAV target issue;
- `e2e/features/enterprise_target_architecture.feature`;
- `tools/enterprise_target_architecture_spine_check.py`;
- `./gradlew --no-daemon specCorpusConformance --console=plain`;
- `./gradlew --no-daemon acceptanceContract --console=plain`;
- `./gradlew --no-daemon docsCheck --console=plain`.
