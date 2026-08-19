# Native Files composition

Status: active implementation contract for issue #1326.

## Boundary

Files is a provider-independent canonical domain. WebDAV is the stable northbound Files interface. PostgreSQL/JPA and BlobStore implementations are southbound persistence and infrastructure adapters.

```text
WebDAV projection
    -> FilesFacadeService
        -> canonical native Files composition
            -> CanonicalFilesQueries
            -> CanonicalFilesCommands
            -> CanonicalFilesTreeCommands
                -> FilesAuthorityRepository
                    -> JPA / Flyway / PostgreSQL
                -> BlobStorePort
                    -> FilesystemBlobStore
                        -> OpenDAL filesystem
```

Textual equivalent: WebDAV translates HTTP and XML into the existing Files facade contract. The native composition translates only support-safe boundary failures and delegates all data behavior to canonical Files queries and commands. Canonical Files behavior owns IDs, paths, versions, lifecycle, integrity, create/write, COPY, MOVE and DELETE rules. JPA stores metadata. `BlobStorePort` stores private immutable bytes.

Provider source and target connectors remain separate southbound transfer adapters. They import or export canonical objects and are never selected by the WebDAV projection.

## Current implementation

`FilesProviderPort` remains the temporary Server-facing facade used by the current WebDAV and service code, but it no longer owns native Files behavior. There is one native implementation bean: `CanonicalNativeFilesComposition`.

The composition directly owns:

- `CanonicalFilesQueries` for list, find, read, streaming integrity verification and bounded reconciliation;
- `CanonicalFilesCommands` for collection creation and content write or replacement;
- `CanonicalFilesTreeCommands` for COPY, MOVE and DELETE.

The former provider-shaped `WeaveNativeFilesAdapter`, its duplicate algorithms and its bean-priority workaround are removed. `weave-native` now means canonical Files application behavior composed with `JpaFilesAuthorityRepository` and a configured `BlobStorePort`; it is not a second Files domain.

A future S3-compatible implementation belongs below `BlobStorePort`. S3 is not a parallel canonical Files provider.

## Remaining structural debt

`CanonicalFileRecord` still carries a private storage reference. Issue #1326 removes that reference from the canonical domain surface by splitting canonical metadata from adapter-private blob binding.

The following responsibilities are not yet complete:

- durable Files change journal and stream head;
- transactional coordination of canonical metadata, journal, outbox and operation intent;
- provider source/target connector conformance and fidelity accounting;
- real-HTTP WebDAV equivalence after isolated PostgreSQL-plus-blob restore.

## Blob authority

`FilesystemBlobStore` is the initial OpenDAL-backed infrastructure adapter. It owns only bounded byte I/O through scoped opaque references:

- verify expected length and SHA-256 digest;
- publish immutable content through a private temporary key and same-backend rename;
- read, delete and inventory scoped blobs;
- enforce root containment, reject symlinks and use private filesystem permissions;
- expose no raw path or blob reference northbound.

Canonical paths are not blob keys. Changing the BlobStore implementation must not change canonical IDs, WebDAV paths, versions, permissions or transfer envelopes.

## Atomicity and reconciliation

PostgreSQL and blob storage do not share one ACID transaction. Canonical commands publish immutable content before activating metadata and treat canonical metadata as authority. Failed or superseded physical content remains visible to bounded reconciliation instead of becoming silent success.

`CanonicalFilesQueries.reconcile` verifies metadata/blob size and digest, reports inconsistent metadata and deletes bounded orphan inventory. The remaining transaction slice binds operation intent, journal and outbox state coherently to canonical metadata.

## Backup and restore contract

The executable recovery slice uses a quiesced canonical Files source and creates two private artifacts:

1. a custom-format PostgreSQL consistency dump without ownership or privilege records;
2. a contained private archive of the native BlobStore root.

It restores both artifacts into independent empty targets and then verifies through the direct canonical Files composition:

- the same canonical file ID, path and version;
- identical file bytes and content integrity;
- matching metadata and blob inventory with no orphan or inconsistent record;
- successful creation and reading of a new file after restore;
- no mutation of the original source environment.

The authoritative test is `CanonicalFilesBackupRestoreTest`. It uses the real schema initializer, Flyway/JPA repository, `FilesystemBlobStore`, direct `CanonicalNativeFilesComposition` and two independent PostgreSQL instances. It requires no Nextcloud, MinIO, Synapse/Tuwunel or external provider.

This is the persistence recovery primitive, not final WebDAV recovery acceptance. Real HTTP WebDAV reads after isolated restore remain part of the complete #1326 vertical and the final #1412 system E2E.

## Security

Access control is enforced before the BlobStore port. Backup artifacts are private, content is never emitted as support evidence, archive extraction rejects absolute paths, empty or dot segments, duplicates and symlink traversal, and restored files and directories receive private permissions where POSIX permissions exist.

## Fresh-start policy

No legacy provider import, dual write, compatibility reader, hidden provider adoption or historical unreleased Files-store compatibility is introduced by this implementation.
