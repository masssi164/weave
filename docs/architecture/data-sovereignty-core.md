# Weave data-sovereignty core

Status: binding target architecture for the current Files, Calendar, Chat, and Files/Calendar MCP core.

Owner: [#1299](https://github.com/masssi164/weave/issues/1299)

## Mission

Weave gives organizations control over collaboration data by separating stable product meaning from access protocols, database mappings, blob infrastructure, and external providers.

The current core contains exactly three collaboration domains:

- Files
- Calendar
- Chat

Concrete provider cutovers are later work. Provider-independent identity, provenance, mappings, journals, checkpoints, transfer envelopes, reconciliation, and explicit fidelity outcomes are core work now.

## Architecture

```text
Northbound projections
  WebDAV          CalDAV/iCalendar          Matrix Client-Server
      \                  |                         /
       \                 |                        /
             canonical application use cases
                Files | Calendar | Chat
                         |
              canonical data authority
       IDs | revisions | provenance | tombstones
       mappings | journals | transfer checkpoints
                 /                     \
Persistence adapters                Provider connectors
JPA/Flyway/PostgreSQL               source/target APIs
BlobStore/OpenDAL                   import/export/reconcile
```

Linear description:

1. Northbound projections parse protocol requests and translate them into canonical commands and queries.
2. Application services authorize and orchestrate domain behavior.
3. Framework-free domains own business identity, meaning, lifecycle, revision, and invariants.
4. Persistence ports are implemented by JPA/Flyway/PostgreSQL and blob infrastructure adapters.
5. Provider source and target ports are implemented by anti-corruption connectors.
6. Boot/composition code selects concrete adapters without changing domain contracts.

## Canonical domain versus JPA entity

A canonical object describes product meaning. A JPA entity describes one relational persistence mapping.

Canonical types:

- contain no persistence annotations;
- contain no Spring type;
- contain no protocol wire type;
- contain no provider SDK type;
- use stable Weave identities;
- retain provider-independent lifecycle and revision semantics.

JPA entities:

- remain private to persistence adapters;
- map explicitly to and from canonical values;
- may contain database-specific representation details;
- never appear in WebDAV, CalDAV, Matrix, MCP, transfer, or provider connector contracts.

Flyway schema version and canonical model version are independent. A schema migration cannot silently redefine domain meaning.

## Persistence adapter versus provider connector

A persistence adapter stores canonical Weave state. A provider connector exchanges canonical state with an external system.

Examples:

- PostgreSQL JPA repository: persistence adapter;
- OpenDAL filesystem implementation: blob infrastructure adapter;
- future S3-backed BlobStore: blob infrastructure adapter;
- Nextcloud Files importer: provider source connector;
- Radicale Calendar exporter: provider target connector;
- Synapse or Tuwunel Chat importer: provider source connector.

S3 is not automatically a Files domain provider. It supplies blob semantics, not canonical hierarchy, permissions, locks, shares, versions, or WebDAV behavior.

## Native composition

`weave-native` means:

```text
canonical application service
    -> canonical repository port
    -> JPA persistence adapter
    -> configured blob infrastructure where required
```

It is a boot profile, not a second Files, Calendar, or Chat implementation. Application policy must not be duplicated inside a large native-provider adapter.

## Files vertical

Northbound:

- WebDAV

Canonical authority:

- stable node identity;
- hierarchy and paths;
- versions and content digests;
- rights and permission intent;
- locks and fencing;
- lifecycle, tombstones, and ordered changes;
- transfer and reconciliation state.

Southbound:

- JPA metadata repository;
- BlobStore port implemented first by OpenDAL filesystem;
- optional provider source/target connectors.

Blob references and provider object identifiers are private adapter data.

## Calendar vertical

Northbound:

- CalDAV;
- iCalendar.

Canonical authority:

- calendars and events;
- stable IDs and UID;
- exact `DATE`, `FLOATING`, `UTC`, and `ZONED` semantics;
- recurrence rules, dates, exceptions, and overrides;
- participants and resources in the accepted profile;
- versions, tombstones, change heads, and sync revisions;
- transfer and fidelity state.

Southbound:

- JPA persistence;
- iCal4j behind narrow codec and recurrence ports;
- optional provider source/target connectors.

iCal4j parses and serializes wire syntax. It does not own canonical temporal meaning or authorization.

## Chat vertical

Northbound:

- bounded Matrix Client-Server profile.

Canonical authority:

- conversations and rooms;
- membership;
- immutable ordered events;
- transaction idempotency;
- relations, receipts, reactions, edits, and redactions in the accepted profile;
- tombstones and sync heads;
- opaque encrypted envelopes and public routing metadata;
- transfer and fidelity state.

Southbound:

- JPA persistence;
- Ruma/JNI for protocol parsing and serialization;
- optional provider source/target connectors.

The Server never stores client private keys or silently converts encrypted content into plaintext.

## Transfer model

Each domain keeps typed canonical objects. Shared transfer primitives provide:

- canonical references;
- model and format versions;
- revisions and tombstones;
- provenance;
- private provider mappings;
- dependency links;
- resumable checkpoints;
- idempotency digests;
- conflicts and permission impact;
- bounded extension/archive references;
- fidelity findings.

Every unsupported source field or object receives one explicit outcome:

- `PORTABLE`
- `LOSSY`
- `UNSUPPORTED`
- `MANUAL_REVIEW`
- `VENDOR_LOCKED`
- `ARCHIVE_ONLY`

Silent omission is always a defect.

## MCP boundary

The separate MCP process contains Files and Calendar semantic tools and resources only.

```text
MCP client
  -> OAuth-protected weave-mcp-server
    -> typed WebDAV and CalDAV clients
      -> Weave Server northbound facades
        -> canonical application use cases
```

MCP contains no:

- Chat catalog;
- JPA or DataSource;
- BlobStore mount;
- provider connector or SDK;
- domain authority;
- Keycloak administration;
- generated raw DAV method catalog;
- OpenAPI route scraping.

Weaver/OpenClaw uses Matrix for conversation and MCP for Files/Calendar actions.

## Dependency direction

Allowed:

```text
projection -> inbound/application contract -> application -> domain
application -> persistence/provider/technical port
adapter -> port plus canonical values
boot -> contracts plus concrete adapters
```

Forbidden:

- domain to application, ports, projections, persistence, adapters, or frameworks;
- application to controllers, JPA entities, provider implementations, or wire DTOs;
- WebDAV, CalDAV, Matrix, or MCP directly to JPA or provider adapters;
- provider DTOs, identifiers, URLs, credentials, or raw errors northbound;
- persistence adapters inventing business semantics;
- native adapters duplicating application use cases.

The executable owner is [#1024](https://github.com/masssi164/weave/issues/1024).

## Version coordinates

The following versions are distinct:

- Flyway schema version;
- canonical domain-model version;
- canonical transfer-format version;
- provider-adapter profile version.

A PostgreSQL backup is a recovery artifact. A canonical export is a provider-independent portability artifact. Neither substitutes for the other.

## Acceptance

The architecture is accepted only when one exact commit proves:

- real WebDAV, CalDAV, Matrix, and Files/Calendar MCP behavior;
- canonical provider A import and provider B export/readback;
- interruption and idempotent resume;
- zero unaccounted data;
- process restart;
- application-consistent PostgreSQL/blob backup;
- restore into an empty namespace;
- no dependency on an external collaboration provider.

System owner: [#1412](https://github.com/masssi164/weave/issues/1412)
