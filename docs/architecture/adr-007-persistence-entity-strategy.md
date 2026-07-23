# ADR-007: Relational Persistence Cutover

Status: accepted

Date: 2026-07-07

Last updated: 2026-07-22

Canonical authority: `WEAVE-ADR-0016` in the pinned Weave specification corpus

Markers: ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION, ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION

## Context

#1012 and #1019 require Weave-owned strategic mutable state to use one durable
relational authority. The earlier implementation slice proved parity while retaining
file/JDBC selectors. The accepted canonical Compose/JPA contract now closes that
transition: strategic file/JSON stores and selectable persistence implementations are
test fixtures or migration evidence only, never production runtime choices.

The pinned specification corpus remains product/domain truth. Provider schemas,
provider identifiers, generated provider DTOs, and downstream payloads stay inside
southbound adapters and do not define canonical entities.

## Decision

- `WeavePersistenceConfiguration` is the single production persistence composition root.
  Environment selection changes only the database: host `dev` uses H2 in PostgreSQL
  compatibility mode; integration, `dogfood`, and `main` use PostgreSQL.
- Spring Data JPA and the Spring-managed Hibernate `EntityManager` are the only
  production relational entry boundary. Repository interfaces remain owned by their
  domain/application ports. Native SQL needed for compare-and-swap, fencing, locks,
  append-only evidence, and outbox semantics is confined to named, reviewed adapter
  methods with static query text. The current allowlist contains only
  `OperationIntentLeaseNativeRepository` and `ChatCallbackClaimNativeRepository`.
  The former generic `JpaSqlExecutor`, direct Spring JDBC, and `java.sql` dependencies
  are rejected from production sources.
- Flyway Core is the sole schema authority. Hibernate uses `ddl-auto=validate`, Open
  EntityManager in View is disabled, and no release profile creates, updates, or drops
  schema objects through Hibernate.
- `JpaProviderSelectionRepository`, `JpaProductProfileOverrideRepository`,
  `JpaAuditEventPublisher`, `JpaMigrationRunEvidenceRepository`, device credentials,
  provider bindings, operation intents, Files authority, Chat authority, identity
  provisioning, and ARC control metadata all use the same relational composition.
- Strategic file/JSON and in-memory implementations live only under explicit test or
  evidence paths. Architecture tests reject their reintroduction into production.
- H2 is a development/test-only dependency and is excluded from the production Boot
  JAR. H2 proves fast mapping and behavior parity. H2 cannot satisfy a PostgreSQL or release claim.
- Testcontainers PostgreSQL executes migration, repository, retry, lock, and concurrency
  contracts. `dogfood` and `main` also fail startup unless their datasource URL and
  driver are PostgreSQL.
- RuntimeState ciphertext generations are external object-store data. PostgreSQL keeps
  only authority metadata. That capability remains `Guarded` in release profiles until
  the cross-store durable reconciliation/outbox evidence gate passes.

## Dependency and license posture

| Dependency | Use | License posture | Runtime posture |
| --- | --- | --- | --- |
| Flyway Core | Forward-only schema migrations | Apache-2.0 | Sole schema authority |
| Spring Data JPA / Hibernate | Managed relational boundary and entity mapping | Apache-2.0 / LGPL-2.1 | Production relational composition |
| PostgreSQL JDBC driver | Dogfood/main and integration database transport | BSD-2-Clause | Production runtime |
| H2 | Host-development PostgreSQL-compatibility loop | MPL-2.0 / EPL-1.0 | Development/test only; absent from Boot JAR |
| Testcontainers JUnit/PostgreSQL | Disposable PostgreSQL proof | MIT | Integration-test only |

## Consequences

There is no mixed-version dual persistence path. Repository restarts, idempotency,
support-safe error translation, and historical file-contract parity remain executable
tests, but production cannot select the old implementation. Deployment rollback restores
the coherent previous application and data snapshot; it does not reactivate a file store
or compatibility reader.

Audit-event retries with the same tenant/idempotency payload remain a no-op, while a
conflicting reuse fails closed. Migration evidence retains expiry and replacement rules.
Provider selection and product-profile state remain provider-neutral and do not leak
provider IDs into northbound contracts.

This decision completes the persistence composition portion of #1012/#1019. It does not
by itself promote provider portability, RuntimeState, or any domain capability to
`Ready`; those claims still require their own current evidence gates.

## Provider-switch no-drift foundation

The provider-switch no-drift foundation remains executable evidence for #1025.

`POST /api/admin/providers/replacements/dry-run` records a support-safe baseline from
the relational provider-selection and product-profile read models, publishes a redacted
audit event, persists migration-run evidence, and returns the switch plan plus canonical
read-model comparison.

The evidence remains dry-run-only: it omits `adminApprovalRef`, keeps
`adminApproved=false`, and performs no provider mutation. Apply, rollback, archive,
restore-smoke, and fidelity proof remain separate gates. This is the
`ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION` marker, not a completed provider
switch claim.
