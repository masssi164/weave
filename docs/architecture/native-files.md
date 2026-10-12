# Native Files composition

Status: active implementation contract for issue #1326.

## Boundary

Files is a provider-independent canonical domain. WebDAV is the stable northbound Files interface. PostgreSQL/JPA and BlobStore implementations are southbound persistence and infrastructure adapters.

```text
WebDAV projection
    -> Files application use cases
        -> canonical Files domain
            -> FilesAuthorityRepository
                -> JPA / Flyway / PostgreSQL
            -> BlobStorePort
                -> FilesystemBlobStore
                    -> OpenDAL filesystem
            -> provider source/target connector ports
```

Textual equivalent: WebDAV translates HTTP/XML into canonical Files commands and queries. Canonical Files behavior owns IDs, paths, versions, lifecycle, copying, moving, deletion, locking and content integrity. JPA stores metadata. `BlobStorePort` stores private immutable bytes. Provider connectors import or export canonical objects; they are not selected by WebDAV.

## Runtime composition

The native runtime now has one `FilesProviderPort` bean: `WeaveNativeFilesAdapter`. Despite its historical class name, it is only the Server boot and error-translation composition. It owns no Files algorithms.

It binds three framework-free application services:

- `CanonicalFilesQueries` for list, find, read, streaming and reconciliation;
- `CanonicalFilesCommands` for collection creation and content write/replace;
- `CanonicalFilesTreeCommands` for COPY, MOVE and DELETE.

All three services depend only on canonical Files types, `FilesAuthorityRepository`, `BlobStorePort` and, for commands, `Clock`. No Spring, JPA, OpenDAL, WebDAV, HTTP or provider type enters their contracts.

The previous second primary composition and BeanFactory post-processor have been removed. There is no longer a native mutation or query path outside the canonical application layer.

`weave-native` means canonical Files behavior composed with `JpaFilesAuthorityRepository` and a configured `BlobStorePort`. It is not a second Files domain. A future S3-compatible implementation belongs below `BlobStorePort`; S3 is not a parallel canonical Files provider.

## Identity and mutation semantics

Create and write use stable canonical IDs. Replacing content keeps the existing ID and publishes immutable bytes before activating metadata.

COPY creates independent deterministic IDs, verifies source size and SHA-256 before publication and leaves the source tree unchanged.

MOVE preserves IDs, substitutes canonical paths across the complete subtree and does not copy blob content.

DELETE tombstones complete subtrees and removes only blobs no longer referenced by active canonical metadata. Expected versions and overwrite rules fail closed before authoritative mutation.

JPA translates uniqueness, optimistic-lock and stale-state races at command-specific repository seams into provider-independent `ConcurrentMutationException`. Framework exceptions do not enter the application layer.

## Blob authority

`FilesystemBlobStore` is the initial OpenDAL-backed infrastructure adapter. It owns only bounded byte I/O through scoped opaque references:

- verify expected length and SHA-256 digest;
- publish immutable content through a private temporary key and same-backend rename;
- read, delete and inventory scoped blobs;
- enforce root containment, reject symlinks and use private filesystem permissions;
- expose no raw path or blob reference northbound.

Canonical/member paths are not blob keys. Changing the BlobStore implementation must not change canonical IDs, WebDAV paths, versions, permissions or transfer envelopes.

## Atomicity and reconciliation

PostgreSQL and blob storage do not share one ACID transaction. Files mutations therefore publish immutable content before metadata activation and retain ambiguous physical leftovers for bounded reconciliation rather than reporting silent success.

Canonical queries verify metadata/blob length and digest. Reconciliation compares active metadata with bounded blob inventory, reports inconsistent records and deletes unreferenced blobs.

`CanonicalFileRecord` contains only canonical domain metadata. The persistence-port envelope `StoredFileRecord` pairs that metadata with an opaque `BlobBinding`; JPA maps both into the same `weave_files_objects` row and transaction. The physical `storage_reference` column and its representation remain private to the persistence adapter, with no storage reference exposed through domain or public Files models.

The incremental qualification record is [Native Files private blob-binding evidence](../evidence/native-files-private-blob-binding.md).

The next durability slice binds canonical mutation, operation intent, change journal and outbox coherently. Until that contract is specified and implemented, publication and orphan cleanup are not a cross-instance transaction: reconciliation must not be represented as closing the race between an in-flight blob publication and another server's orphan scan.

## Backup and restore contract

The executable recovery slice uses a quiesced canonical Files source and creates two private artifacts:

1. a custom-format PostgreSQL consistency dump without ownership or privilege records;
2. a contained private archive of the native BlobStore root.

It restores both artifacts into independent empty targets and verifies through the Files port:

- the same canonical file ID, path and version;
- identical bytes and content integrity;
- matching metadata and blob inventory;
- successful creation and reading of a new file after restore;
- no mutation of the original source environment.

The authoritative persistence test is `CanonicalFilesBackupRestoreTest`. Real HTTP WebDAV equivalence after isolated restore remains part of the complete #1326 vertical and the final #1412 system E2E.

## Security

Access control is enforced before the BlobStore port. Backup artifacts are private, content is never emitted as support evidence, archive extraction rejects absolute paths, empty or dot segments, duplicates and symlink traversal, and restored files/directories receive private permissions where POSIX permissions exist.

## Fresh-start policy

No legacy provider import, dual write, compatibility reader, hidden provider adoption or historical unreleased Files-store compatibility is introduced by this implementation.
