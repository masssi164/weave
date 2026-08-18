# Native Files composition

Status: active transition contract for issue #1326.

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

Textual equivalent: WebDAV translates HTTP/XML into canonical Files commands and queries. Canonical Files behavior owns IDs, paths, versions, lifecycle, locking and integrity. JPA stores metadata. `BlobStorePort` stores private immutable bytes. Provider connectors later import or export canonical objects; they are not selected by WebDAV.

## Current transition

The current `FilesProviderPort`, `FilesFacadeService` and `WeaveNativeFilesAdapter` still combine more application and composition responsibility than the target architecture permits. They remain compatibility seams while behavior is moved into explicit Files application services.

`weave-native` means canonical Files application behavior composed with `JpaFilesAuthorityRepository` and a configured `BlobStorePort`. It is not a second Files domain. A future S3-compatible implementation belongs below `BlobStorePort`; S3 is not a parallel canonical Files provider.

A remaining known debt is that `CanonicalFileRecord` still carries a private storage reference. Issue #1326 removes that reference from the canonical domain surface as the JPA/blob binding is split into adapter-private persistence.

## Blob authority

`FilesystemBlobStore` is the initial OpenDAL-backed infrastructure adapter. It owns only bounded byte I/O through scoped opaque references:

- verify expected length and SHA-256 digest;
- publish immutable content through a private temporary key and same-backend rename;
- read, delete and inventory scoped blobs;
- enforce root containment, reject symlinks and use private filesystem permissions;
- expose no raw path or blob reference northbound.

Canonical/member paths are not blob keys. Changing the BlobStore implementation must not change canonical IDs, WebDAV paths, versions, permissions or transfer envelopes.

## Atomicity and reconciliation

PostgreSQL and blob storage do not share one ACID transaction. Files mutations therefore require durable operation intent, immutable blob publication, metadata activation and bounded reconciliation. Ambiguous partial outcomes never become silent success.

The current native adapter already verifies metadata/blob length and digest and deletes bounded orphan inventory. The next structural slices move this orchestration out of the adapter and bind intent, journal and outbox state transactionally to canonical metadata.

## Backup and restore contract

The executable recovery slice uses a quiesced canonical Files source and creates two private artifacts:

1. a custom-format PostgreSQL consistency dump without ownership or privilege records;
2. a contained private archive of the native BlobStore root.

It restores both artifacts into independent empty targets and then verifies through the Files port:

- the same canonical file ID, path and version;
- identical file bytes and content integrity;
- matching metadata and blob inventory with no orphan or inconsistent record;
- successful creation and reading of a new file after restore;
- no mutation of the original source environment.

The authoritative test is `CanonicalFilesBackupRestoreTest`. It uses the real schema initializer, Flyway/JPA repository, `FilesystemBlobStore`, `WeaveNativeFilesAdapter` composition and two independent PostgreSQL instances. It requires no Nextcloud, MinIO, Synapse/Tuwunel or external provider.

This is the persistence recovery primitive, not final WebDAV recovery acceptance. Real HTTP WebDAV reads after isolated restore remain part of the complete #1326 vertical and the final #1412 system E2E.

## Security

Access control is enforced before the BlobStore port. Backup artifacts are private, content is never emitted as support evidence, archive extraction rejects absolute paths, empty or dot segments, duplicates and symlink traversal, and restored files/directories receive private permissions where POSIX permissions exist.

## Fresh-start policy

No legacy provider import, dual write, compatibility reader, hidden provider adoption or historical unreleased Files-store compatibility is introduced by this recovery path.
