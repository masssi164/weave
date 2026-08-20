# Native Files private blob-binding evidence

Status: qualification in progress for an incremental issue #1326 slice. This is not complete Files closure evidence and introduces no public contract or schema change.

## Scope under test

- `CanonicalFileRecord` contains canonical domain metadata only;
- `StoredFileRecord` is the persistence-port envelope that pairs canonical metadata with an opaque `BlobBinding`;
- the JPA adapter stores metadata and binding in the existing `weave_files_objects` row and transaction;
- the existing `storage_reference` column remains adapter-private, so this slice adds no Flyway migration;
- COPY allocates an independent binding, while MOVE and tombstoning preserve the binding already associated with each canonical object;
- absent or structurally unsafe persisted bindings fail closed before blob access and expose only support-safe errors.

The historical PR #1442 record remains in `native-files-closure.md`. This later slice closes only the adapter-private binding debt listed there; it does not revise or overstate that earlier evidence.

## Required executable evidence

Every head proposed for merge must prove:

```text
./gradlew :weave-files-core:test
./gradlew coreArchitectureCi
./gradlew :server:compileJava :server:compileTestJava
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.*'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepositoryPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.service.files.CanonicalFilesBackupRestoreTest'
```

The slice-specific tests prove:

- missing and unsafe bindings return `INVALID_BLOB_REFERENCE` without calling `BlobStorePort` or exposing the persisted value;
- COPY persists a binding distinct from its source;
- MOVE and tombstoned records retain their prior bindings;
- PostgreSQL replacement commits metadata and binding together and rolls both back when an activation loses an active-path race;
- backup and restore preserve the deployed JPA-plus-blob boundary.

Protected `native-providers`, `postgres-flyway`, PostgreSQL persistence, architecture and Full Compose checks remain required when this slice is proposed through a pull request.

## Exact-head record

The pull request comment must record the exact commit SHA and successful check URLs before merge. This file cannot truthfully self-reference the commit that contains it.

## Subsequent progress and remaining scope

The later [Native Files V7 durability evidence](native-files-v7-durability.md) records the
implementation of the change taxonomy, stream head, captured-high-water reader, coordinated
intent/plan/finalization transaction and plan-aware orphan protection that were still open when
this private-binding slice was written.

Issue #1326 still requires provider connector round-trip conformance, full real-HTTP WebDAV
qualification, two-instance/restart proof and real-WebDAV equivalence after isolated restore.
