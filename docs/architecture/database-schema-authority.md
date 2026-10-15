# Database schema authority

Weave-owned relational persistence has exactly one production schema authority: versioned Flyway SQL migrations.

## Boundary

The canonical Files, Calendar, and Chat domains remain provider-neutral. Their application services depend on persistence ports; JPA and PostgreSQL implement those ports without defining product meaning.

```text
canonical domain and application services
    -> canonical persistence ports
        -> JPA mappings
            -> PostgreSQL schema
                <- Flyway versioned SQL authority
```

Textual equivalent: canonical application code calls provider-independent repository, journal, and checkpoint ports. JPA maps canonical values to PostgreSQL. Flyway alone creates and evolves the physical schema. Hibernate validates mappings but never becomes schema or domain authority.

External provider databases such as Nextcloud or Matrix homeservers are outside this schema authority. No Weave Flyway migration imports, adopts, or rewrites a provider database.

## Production lifecycle

1. PostgreSQL and separate migration/serving roles are provisioned. The migration role derives from the existing backend username; its private password is mounted only into `schema-init`. Existing serving credentials remain unchanged.
2. The exact candidate `schema-init` acquires a bounded PostgreSQL session advisory lock scoped to the current database and schema.
3. Flyway validates immutable history. For an existing schema, its current physical catalog is compared with an independent clean replay of that prior migration version before any forward migration.
4. The reviewed chain executes in an exact UUID-owned scratch schema on the same pinned PostgreSQL instance to derive the expected catalog. Only that owned schema is removed, and cleanup failure blocks initialization.
5. Flyway applies forward migrations, validates history, and the initializer starts a non-web Spring context with Hibernate `validate`.
6. The resulting catalog must match the clean reviewed chain. The authority marker and runtime SELECT-only access to migration history/authority are established before receipt publication.
7. A support-safe receipt is written atomically. Closing the lock connection releases the advisory lock.
8. Normal Server startup independently verifies exact candidate, receipt, marker, live catalog and lack of runtime DDL capability before serving.

The advisory lock covers migration, Hibernate validation, marker mutation, fingerprint verification, and receipt creation. Flyway's own lock remains the DDL/history authority; the outer lock prevents two one-shot processes from racing after Flyway has released its migration lock.

The normal Server process does not own schema evolution. Deployment fails closed before application traffic when migration, checksum, mapping, catalog, marker, or receipt validation fails.

## Catalog fingerprint

The support-safe catalog projection is explicitly versioned as `weave.schema-catalog/v3`; the matching receipt is `weave.schema-init-receipt/v5`.

The fingerprint contains tables, ordered columns and type dimensions, nullability, defaults, primary and foreign keys with target schema/actions/deferrability, check and unique constraints, explicit indexes, owned views/materialized views, function signatures, sequences, enum/domain types and other relation kinds. Unexpected objects absent from the clean reviewed chain fail initialization. It excludes row data, secrets, Flyway history rows, object ownership, privileges, and provider databases.

PostgreSQL reparses check expressions during dump/restore and can move redundant casts between a literal text array and its string elements. The projection normalizes only those redundant casts on string literals and literal text arrays. Casts on columns and computed expressions remain fingerprinted, as do constraint names and literal values. Therefore semantically identical restored checks remain stable while real constraint drift still fails closed.

Serving accepts only the current receipt format. The initializer alone can requalify an existing v2 marker after validating its fingerprint, immutable Flyway history and independently replayed prior physical catalog. It emits only v3/v5 afterward. This bounded format transition preserves stale-marker rejection and legitimate forward upgrades without a second serving authority, migration edits or automatic baseline/repair.

## Migration policy

Migration files under `server/src/main/resources/db/migration` are immutable once accepted on the current mainline. Changes are forward-only. Destructive evolution uses explicit expansion/contraction migrations and restart-safe data backfills where required.

`baselineOnMigrate` is disabled. A non-empty schema without expected Flyway history is rejected instead of being silently adopted. Flyway `clean` is absent from every production-capable runtime path.

Schema version, canonical domain-model version, canonical transfer-format version, and provider-adapter profile version are independent coordinates. A PostgreSQL backup and a serialization of JPA entities are not canonical transfer formats.

## PostgreSQL qualification

PostgreSQL is authoritative for constraints, indexes, transaction ordering, locking, concurrent migration, JSON semantics, journals, mappings, and transfer checkpoints. H2 remains limited to fast tests that make no PostgreSQL-specific claim.

The active PostgreSQL contract proves:

- empty-schema migration followed by Hibernate validation;
- stable restart with zero new migrations;
- rejection of non-empty foreign schemas;
- rejection of catalog drift;
- rejection of modified applied-migration checksums before Hibernate starts;
- upgrade from the immediately previous resolved Flyway version;
- two concurrent initializers serialize across the complete one-shot operation;
- one authority marker and no duplicate successful migration version after concurrency;
- a private custom-format PostgreSQL dump restores into a separate empty PostgreSQL instance;
- source and restored semantic catalog fingerprints are equal despite PostgreSQL's redundant-cast redistribution;
- restored Flyway history, schema fingerprint, and authority receipt validate without new migrations;
- a canonical transfer checkpoint and fidelity outcome survive restore;
- the restored canonical transfer resumes and completes through `TransferRunRepository`;
- continuing the restored transfer does not mutate the original source database.

The recovery test uses the PostgreSQL client shipped by the exact source and target server images. It creates a consistent custom-format dump with ownership and privileges excluded, restores in one transaction with `pg_restore --single-transaction --exit-on-error`, runs the real schema initializer, and then exercises the canonical repository port. It is deliberately independent of the historical Compose backup manifest, Nextcloud, Synapse/Tuwunel, or provider-volume evidence paths.

This proves the relational half of canonical recovery. Files blob backup/restore and post-restore WebDAV, CalDAV, and Matrix equivalence remain owned by #1326, #1301, #1302, and the final #1412 system E2E.

## Fresh-start boundary

The Flyway baseline captures only Weave-owned relational state. It adds no legacy-provider import, dual writes, compatibility reader, hidden provider adoption, or historical unreleased-schema compatibility layer.
