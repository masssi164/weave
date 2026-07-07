# ADR-007: Persistence Entity Strategy

Status: accepted

Date: 2026-07-07

Markers: ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION

## Context

#1012 requires Weave-owned strategic mutable state to move toward durable relational persistence without silently deleting the current JSON/file-backed stores. The pinned specification corpus keeps product/domain truth in canonical Weave domains; provider schemas remain adapter I/O and must not become source material for canonical entities.

The first safe implementation targets are Admin Console provider selections, product profile overrides, support-safe audit events, and migration run evidence. They are mutable Weave-owned state or append-only evidence streams, already fenced behind repository interfaces, and low-risk enough to prove read/write parity and restart recovery before wider cutover.

## Decision

Weave will introduce relational persistence behind explicit repository/storage gates:

- `weave.provider.selections.storage.mode=file` remains the default until parity, rollback, and dogfood migration notes exist.
- `weave.provider.selections.storage.mode=jdbc` enables the first Flyway-backed repository for provider selections.
- `weave.profile.storage.mode=file` remains the default until parity, rollback, and dogfood migration notes exist.
- `weave.profile.storage.mode=jdbc` enables the Flyway-backed repository for product profile overrides.
- `weave.audit.events.storage.mode=file` remains the default until audit parity, rollback, dogfood migration notes, and production/dogfood cutover evidence exist.
- `weave.audit.events.storage.mode=jdbc` enables the Flyway-backed publisher for support-safe audit-event persistence.
- `weave.migration.evidence.storage.mode=file` remains the default until migration evidence import, rollback, dogfood migration notes, and provider-switch no-drift proof exist.
- `weave.migration.evidence.storage.mode=jdbc` enables the Flyway-backed repository for support-safe migration run evidence.
- Flyway migrations are the schema baseline for Weave-owned tables.
- The first repository implementation uses Spring JDBC. JPA entity adoption is deferred until a bounded aggregate needs identity/lifecycle behavior that is clearer with entities than with direct repository mapping.
- Testcontainers PostgreSQL is added as the release-grade proof dependency, but H2-only local tests may prove local parity and restart behavior only. H2 proof must not be described as PostgreSQL production readiness.
- Canonical tables are handwritten from Weave domain contracts. Provider databases, exported provider schemas, generated provider DTOs, and downstream API payloads are adapter I/O only.

## Dependency and license posture

| Dependency | Use | License posture | Runtime posture |
| --- | --- | --- | --- |
| Flyway Core | Versioned schema migrations | Apache-2.0 | Adopted for gated relational persistence |
| Spring JDBC | Repository implementation | Apache-2.0 | Adopted for first low-risk repositories |
| PostgreSQL JDBC driver | Dogfood/production-compatible JDBC driver | BSD-2-Clause | Present for explicit JDBC mode |
| H2 | Local/test database only | MPL 2.0 / EPL 1.0 | Test runtime only |
| Testcontainers JUnit/PostgreSQL | PostgreSQL-compatible proof path | MIT | Test dependency, required before release-grade persistence claims |
| Spring Data JPA | Entity/aggregate mapping | Apache-2.0 | Deferred; no runtime adoption in this slice |

## Consequences

The first PRs can prove a relational baseline without changing production defaults. The audit-event slice adds a retry-safe idempotency contract: repeating the same tenant/idempotency event is a no-op, while conflicting reuse fails closed through `AuditRequiredException` instead of leaking raw database exceptions to callers. The migration-evidence slice adds durable restart/recovery proof for dry-run/apply-gate evidence while preserving the file-backed default and avoiding provider-switch cutover claims. Wider cutover still requires #1019: parity for each strategic store, import/backup/rollback operator notes, restart/recovery tests for provider selections, profile overrides, audit, and migration evidence, plus architecture checks that block new production JSON/file strategic stores without an explicit exception.

This decision advances #1012 and #1025 but does not close the persistence target by itself.

## Provider-switch no-drift foundation

The provider-switch no-drift foundation builds on #1019/#1025 without flipping defaults or mutating live providers. `POST /api/admin/providers/replacements/dry-run` now records a support-safe baseline snapshot from persisted provider selection and product profile override read models, publishes a redacted audit event, persists migration-run evidence for the dry-run comparison, and returns the switch plan plus read-model comparison in the Admin Control Plane response.

The evidence remains dry-run-only: persisted migration evidence omits `adminApprovalRef`, keeps `adminApproved=false`, and leaves production provider mutation/default changes blocked until a later issue supplies approval, rollback/archive, restore-smoke, operator cutover, and release-claim evidence. This is the `ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION` marker for #1025, not final provider-switch completion.
