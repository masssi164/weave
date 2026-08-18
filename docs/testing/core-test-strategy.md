# Core test strategy

This strategy tests Weave as one canonical data-sovereignty platform. It replaces confidence based on controller reachability, fixture schemas, copied evidence, or human confirmation rituals.

## Principles

- Test the owner of an invariant at the lowest useful level.
- Keep live-stack E2E sparse and cross-component.
- Use PostgreSQL for authoritative persistence, locking, and concurrency behavior.
- Use real HTTP for accepted WebDAV, CalDAV, Matrix, and MCP boundaries.
- Use deterministic provider fixtures in ordinary CI.
- Run named external providers only in later qualification lanes.
- Bind system evidence to the exact tested commit.
- Never log credentials, raw provider payloads, private content, or host-specific paths.

## Active gate model

```text
coreArchitectureCi
        |
canonicalDataCi
        |
postgresPersistenceCi
        |
protocolFacadeCi
   WebDAV | CalDAV | Matrix
        |
providerConnectorCi
        |
mcpFilesCalendarCi
        |
coreSystemE2e
```

The final task names may evolve while #1307 is implemented. The responsibilities and dependency direction are binding.

## Architecture tests

Owner: #1024

Prove:

- domain code has no framework, persistence, protocol, provider, or adapter dependency;
- application code depends only on domain and ports;
- projections call application contracts only;
- JPA entities remain private to persistence adapters;
- provider SDKs and DTOs remain private to provider connectors;
- MCP has no persistence/provider dependency and no Chat catalog;
- native boot wiring contains composition rather than duplicate use cases;
- core package slices are acyclic;
- transitional broad packages are explicitly inventoried and expire through open debt.

Architecture tests fail at compile/test time. Documentation alone is not an architecture boundary.

## Canonical data tests

Owner: #1012

Prove:

- stable canonical identity and positive revisions;
- lifecycle and tombstone semantics;
- ordered change-stream behavior;
- provenance and private provider mapping contracts;
- independent schema, model, transfer, and adapter versions;
- deterministic transfer envelope digest;
- resumable checkpoints and idempotency;
- dependency/reference preservation;
- all six fidelity outcomes;
- no unaccounted source object or field;
- no provider credential, endpoint, DTO, or raw error in canonical output.

The first executable proof uses deterministic provider A and B connectors with different capabilities.

## PostgreSQL and BlobStore tests

Owner: #1320, with Files-specific behavior in #1326

Use Testcontainers PostgreSQL for:

- clean Flyway migration;
- supported upgrade;
- checksum failure;
- concurrent initializer behavior;
- Hibernate validation-only startup;
- canonical-to-JPA-to-canonical round trips;
- revisions, journals, locks, and checkpoints under concurrency;
- coherent mutation, outbox, mapping, and audit state;
- backup and restore into an empty database.

For Files, additionally test:

- temporary blob write and digest verification;
- immutable publication;
- crash before and after metadata activation;
- operation-intent reconciliation;
- orphan and missing-blob detection;
- metadata plus blob backup/restore consistency.

H2 may provide fast local feedback but cannot close PostgreSQL locking, constraint, migration, or recovery acceptance.

## WebDAV tests

Owner: #1326

Use real HTTP against canonical PostgreSQL state and the real configured BlobStore adapter.

Cover the accepted profile:

- `OPTIONS`;
- `PROPFIND`;
- `GET` and `HEAD`;
- `PUT`;
- `MKCOL`;
- `DELETE`;
- `MOVE` and `COPY`;
- `LOCK` and `UNLOCK`;
- strong ETags;
- `If-Match` and `If-None-Match`;
- overwrite and lock-token rules;
- bounded search and streaming;
- standard-shaped errors;
- provider/blob-reference leakage negatives.

Concurrency and crash matrices remain focused integration tests rather than one oversized system scenario.

## CalDAV and iCalendar tests

Owner: #1301

Cover:

- service, principal, and calendar-home discovery;
- `PROPFIND` and `MKCALENDAR` where enabled;
- calendar-query and multiget `REPORT`;
- sync-collection;
- `GET`, `PUT`, and `DELETE`;
- ETags and conditional writes;
- valid multistatus and error XML;
- opaque Weave sync tokens;
- bounded request/response behavior.

Temporal fixtures include:

- `DATE`;
- `FLOATING`;
- `UTC`;
- `ZONED` with IANA TZID;
- all-day exclusive end;
- DST gaps and overlaps;
- DAILY, WEEKLY, MONTHLY, and YEARLY recurrence;
- interval, count, until, BY* rules, RDATE, EXDATE;
- moved and cancelled recurrence overrides;
- unsupported and invalid constructs.

Assert semantic equivalence rather than unstable textual ICS ordering where the standard permits equivalent serialization.

## Matrix tests

Owner: #1302

Use real HTTP for the accepted Client-Server subset:

- discovery and versions;
- authenticated identity;
- create, invite, join, and leave;
- membership reads;
- send with transaction ID;
- timeline and incremental sync;
- receipts;
- accepted relations, edits, reactions, and redactions;
- structured Matrix errors;
- explicit unsupported endpoints.

Prove:

- immutable ordering;
- transaction retry returns the original result;
- same transaction key with changed payload fails without mutation;
- two Server instances do not duplicate or lose events;
- sync high-water behavior survives restart;
- encrypted envelopes remain opaque;
- no Chat MCP catalog exists.

Client private keys, local crypto-store recovery, and physical multi-device UX are a later client lane.

## Provider connector contract tests

Owner: #1014

Every source/target connector reuses a parameterized conformance kit.

Cover:

- discovery and pagination;
- source version/cursor handling;
- typed canonical mapping;
- target preflight;
- idempotent apply and acknowledgement;
- target readback;
- reconciliation;
- transient and permanent failures;
- resume after interruption;
- collisions and conflicts;
- permission and identity impact;
- extension/archive preservation;
- all six fidelity classes;
- zero unaccounted data;
- support-safe diagnostics.

Ordinary CI uses deterministic provider A/B fixtures. Real Nextcloud, Radicale, Synapse, Tuwunel, or cloud services are later adapter qualification lanes.

## MCP tests

Owners: #1263 and #1415

Catalog tests prove:

- stateless MCP startup;
- exact Files/Calendar tool and resource allowlist;
- deterministic schemas and annotations;
- no Chat, raw DAV, provider setup, generic OpenAPI, prompt, sampling, completion, or elicitation surface.

Authentication tests prove:

- valid resource/audience/workload/scope path;
- missing, expired, wrong-issuer, wrong-resource, wrong-client, and insufficient-scope failures;
- failures occur before DAV/provider contact;
- downstream Weave token is exchanged and scope-reduced;
- inbound token is never relayed;
- secrets and tokens do not enter logs or errors.

Cross-facade equivalence uses real HTTP:

- WebDAV-created Files are visible through MCP;
- MCP Files writes are visible through WebDAV with the same canonical ID, ETag, and content digest;
- CalDAV-created events are visible through MCP;
- MCP Calendar writes are visible through CalDAV with the same ID, UID, ETag, time, recurrence, and sync behavior;
- restart and two-instance behavior remain stable.

Read equivalence must pass before write tools are promoted.

## Sparse system E2E

Owner: #1412

One exact-commit disposable scenario proves component composition:

1. clean PostgreSQL, blob, IAM, and fixture bootstrap;
2. provider A import into canonical Files, Calendar, and Chat;
3. representative WebDAV, CalDAV, and Matrix reads/writes;
4. Files/Calendar MCP discovery, reads, and promoted writes;
5. canonical export and provider B apply/readback;
6. injected interruption and idempotent resume;
7. Server and MCP restart;
8. application-consistent backup;
9. destruction and isolated restore;
10. representative protocol and MCP verification after restore.

The E2E also proves absent dependencies: no Nextcloud, Tuwunel/Synapse, MAS, Element, mandatory MinIO, or external cloud provider may run or be contacted.

## Evidence

Tests emit only bounded machine-readable diagnostics:

- exact source commit;
- runtime and dependency coordinates;
- schema, model, and transfer versions;
- sanitized service inventory;
- per-phase status;
- object counts and aggregate hashes;
- checkpoint and resume summary;
- fidelity totals and zero-unaccounted assertion;
- backup/restore identifiers.

Do not require screenshots, copied issue comments, prose attestations, marketing artifacts, or manual signoff when an executable test already owns the invariant.

## Change impact

A change to any of the following invalidates prior system evidence:

- canonical domain semantics;
- persistence mapping or migration;
- WebDAV, CalDAV, Matrix, or MCP behavior;
- connector mapping or transfer format;
- authentication or authorization boundary;
- Compose topology, image, or backup/restore path.

Run #1412 again on the exact candidate commit before mainline acceptance.
