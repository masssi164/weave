# Weave data-sovereignty core

Status: binding architecture for the current Files, Calendar, Chat, and Files/Calendar MCP roadmap.

## Mission

Weave gives organizations data sovereignty over collaboration data.

Data sovereignty means that an organization can use stable open protocols, retain a provider-independent canonical representation, back up and restore it, import and export through connectors, and account explicitly for unsupported or lossy fields.

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
WebDAV        CalDAV/iCalendar        Matrix Client-Server
   \                 |                       /
        northbound protocol projections
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

Textual equivalent: open-standard projections translate requests into canonical commands and queries. Application services enforce authorization, revisions, idempotency, synchronization, and errors. Domain models express provider-independent meaning. Persistence adapters store canonical state. Provider connectors translate external state to and from canonical values.

Dependency direction points inward.

## Northbound projections

### Files

WebDAV is the stable Files data plane. XML, HTTP headers, paths, ETags, locks, and status codes stay in the projection. WebDAV calls canonical Files use cases, never JPA, OpenDAL, S3, Nextcloud, or another provider directly.

### Calendar

CalDAV and iCalendar are the stable Calendar data plane. Discovery, REPORT payloads, multistatus responses, ICS syntax, ETags, and sync tokens stay in the projection. iCal4j may sit behind narrow codec and recurrence ports; its types never enter the canonical domain.

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

Files content uses a BlobStore port. The initial adapter uses OpenDAL filesystem storage. A later S3-compatible BlobStore adapter may implement the same port without changing canonical identity or WebDAV behavior.

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

The Weave MCP Server is a separate process. Its v1 catalog contains Files and Calendar only and reaches Weave Server through typed WebDAV and CalDAV clients.

It contains no Chat catalog, DataSource, Flyway migration, JPA repository, BlobStore mount, provider adapter, Keycloak administration authority, or independent domain/approval workflow.

Weaver/OpenClaw converses through Matrix. Files and Calendar operations use MCP.

## Forbidden dependencies

Canonical domain and application packages must not depend on Spring/JPA, Jackson transport DTOs, WebDAV/CalDAV/Matrix/MCP wire types, OpenDAL/iCal4j/Ruma types, provider SDKs, controllers, projections, or JPA repositories.

Northbound projections must not call JPA or providers directly. Persistence adapters must not invent domain semantics. Provider details must not leak northbound.

## System acceptance

One exact commit must prove:

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

Issue #1412 owns this proof.

## Deferred

Named provider cutover, historical data migration, Home-core integration, federation, Calls, complete client E2EE, Flutter/native acceptance, TestFlight, and public release are outside the current core.
