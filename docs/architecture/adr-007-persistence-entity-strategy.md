# ADR-007: Persistence Entity Strategy

Status: superseded by `WEAVE-ADR-0016`

Date: 2026-07-07

Markers: ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION

## Context

#1012 originally introduced durable relational persistence beside historical JSON/file stores so
parity and restart recovery could be measured before cutover. That transitional state is complete:
the current Fresh architecture has one JPA authority for strategic runtime state. The pinned
specification corpus keeps product/domain truth in canonical Weave domains; provider schemas
remain adapter I/O and must not become source material for canonical entities.

The first safe implementation targets are Admin Console provider selections, product profile overrides, support-safe audit events, and migration run evidence. They are mutable Weave-owned state or append-only evidence streams, already fenced behind repository interfaces, and low-risk enough to prove read/write parity and restart recovery before wider cutover.

## Decision

This historical decision introduced relational persistence behind explicit repository/storage
gates. Its Spring JDBC implementation choice is superseded by the entity-first, portable JPA
contract in
`../../../weave-specs/architecture/adr/0016-java-spring-and-portable-jpa-persistence.md`.
The current implementation therefore uses:

- `jpa` as the only production storage mode for strategic Weave-owned state;
- explicit Jakarta Persistence entities and Spring Data repositories behind domain ports;
- compile-time MapStruct mapping where persistence and domain models differ;
- one consolidated Fresh Start Flyway baseline reviewed from the entity model;
- Hibernate `validate` for PostgreSQL-shaped runtime profiles;
- H2 only for fast development/unit feedback and PostgreSQL as the authoritative integration,
  migration, concurrency, dogfood, and production database;
- canonical relational shape authored in reviewed entities and domain constraints. Reviewed
  Flyway SQL is a deployment/migration artifact derived from that model, never a second
  database-first model. Provider databases, exported provider schemas, generated provider DTOs,
  and downstream API payloads remain adapter I/O only.

## Dependency and license posture

| Dependency | Use | License posture | Runtime posture |
| --- | --- | --- | --- |
| Flyway Core | Versioned schema migrations | Apache-2.0 | Adopted for gated relational persistence |
| Spring Data JPA / Hibernate | Entity persistence and repository implementation | Apache-2.0 / LGPL-2.1-or-later | Adopted behind domain ports |
| PostgreSQL JDBC driver | Dogfood/production database driver beneath JPA | BSD-2-Clause | Required for authoritative runtime profiles |
| H2 | Local/test database only | MPL 2.0 / EPL 1.0 | Test runtime only |
| Testcontainers JUnit/PostgreSQL | PostgreSQL-compatible proof path | MIT | Test dependency, required before release-grade persistence claims |
| MapStruct | Compile-time persistence/domain mapping | Apache-2.0 | Adopted for explicit boundary mapping |

## Consequences

The Fresh architecture completes the production cutover for strategic relational state. JPA
repositories now own provider selections, product-profile overrides, audit, migration evidence,
identity provisioning, Matrix/Chat state, device credentials and Agent Runtime Control. File
stores remain only where the accepted contract explicitly requires a mounted cryptographic or
policy `SecretRef`; they are not alternative strategic database authorities.

Idempotency, optimistic/pessimistic concurrency, restart recovery, constraints and migration
compatibility are exercised against authoritative PostgreSQL Testcontainers. H2 provides only
fast entity/repository feedback. Provider-switch execution remains independently evidence-gated;
the persistence cutover does not itself claim a live provider migration or rollback.

## Provider-switch no-drift foundation

The provider-switch no-drift foundation builds on the retirement and evidence work in
#1019/#1025 without mutating live providers. `POST /api/admin/providers/replacements/dry-run`
records a support-safe baseline snapshot from persisted provider selection and product profile
override read models, publishes a redacted audit event, persists migration-run evidence for the
dry-run comparison, and returns the switch plan plus read-model comparison in the Admin Control
Plane response.

The evidence remains dry-run-only: persisted migration evidence omits `adminApprovalRef`, keeps `adminApproved=false`, and leaves production provider mutation/default changes blocked until a later issue supplies approval, rollback/archive, restore-smoke, operator cutover, and release-claim evidence. This is the `ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION` marker for #1025, not final provider-switch completion.
