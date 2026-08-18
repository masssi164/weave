# Core development workflow

This guide applies to the current Files, Calendar, Chat, portability, persistence, and Files/Calendar MCP core.

## Source line

`dev` is the current implementation line until the data-sovereignty foundation is promoted through the protected `dev` to `main` pull request.

Do not develop against historical `main` assumptions. Do not bypass protected checks or rewrite branch history to force convergence.

## Required tools

- Git
- Java 21
- the repository Gradle wrapper
- Docker-compatible containers for PostgreSQL and full-stack tests
- Rust toolchain only when changing the Matrix/Ruma boundary

Flutter, Node, MkDocs, Xcode, TestFlight, and physical devices are not prerequisites for ordinary Server/Data/MCP core checks.

## Issue order

The canonical implementation order is maintained in [#1299](https://github.com/masssi164/weave/issues/1299).

Work begins from the earliest unblocked owner:

1. architecture boundary: #1024;
2. canonical data/transfer kernel: #1012;
3. persistence: #1320;
4. Files/WebDAV: #1326;
5. Calendar/CalDAV: #1301;
6. Chat/Matrix: #1302;
7. Files/Calendar MCP: #1263 and #1415;
8. connector conformance: #1014;
9. IAM and standalone topology: #1304 and #1306;
10. exact-commit E2E: #1412;
11. DevOps cleanup: #1307.

Do not start a later issue by copying logic across a missing earlier boundary.

## Branch and pull-request flow

Use one focused branch and pull request per executable slice.

```text
dev
  -> architecture foundation
       -> canonical data foundation
            -> persistence or domain vertical
```

Stacked pull requests are allowed when a dependency is not merged yet. The PR body must name its base dependency and must be retargeted to `dev` after the prerequisite merges.

A PR must:

- link its owning issue;
- state what executable invariant it adds;
- state what it deliberately does not claim;
- provide the smallest reproducible validation command;
- avoid unrelated release, client, or historical cleanup;
- remain draft while an advertised required gate is absent or known failing.

## Adding canonical domain code

Canonical domain code belongs in a framework-free domain package or module.

It may use:

- Java standard-library value types;
- other canonical value types from an explicitly allowed dependency;
- domain-specific validation and behavior.

It may not use:

- Spring;
- JPA or Jakarta Persistence;
- Jackson wire annotations;
- WebDAV, CalDAV, Matrix, or MCP types;
- OpenDAL or iCal4j types;
- provider SDKs, URLs, credentials, or DTOs;
- controllers or generated API models.

## Adding application code

Application services expose explicit commands and queries and orchestrate domain behavior through ports.

Application code owns:

- authorization decisions using provider-neutral identity/context;
- canonical revisions and preconditions;
- transaction and operation intent;
- idempotency;
- error normalization;
- audit intent;
- transfer and reconciliation orchestration.

Application code may depend on domain types and port interfaces. It may not depend on concrete JPA repositories, controllers, protocol DTOs, provider adapters, or SDKs.

## Adding persistence

Define a repository, journal, checkpoint, outbox, or BlobStore port before adding an implementation.

The JPA adapter:

- owns its entities and Spring Data repositories;
- maps explicitly to and from canonical values;
- follows Flyway-managed schema;
- runs with Hibernate validation in production-capable profiles;
- keeps canonical mutation, journal, mapping, and checkpoint state coherent inside the accepted transaction boundary.

For Files, binary publication uses a durable operation-intent and reconciliation design because PostgreSQL and blob storage do not share one ACID transaction.

## Adding an open-standard projection

A WebDAV, CalDAV, or Matrix projection:

- parses and validates protocol input;
- maps authenticated context to an application command/query;
- calls only canonical inbound/application contracts;
- maps canonical output and errors back to the protocol;
- owns protocol headers, XML, JSON, status codes, and sync-token encoding;
- never selects or calls a provider adapter;
- never accesses JPA directly.

Use real HTTP protocol tests for accepted behavior.

## Adding a provider connector

Provider source and target connectors are anti-corruption adapters.

A connector may own:

- provider authentication and transport;
- provider pagination and cursors;
- provider DTOs and IDs;
- provider-specific retry and rate-limit behavior;
- translation to and from typed canonical values;
- private mapping and acknowledgement state.

A connector must not expose provider-native values through application or northbound contracts.

Every connector is tested with the reusable contract from #1014 before a provider-specific readiness claim.

## Adding MCP behavior

MCP supports Files and Calendar only.

Implement semantic tools and resources explicitly in `weave-mcp-server`. Do not generate a generic catalog from OpenAPI or raw DAV methods.

The MCP process:

- validates its own OAuth resource, audience, workload, and scopes;
- exchanges rather than relays the inbound bearer token;
- calls the Weave Server through typed WebDAV and CalDAV clients;
- contains no JPA, DataSource, provider adapter, BlobStore, or Chat tool;
- promotes write tools only after read equivalence and domain idempotency/precondition behavior are green.

## Local validation

Architecture foundation:

```bash
./gradlew coreArchitectureCi
```

Canonical data foundation:

```bash
./gradlew canonicalDataCi
```

Focused existing Server and MCP tests:

```bash
./gradlew :server:test :weave-mcp-server:test
```

Use the owning issue's focused test command while its replacement root gate is still being implemented.

Do not use `./gradlew ci` as proof of the new core merely because the historical umbrella happens to pass. Its responsibilities are being replaced under #1307.

## Exact-commit system validation

The final system test is owned by #1412 and must run from one documented command on one exact commit.

It starts from empty state and covers:

- Flyway and IAM bootstrap;
- provider A import;
- WebDAV, CalDAV, and Matrix operations;
- Files/Calendar MCP equivalence;
- provider B apply, interruption, and resume;
- process restart;
- backup and isolated restore.

A result from another commit is not transferable evidence.

## Cleanup rule

There is no backward-compatibility requirement for historical unreleased Weave state.

After a replacement path is green:

1. switch boot wiring to the canonical implementation;
2. remove dual writes and fallback reads;
3. delete obsolete configuration and profiles;
4. archive or delete contradictory documentation;
5. add a negative test preventing reintroduction.

Do not keep a retired runtime authority "just in case".
