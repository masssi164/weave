# Core test strategy

Status: historical foundation test inventory for #1012/#1024/#1299. Current #1470
release acceptance is specified by the pinned corpus and #1480, including generated
consumer freshness and real user/admin/MCP/Matrix journeys. Provider adoption and
migration tests belong to #1498. The commands below remain useful regression checks;
their success alone does not close either epic.

## Current October release responsibilities

The pinned release/delivery contracts govern current work. The existing entry points below
exercise distinct boundaries; similar operations at a generated-client, native UI or physical
surface remain separate proof. These implementation notes do not redefine acceptance.

| Responsibility | Existing entry point | Actual boundary | Evidence |
| --- | --- | --- | --- |
| Unit and module behavior | Focused Flutter tests and `testAppContract` | Pure decisions, state transitions, runner failure/cleanup and domain invariants | Test reports and bounded failure diagnostics; no running-product claim |
| Contract and security | `generatedApiContractCi`, scoped generated-consumer gates, Server decoder/security tests | Code-first export, serialization, real configured JWT decoder/validators, claims/client admission and domain authorization | Freshness/drift results and positive/negative boundary assertions; mocked principals alone do not prove decoding |
| Collaborating components | `postgresPersistenceCi`, relevant Server integration tests and `WEAVE_TEST_APP_RELEASE_MCP=true ./gradlew testApp` without a native runner | Real Spring/PostgreSQL/adapters/generated clients; imported Keycloak invitation, browser PKCE and permitted identity operations before native UI starts | Actual component/product protocol results, exact runtime/image/spec identity and cleanup; no native acceptance |
| Native product journeys | Protected Full Compose E2E selects `member-collaboration` with the shared versioned scenario and verified hosted artifacts | Two concurrent independent real iOS Flutter apps, ordinary-member PKCE, current shared-Space grants, Chat/Files/Calendar collaboration and recovery; macOS desktop is manual-only | Candidate/scenario/composition-bound native v3 result, per-action and all-seven scenario outcomes, private diagnostics and owned-resource cleanup |
| Physical preparation | Prepare Human Test consumes qualified hosted artifacts | Local signing, in-place iPhone install, trusted TLS, preserved owner state and observed retained workspace or normal sign-in | Candidate/run-attempt/composition-bound v4 receipt; owner HIL remains pending |
| Human acceptance | Explicit v2 human-result protocol | Real sign-in when needed, contextual usability, Chat/Files/Calendar and VoiceOver under the reported owner/admin/member role | Separately supplied human result; simulator, fixture and startup proof cannot manufacture acceptance |

The canonical scenario is `fixtures/member-client-baseline/v1.json`. Its adapters resolve logical
actors/content into each environment's private runtime bindings, verify current access and use
the real Weave User API and Matrix facade. They share semantic input and recipe digest, not a
mutable database. Separate setup/admin credentials never replace either ordinary product actor.

Development identity uses one generated canonical realm import. Ordinary restart preserves the
existing realm; new import JSON is not an incremental migration. Production retains separately
qualified restricted migration/backup/readback. Import-render provenance in readiness version 6
never substitutes for real authentication and identity-operation results. Use the affected direct
entry point to reproduce a regression before another complete native qualification.

The sections below preserve the earlier foundation inventory and deferred portability scope.

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

Current foundation command:

```bash
./gradlew postgresPersistenceCi
```

## 4. Protocol facades

Owners: #1326, #1301, #1302.

Use real HTTP for accepted behavior.

- WebDAV: discovery, read/write, collections, move/copy, deletion, ETags, preconditions, locks, streaming, and errors.
- CalDAV: discovery, query, multiget, sync, DATE/FLOATING/UTC/ZONED, recurrence/overrides, ETags, and errors.
- Matrix: identity, room lifecycle, membership, transaction idempotency, timeline, sync, receipts, accepted relations/redactions, encrypted opaque envelopes, and explicit unsupported endpoints.

Every lane rejects provider IDs, URLs, credentials, raw errors, or storage references northbound.

Until the focused lanes exist, `protocolFacadeFoundationCi` runs the current Server regression suite without claiming full protocol conformance.

## 5. Provider connector conformance

Owner: #1014.

A reusable deterministic source/target suite covers pagination, typed mapping, source version/provenance, preflight, idempotent apply, acknowledgement, readback, checkpoint resume, conflicts, permission impact, extension/archive preservation, all six fidelity classes, zero unaccounted data, and stable northbound behavior before/after transfer.

Ordinary core CI does not start real external providers. The current canonical coordinator proof runs under `canonicalDataCi`; `providerConnectorCi` is not introduced until the reusable domain contract exists.

## 6. Files/Calendar MCP

Owners: #1263 and #1415.

Checks stateless Streamable HTTP startup, deterministic Files/Calendar-only catalog, no Chat, JSON schemas/resources, OAuth resource/audience/client/scope validation, no bearer relay, typed WebDAV/CalDAV clients, DAV-to-MCP and MCP-to-DAV equivalence, restart/two-instance behavior, and no persistence/provider/IAM-admin dependency.

The current `mcpFoundationCi` runs the separate-process tests honestly. It does not claim Calendar MCP or cross-facade equivalence before those implementations exist.

## 7. System E2E

Owner: #1412.

The disposable topology contains PostgreSQL, import-initialized Keycloak, Flyway initialization, Weave Server, Weave MCP Server, Files blob storage, ingress/TLS where required, deterministic provider A/B fixtures, and one runner.

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

## Current command surface

```text
coreArchitectureCi
canonicalDataCi
postgresPersistenceCi
protocolFacadeFoundationCi
mcpFoundationCi
coreDocsCheck
coreCheck
```

Future focused tasks are introduced only when their tests exist:

```text
protocolFacadeCi
providerConnectorCi
mcpFilesCalendarCi
coreSystemE2e
```

Task names must describe what they actually prove; permanent always-green compatibility contexts are prohibited.

## Negative tests

Fail on dependency inversion, duplicate canonical identity, stale revision/precondition, duplicate Matrix transaction, checkpoint without progress, acknowledgement mismatch, conflicting/unaccounted fidelity, cross-tenant access, invalid MCP audience/scope, bearer relay, provider/SecretRef leakage, external-provider contact, or restore drift.

## Evidence output

Allowed: exact commit, dependency/runtime coordinates, schema/model/transfer versions, sanitized service inventory, counts/hashes, checkpoint/retry summary, fidelity totals, and restore identifiers.

Forbidden: secrets, raw member content, provider payloads, private host paths, screenshots, and manual approval records.
