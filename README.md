# Weave

Weave is a collaboration data-sovereignty platform.

It gives organizations one provider-independent authority for **Files**, **Calendar**, and **Chat**. Weave exposes those domains through open standards, stores their canonical state independently of external providers, and supplies explicit import, export, reconciliation, and loss reporting so data can move without silently becoming provider-owned.

Weave is under active architectural convergence. The repository does not currently claim production readiness or complete protocol compatibility.

## Core architecture

```text
WebDAV             CalDAV / iCalendar          Matrix Client-Server
   |                        |                            |
   +--------------- northbound projections ------------+
                            |
                  canonical application use cases
                    Files | Calendar | Chat
                            |
                   canonical data authority
      IDs | revisions | journals | tombstones | provenance
        mappings | checkpoints | transfer and loss contracts
                       /                    \
       JPA / Flyway / PostgreSQL       provider connectors
       BlobStore / OpenDAL             import/export/reconcile
```

The same architecture in linear form:

1. WebDAV, CalDAV/iCalendar, and Matrix translate open-protocol requests into canonical Weave commands and queries.
2. Provider-independent domain and application code owns identity, authorization, lifecycle, revisions, synchronization, provenance, and transfer invariants.
3. JPA/Flyway/PostgreSQL and BlobStore/OpenDAL implement persistence ports. JPA entities are not domain objects.
4. External systems implement southbound source and target connector ports. Provider identifiers, URLs, DTOs, credentials, and raw errors remain inside those adapters.
5. `weave-native` means that the canonical application is composed directly with Weave persistence. It is not a second implementation of the domain.

## Core domains

### Files

Weave Files owns canonical hierarchy, identity, versions, hashes, permissions, locks, tombstones, and change history. The stable northbound data plane is WebDAV. Binary data is stored through a separate BlobStore port; a blob key is never a public Files identity.

Owner: [#1326](https://github.com/masssi164/weave/issues/1326)

### Calendar

Weave Calendar owns calendars, events, exact `DATE`, `FLOATING`, `UTC`, and `ZONED` time semantics, recurrence, exceptions, participants, versions, tombstones, and ordered synchronization. The stable northbound data plane is CalDAV with iCalendar.

Owner: [#1301](https://github.com/masssi164/weave/issues/1301)

### Chat

Weave Chat owns the canonical conversation and event ledger, membership, ordering, transaction idempotency, relations, receipts, redactions, tombstones, and opaque encrypted envelopes. The stable northbound data plane is a bounded Matrix Client-Server profile.

Owner: [#1302](https://github.com/masssi164/weave/issues/1302)

## MCP and Weaver

`weave-mcp-server` is a separate process. Its first stable catalog contains semantic **Files** and **Calendar** tools and resources only.

It accesses Weave Server through authenticated, typed WebDAV and CalDAV clients. It has no DataSource, JPA repository, BlobStore mount, provider adapter, or Keycloak administration authority.

There is no Chat MCP catalog. Weaver/OpenClaw communicates with Weave Chat through Matrix using the OpenClaw Matrix integration. This avoids a second conversational, authorization, and thread model.

Owners: [#1263](https://github.com/masssi164/weave/issues/1263) and [#1415](https://github.com/masssi164/weave/issues/1415)

## Data portability

The canonical transfer layer is part of the core architecture now, even though named production provider cutovers come later.

Every source object or field must be classified as one of:

- `PORTABLE`
- `LOSSY`
- `UNSUPPORTED`
- `MANUAL_REVIEW`
- `VENDOR_LOCKED`
- `ARCHIVE_ONLY`

The promise is **no unaccounted data loss**, not universal lossless conversion between arbitrary products.

Flyway schema version, canonical model version, transfer format version, and provider-adapter profile version are independent coordinates. A database backup or serialization of JPA entities is not a canonical export format.

Owners: [#1012](https://github.com/masssi164/weave/issues/1012) and [#1014](https://github.com/masssi164/weave/issues/1014)

## Current status

Implemented foundations exist for parts of the canonical domains, JPA/Flyway persistence, WebDAV, CalDAV, Matrix, OpenDAL, and the separate MCP process. The repository still contains historical package layouts, workflows, documentation, and confirmation logic from earlier architectures.

The active convergence therefore focuses on:

- enforcing dependency direction;
- completing the canonical transfer kernel;
- making PostgreSQL/JPA the sole durable metadata authority;
- finishing the three open-standard verticals;
- limiting MCP to Files and Calendar;
- replacing fixture and approval ceremony with executable protocol, connector, restart, and restore tests;
- moving the coherent foundation from `dev` to `main`.

There is no backward-compatibility promise for historical unreleased Weave APIs, tables, file/JSON stores, provider layouts, or deployment workflows.

Canonical roadmap: [#1299](https://github.com/masssi164/weave/issues/1299)

## Ordered roadmap

1. [#1024](https://github.com/masssi164/weave/issues/1024) — enforce domain, application, projection, persistence, and provider boundaries.
2. [#1012](https://github.com/masssi164/weave/issues/1012) — establish canonical identity, provenance, mappings, checkpoints, transfer envelopes, and loss contracts.
3. [#1320](https://github.com/masssi164/weave/issues/1320) — complete Flyway/JPA persistence adapters and recovery tests.
4. [#1326](https://github.com/masssi164/weave/issues/1326) — finish Files through WebDAV as the reference vertical.
5. [#1301](https://github.com/masssi164/weave/issues/1301) — finish Calendar through CalDAV/iCalendar.
6. [#1302](https://github.com/masssi164/weave/issues/1302) — finish Chat through Matrix Client-Server.
7. [#1263](https://github.com/masssi164/weave/issues/1263) and [#1415](https://github.com/masssi164/weave/issues/1415) — complete Files/Calendar MCP and cross-facade equivalence.
8. [#1014](https://github.com/masssi164/weave/issues/1014) — execute provider source-to-target no-drift conformance.
9. [#1304](https://github.com/masssi164/weave/issues/1304) and [#1306](https://github.com/masssi164/weave/issues/1306) — package the minimum standalone IAM and runtime topology.
10. [#1412](https://github.com/masssi164/weave/issues/1412) — prove the complete system from clean bootstrap through transfer, restart, backup, and isolated restore.
11. [#1307](https://github.com/masssi164/weave/issues/1307) — make those executable gates the DevOps truth and retire obsolete ceremony.

## Develop and test

Java 21 is required. Docker-compatible containers are required for PostgreSQL and full-stack integration tests.

Run the current architecture boundary:

```bash
./gradlew coreArchitectureCi
```

Run the canonical data and transfer foundation:

```bash
./gradlew canonicalDataCi
```

Run the existing focused server and MCP tests:

```bash
./gradlew :server:test :weave-mcp-server:test
```

The final single-command standalone topology and exact-commit E2E are being implemented under [#1306](https://github.com/masssi164/weave/issues/1306) and [#1412](https://github.com/masssi164/weave/issues/1412). Until those issues are green, README text or controller reachability is not evidence that the core is complete.

## Documentation

- [Core package boundaries](docs/architecture/core-package-boundaries.md)
- [Canonical transfer kernel](docs/architecture/canonical-transfer-kernel.md)
- [Canonical data-sovereignty architecture](docs/architecture/data-sovereignty-core.md)
- [Core development workflow](docs/development/core-workflow.md)
- [Core test strategy](docs/testing/core-test-strategy.md)
- [Documentation audit](docs/documentation-audit.md)

Historical sprint, dogfood, Candidate, release, provider-first, and UI plans are not architecture authority. They are being archived or removed under [#1416](https://github.com/masssi164/weave/issues/1416).

## Explicitly deferred

- production cutover between named providers;
- migration of historical Nextcloud or Tuwunel data;
- Home-core integration;
- Matrix federation and Calls/MatrixRTC;
- full client-owned Matrix E2EE lifecycle;
- Flutter and native OS distribution;
- TestFlight, public release, and broad human-signoff programs.

## License

No repository-wide open-source license is currently declared. An explicit license decision is required before public redistribution or external reuse is claimed.
