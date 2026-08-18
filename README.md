# Weave

Weave gives organizations control over their collaboration data.

It provides provider-independent **Files**, **Calendar**, and **Chat** domains, exposes them through open standards, persists a canonical Weave representation, and connects external providers through replaceable import/export adapters.

Weave is under active core reconstruction. It is not yet presented as a finished production collaboration server.

## What Weave is

Weave separates three concerns that historically became mixed together:

1. Open client protocols.
2. Canonical collaboration data and application behavior.
3. Storage technologies and external providers.

The product authority is the canonical Weave state. A provider may be an import source, export target, or synchronized projection, but its IDs, URLs, DTOs, and database schema are not the Weave contract.

The current core intentionally covers only:

- Files;
- Calendar;
- Chat.

Provider-specific production migration, Home-core integration, Calls, broad UI work, and public release operations come later.

## Core architecture

```text
Northbound projections
  WebDAV          CalDAV/iCalendar          Matrix Client-Server
      \                  |                         /
             canonical application use cases
                Files | Calendar | Chat
                         |
              canonical data authority
       IDs | revisions | provenance | tombstones
       mappings | journals | transfer checkpoints
                 /                     \
Persistence adapters                Provider connectors
JPA/Flyway/PostgreSQL               import/export/reconcile
BlobStore/OpenDAL                   external provider APIs
```

Textual equivalent: clients call Weave-owned WebDAV, CalDAV, or Matrix endpoints. Those protocol adapters call canonical application services. The services own identity, authorization, lifecycle, revisions, synchronization, and transfer rules. JPA/Flyway and BlobStore adapters persist canonical state. External providers remain behind separate source and target connector ports.

### Northbound standards

- Files: WebDAV.
- Calendar: CalDAV and iCalendar.
- Chat: a bounded Matrix Client-Server profile.

OpenAPI may remain for derived control, discovery, status, or generated convenience. It is not the Files, Calendar, Chat, portability, or MCP data-plane authority.

### Native operation

`weave-native` means that Weave serves the canonical state directly from its own persistence adapters. It is boot composition, not a second Files, Calendar, or Chat implementation.

OpenDAL is a BlobStore technology. iCal4j is calendar syntax and recurrence infrastructure. Ruma/JNI is Matrix protocol infrastructure. None of them defines a provider-independent domain.

### MCP and Weaver

The separate Weave MCP Server supports **Files and Calendar only**.

It exposes semantic tools and resources and reaches Weave Server through typed WebDAV and CalDAV clients. It has no JPA repository, DataSource, provider adapter, or Chat catalog.

Weaver/OpenClaw communicates conversationally through the Weave Matrix facade using the OpenClaw Matrix plugin. Chat is therefore not duplicated as MCP tools.

## Current status

The development line already contains substantial historical implementation, but several layers remain mixed and are being corrected before the current tree becomes `main`.

Foundation now being established:

- executable architecture boundaries for domain, application, projection, persistence, and provider code;
- provider-independent canonical IDs and transfer envelopes;
- resumable checkpoints, deterministic idempotency keys, and explicit loss accounting;
- Flyway/JPA as persistence adapters rather than domain authority;
- concise core CI and documentation entry points.

Still incomplete:

- complete canonical Files application and WebDAV conformance;
- complete canonical Calendar application and CalDAV conformance;
- complete canonical Chat ledger and Matrix profile;
- Files/Calendar MCP equivalence;
- PostgreSQL-backed transfer checkpoints;
- provider connector conformance for all three domains;
- exact-commit restart and backup/restore E2E.

No current statement implies named-provider cutover readiness, Matrix federation, complete client E2EE, Home-core replacement, or public production readiness.

## Ordered roadmap

The binding roadmap is [issue #1299](https://github.com/masssi164/weave/issues/1299).

1. [#1024](https://github.com/masssi164/weave/issues/1024): enforce architecture boundaries.
2. [#1012](https://github.com/masssi164/weave/issues/1012): canonical data and transfer kernel.
3. [#1320](https://github.com/masssi164/weave/issues/1320): Flyway/JPA persistence adapters.
4. [#1326](https://github.com/masssi164/weave/issues/1326): Files and WebDAV reference vertical.
5. [#1301](https://github.com/masssi164/weave/issues/1301): Calendar and CalDAV/iCalendar.
6. [#1302](https://github.com/masssi164/weave/issues/1302): Chat and Matrix Client-Server.
7. [#1263](https://github.com/masssi164/weave/issues/1263) and [#1415](https://github.com/masssi164/weave/issues/1415): Files/Calendar MCP.
8. [#1014](https://github.com/masssi164/weave/issues/1014): executable provider connector conformance.
9. [#1304](https://github.com/masssi164/weave/issues/1304) and [#1306](https://github.com/masssi164/weave/issues/1306): minimal standalone topology.
10. [#1412](https://github.com/masssi164/weave/issues/1412): complete system E2E.
11. [#1307](https://github.com/masssi164/weave/issues/1307): active CI and DevOps truth.
12. [#1416](https://github.com/masssi164/weave/issues/1416): documentation truth.

The single `dev` to `main` convergence path is [PR #1413](https://github.com/masssi164/weave/pull/1413).

## Develop and test

Use Java 21. Container tooling is required only for PostgreSQL, IAM, protocol, or full-system tests that actually start infrastructure.

Current foundation commands:

```bash
./gradlew coreArchitectureCi
./gradlew canonicalDataCi
./gradlew :server:test
./gradlew :server:postgresJpaTest
./gradlew :weave-mcp-server:test
```

The stable aggregate commands `coreCheck` and `coreSystemE2e` are introduced under #1307 as the focused gates become truthful.

Do not require Flutter, Node, MkDocs, Xcode, TestFlight, screenshots, or manual release evidence for unrelated Server/Data/MCP changes.

## Documentation

- [Data-sovereignty architecture](docs/architecture/data-sovereignty-core.md)
- [Package and dependency boundaries](docs/architecture/core-package-boundaries.md)
- [Canonical transfer kernel](docs/architecture/canonical-transfer-kernel.md)
- [Core development workflow](docs/development/core-workflow.md)
- [Core test strategy](docs/testing/core-test-strategy.md)
- [Documentation audit](docs/documentation-audit.md)

Historical documents are not architecture authority. Superseded entry points redirect to the active documents above.

## License

See [LICENSE](LICENSE).
