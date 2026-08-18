# Core test strategy

Status: active test ownership for the Files, Calendar, Chat, provider-portability, and Files/Calendar MCP core.

## Principle

Unit and contract tests cover edge cases. A small number of real boundary and system tests prove that the architecture works together. Compilation, mocks, fixture schemas, and controller reachability alone are insufficient.

## 1. Architecture

Owner: #1024.

Checks domain/application framework freedom, inward dependency direction, JPA/provider/protocol isolation, no provider leakage, no Chat MCP, and composition-only `weave-native` wiring.

Current command:

```bash
./gradlew coreArchitectureCi
```

## 2. Canonical data and transfer

Owner: #1012.

Checks typed identities, revisions, lifecycle, provenance, dependencies, transfer envelopes, checkpoints, deterministic digest/idempotency, all six fidelity classes, no conflicting classification, provider A to canonical to provider B movement, and duplicate-free retry after interruption.

Current command:

```bash
./gradlew canonicalDataCi
```

## 3. PostgreSQL persistence

Owner: #1320.

Checks clean Flyway migration, accepted-schema upgrade, checksum mismatch, concurrent initialization, Hibernate validation-only startup, canonical/JPA round trips, ordered journals, durable mappings/checkpoints, restart, and backup/restore.

PostgreSQL/Testcontainers is authoritative for locking, JSON, constraints, indexes, and concurrency. H2 cannot close this track.

## 4. Protocol facades

Owners: #1326, #1301, #1302.

Use real HTTP for accepted behavior.

- WebDAV: discovery, read/write, collections, move/copy, deletion, ETags, preconditions, locks, streaming, and errors.
- CalDAV: discovery, query, multiget, sync, DATE/FLOATING/UTC/ZONED, recurrence/overrides, ETags, and errors.
- Matrix: identity, room lifecycle, membership, transaction idempotency, timeline, sync, receipts, accepted relations/redactions, encrypted opaque envelopes, and explicit unsupported endpoints.

Every lane rejects provider IDs, URLs, credentials, raw errors, or storage references northbound.

## 5. Provider connector conformance

Owner: #1014.

A reusable deterministic source/target suite covers pagination, typed mapping, source version/provenance, preflight, idempotent apply, acknowledgement, readback, checkpoint resume, conflicts, permission impact, extension/archive preservation, all six fidelity classes, zero unaccounted data, and stable northbound behavior before/after transfer.

Ordinary core CI does not start real external providers.

## 6. Files/Calendar MCP

Owners: #1263 and #1415.

Checks stateless Streamable HTTP startup, deterministic Files/Calendar-only catalog, no Chat, JSON schemas/resources, OAuth resource/audience/client/scope validation, no bearer relay, typed WebDAV/CalDAV clients, DAV-to-MCP and MCP-to-DAV equivalence, restart/two-instance behavior, and no persistence/provider/IAM-admin dependency.

## 7. System E2E

Owner: #1412.

The disposable topology contains PostgreSQL, Keycloak and one-shot bootstrap, Flyway initialization, Weave Server, Weave MCP Server, Files blob storage, ingress/TLS where required, deterministic provider A/B fixtures, and one runner.

It must not require Nextcloud, Tuwunel/Synapse, MAS, Element, MinIO, a real cloud provider, Flutter, or a physical device.

Seven phases:

1. clean bootstrap;
2. source-provider import;
3. WebDAV, CalDAV, and Matrix proof;
4. Files/Calendar MCP equivalence;
5. canonical export/target transfer with interruption and resume;
6. process restart;
7. backup and isolated restore.

The result is valid only for the exact commit tested.

## Target command surface

```text
coreArchitectureCi
canonicalDataCi
postgresPersistenceCi
protocolFacadeCi
providerConnectorCi
mcpFilesCalendarCi
coreSystemE2e
```

`coreCheck` aggregates all required non-system-E2E gates. Task names must describe what they actually prove; permanent always-green compatibility contexts are prohibited.

## Negative tests

Fail on dependency inversion, duplicate canonical identity, stale revision/precondition, duplicate Matrix transaction, checkpoint without progress, acknowledgement mismatch, conflicting/unaccounted fidelity, cross-tenant access, invalid MCP audience/scope, bearer relay, provider/SecretRef leakage, external-provider contact, or restore drift.

## Evidence output

Allowed: exact commit, dependency/runtime coordinates, schema/model/transfer versions, sanitized service inventory, counts/hashes, checkpoint/retry summary, fidelity totals, and restore identifiers.

Forbidden: secrets, raw member content, provider payloads, private host paths, screenshots, and manual approval records.
