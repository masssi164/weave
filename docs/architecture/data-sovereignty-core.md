# Weave data-sovereignty core

Status: historical Core architecture and implementation evidence. For the current release, the pinned corpus `steering/release-2026-10-product-consolidation.md` and #1470 govern the product boundary; #1498 governs provider adoption and migration. The historical northbound DAV and MCP-over-DAV assertions below are not current release requirements. Retain security, integrity, provenance, permission, and recovery requirements where the corresponding capability is enabled.

## Mission

Weave gives organizations data sovereignty over collaboration data.

Data sovereignty means that an organization can use stable product contracts, retain provider-independent resource references and authorization intent, back up and restore them, and account explicitly for unsupported or lossy provider fields. #1498 defines the concrete source inventory, transfer and rollback proof.

Weave does not promise universal lossless conversion. It promises no unaccounted data loss.

## Authority

Canonical Weave state is the product and data authority. It owns:

- canonical identities and scope;
- domain semantics and authorization intent;
- lifecycle, tombstones, and revisions;
- ordered change journals;
- provenance and private provider mappings;
- transfer checkpoints and idempotency;
- conflict, permission-impact, and fidelity outcomes.

Provider identities may map to canonical identities but never replace them.

## Layers

```text
Generated User API (Files/Calendar)    Matrix Client-Server (Chat)
                  \                         /
                  northbound projections
                       |
          canonical application services
             Files | Calendar | Chat
                       |
             canonical domain models
                 /                 \
 persistence ports             provider ports
      |                              |
 JPA/Flyway and BlobStore      source/target connectors
```

Textual equivalent: the generated Weave User API and the Matrix protocol facade translate requests into canonical commands and queries. Application services enforce authorization, revisions, idempotency, synchronization, and errors. Domain models express provider-independent meaning. Persistence adapters store canonical state. Provider connectors translate external state to and from canonical values.

Dependency direction points inward.

## Northbound projections

### Files (historical northbound DAV proposal)

The current Files northbound is the generated Weave User API. WebDAV may remain inside a southbound provider adapter. The earlier public WebDAV projection is historical protocol design, not a current member endpoint.

### Calendar (historical northbound DAV proposal)

The current Calendar northbound is the generated Weave User API. CalDAV/iCalendar may remain inside a southbound provider adapter or narrow codec; their types never enter the canonical domain.

### Chat

A bounded Matrix Client-Server profile is the stable Chat data plane. Matrix JSON, endpoint shapes, transaction IDs, sync tokens, and errors stay in the projection. Ruma/JNI handles bounded protocol parsing and serialization only. Federation and Calls are deferred.

## Canonical core

Each domain owns a typed model; there is no untyped collaboration super-entity.

Shared data-sovereignty primitives include canonical object ID, model version, object and stream revision, lifecycle, provenance, provider mapping, change journal, transfer run/checkpoint, dependency graph, conflict state, extension/archive payload, and fidelity classification.

Fidelity outcomes are:

- `portable`;
- `lossy`;
- `unsupported`;
- `manual_review`;
- `vendor_locked`;
- `archive_only`.

## Domain model versus JPA entity

A canonical object is framework-independent business meaning. A JPA entity is a relational representation and stays adapter-private.

JPA entities are not returned from application ports, protocol projections, MCP, provider connectors, or canonical transfer envelopes.

Flyway schema version, canonical model version, transfer format version, and provider-adapter profile version are independent coordinates.

## Persistence adapters

PostgreSQL stores canonical metadata and transfer state. Flyway owns schema evolution; Hibernate validates mappings in production-capable profiles.

Files content uses a BlobStore port. The initial adapter uses OpenDAL filesystem storage. A later S3-compatible BlobStore adapter may implement the same port without changing canonical identity or the member-facing User API.

Because PostgreSQL and blob publication are not one ACID transaction, Files mutations require durable operation intent, immutable publication, integrity verification, and reconciliation.

## Provider connectors

Provider connectors are not alternate application services.

A source connector discovers and reads bounded provider pages, maps them into typed canonical values, retains source versions privately, and advances resumable checkpoints.

A target connector preflights canonical batches, applies them idempotently, returns an opaque acknowledgement, reads the target back, verifies invariants, and reports every fidelity difference.

Provider DTOs, URLs, credentials, raw errors, and private references remain inside adapters. Named production provider qualification is deferred; deterministic test connectors prove the architecture first.

## Native composition

`weave-native` means canonical application services composed with canonical persistence adapters. It is not a second Files, Calendar, or Chat implementation.

A large native adapter that owns application policy, persistence, protocol, and provider choice must be decomposed.

## MCP and Weaver

The Weave MCP Server is a separate process. Its current curated Files and Calendar tools reach Weave Server through the same generated JVM User API client and transport models used by product E2E, with a distinct workload identity and current member/resource authorization. The earlier typed WebDAV/CalDAV transport is historical.

It contains no Chat catalog, DataSource, Flyway migration, JPA repository, BlobStore mount, provider adapter, Keycloak administration authority, or independent domain/approval workflow.

Weaver/OpenClaw converses through Matrix. Files and Calendar operations use MCP.

## Forbidden dependencies

Canonical domain and application packages must not depend on Spring/JPA, Jackson transport DTOs, WebDAV/CalDAV/Matrix/MCP wire types, OpenDAL/iCal4j/Ruma types, provider SDKs, controllers, projections, or JPA repositories.

Northbound projections must not call JPA or providers directly. Persistence adapters must not invent domain semantics. Provider details must not leak northbound.

## System acceptance

The historical Core program proposed that one exact commit prove:

1. empty-state startup and Flyway migration;
2. Files through WebDAV;
3. Calendar through CalDAV;
4. Chat through Matrix;
5. Files/Calendar equivalence through MCP;
6. provider A to canonical to provider B transfer;
7. interruption and idempotent resume;
8. zero unaccounted objects or fields;
9. restart;
10. backup and isolated restore;
11. no mandatory external collaboration provider.

Current #1470 acceptance is governed by #1480's real user/admin/MCP/Matrix/Files/Calendar journeys. Provider adoption, transfer, cutover, and rollback evidence belongs to #1498. The numbered list above remains historical context for #1412; it is not the #1470 closure checklist.

## Deferred

Named provider cutover and historical data migration moved to #1498. Home-core is not a Weave dependency. Federation, Calls, and broad Runner/orchestration work are deferred. E2EE is not complete until its separate client-owned encrypted-room/device/recovery/accessibility evidence passes. Flutter/native interoperability is active #1475/#1480 acceptance.
