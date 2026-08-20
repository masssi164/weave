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

It binds framework-free application services for queries, direct canonical commands, deterministic
durable-mutation planning, and idempotent blob effects:

- `CanonicalFilesQueries` for list, find, read, streaming and reconciliation;
- `CanonicalFilesCommands` for collection creation and content write/replace;
- `CanonicalFilesTreeCommands` for COPY, MOVE and DELETE;
- `CanonicalFilesMutationPlanner` for one immutable target set per operation;
- `CanonicalFilesBlobEffects` for receipt-verified, retry-safe PUT and subtree COPY effects.

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
- stream every publish, read, receipt and existing-key verification within the configured bound
  before allocation; require publish, receipt and existing-key verification to synchronize the
  file and its containing directory before returning durable effect proof;
- read, durably synchronized delete and inventory scoped blobs;
- enforce root containment, reject symlinks and use private filesystem permissions;
- expose no raw path or blob reference northbound.

Upload spools and OpenDAL rename targets live only in the reserved
`.weave-native-staging/v1/` namespace outside every member scope. Startup and inventory paths
delete a bounded batch only after its modification time exceeds the one-hour safety window and a
fresh cross-process exclusive lock can be acquired on its opaque owner marker. The publisher holds
that lock across spool, publication, and cleanup, so even an old in-flight staging object remains
protected. Current staging never enters `BlobReference` parsing or orphan reconciliation, while
an unlocked crash-left entry becomes safely reclaimable. Publication synchronizes the blob, destination
directory and staging directory; deletion synchronizes the containing directory, and sync failure
remains retryable rather than producing durable cleanup evidence.

Canonical/member paths are not blob keys. Changing the BlobStore implementation must not change canonical IDs, WebDAV paths, versions, permissions or transfer envelopes.

The filesystem root contains one reserved adapter-private
`.weave-files-volume-authority-v1.json` marker bound to one append-only
`weave_files_volume_authorities` row. The initializer mints their unpredictable volume,
generation and transition references only for the existing isolated first-provision or exact
dogfood reset call path. The initializer-minted transition receipt is transient private evidence
of that accepted call; only its digest is retained. It is not a reset token, manifest, approval or
second lifecycle authority.

Schema receipt v6 binds both authority-row and canonical marker digests. Initializer restart,
offline receipt verification and serving readiness reject a missing/altered marker, replacement
root, missing/duplicated row, generation mismatch or stale receipt. The reserved marker is outside
every `BlobScope`, cannot be constructed as a `BlobReference`, and is therefore excluded from
member inventory and orphan cleanup.

## Atomicity, journal and reconciliation

PostgreSQL and blob storage do not share one ACID transaction. Native PUT, MKCOL, COPY, MOVE and
DELETE therefore use two short relational boundaries around retry-safe blob effects:

The configured native binding bootstrap provisions the default organization/Space stream head
before serving begins. It may recover a crash between first binding activation and first head
creation only while the scope and organization have no Files intent, plan, change, lock or metadata
history. Once any Files activity exists, bootstrap, mutation, retry and LOCK entry points never
create or repair a missing head; a missing row is treated as corrupt state and fails closed before
blob access.

1. PostgreSQL commits a CREATED operation intent, complete target set and sealed immutable mutation
   plan before any blob access.
2. The blob phase publishes or verifies only the bindings named by that plan. A retry skips every
   already-proven receipt and performs only missing planned effects.
3. Under the organization/Space stream-head lock, PostgreSQL rechecks the sealed plan, current
   authorization, source snapshots, parents, ETags and applicable locks. It then commits metadata,
   private bindings, lock movement, the complete gap-free journal range, head advance, SUCCEEDED
   intent and one reserved outbox row atomically.

LOCK, refresh and UNLOCK use the same scope-head serialization and atomically commit lock state,
intent and the reserved outbox row, but deliberately create no mutation plan, journal row or stream
revision. Active ancestor and descendant locks conflict, and authorization is rechecked while the
head is locked.

An uncertain finalization is probed by operation reference. Success is accepted only when the
exact metadata, journal range, current head and reserved outbox evidence agree. A clean non-commit
may retry the same plan; an impossible partial set fails closed as corruption. Ambiguous attempts
are bounded and never allocate a second plan, File ID, blob binding or revision range.

Canonical queries verify metadata/blob length and digest. Reconciliation compares canonical
metadata with bounded blob inventory but excludes every source or result binding protected by a
nonterminal plan. DENIED and FAILED plans remain protected until cleanup has recorded one immutable
disposition for every distinct planned binding; the outbox-driven cleanup path rechecks canonical
references and other plans before any idempotent delete.

The per-organization/Space change reader captures one numeric high-water on its first page, returns
ascending gap-free changes, and uses an HMAC-protected continuation bound to organization, Space,
high-water, last revision, page limit and cursor version. History below the retained floor fails
closed with reset-required evidence.

## Bounded WebDAV content transfer

The first qualified northbound content profile is native WebDAV PUT, GET and HEAD. It has no
byte-array fallback:

- PUT validates authorization, lock state, current ETag preconditions, request framing, content
  coding and fixed-length bounds before it opens the servlet request body; the preconditions are
  checked again against the pinned binding before Tx1 seals its immutable fences;
- fixed and chunked bodies are copied in at most 65,536-byte chunks into a private, capacity-reserved
  ingress object under the validated Files volume/generation;
- the ingress owner lock remains held while the descriptor is bound to the immutable operation
  reference and Tx1 commits; a nonterminal plan protects the retained bytes;
- PostgreSQL, not private spool inventory, enumerates bounded recovery candidates. Recovery reopens
  the exact same-generation ingress by operation reference and executes the sealed plan without a
  client resend;
- GET observes metadata and its private binding once, verifies the complete representation into a
  capacity-reserved private egress object before committing HTTP success, then streams that verified
  object to the client;
- HEAD and a matching If-None-Match response use the same binding-free metadata snapshot without
  opening a blob or allocating egress;
- a client disconnect releases and deletes private egress, while ingress is released only after
  terminal relational evidence;
- a scheduled bounded, age-fenced scavenger rechecks the relational protection state while holding
  the private owner lock before reclaiming crash-left content.

The implementation target is 64 MiB per representation with four admitted ingress and four
admitted egress transfers per server instance. Those values are an adapter capability profile, not
member-visible capacity diagnostics. External provider adapters that have not qualified this exact
profile fail closed with `files-streaming-not-supported`; they do not receive a byte-array bridge.
The provider registry projects a fresh `weave.capability-profile/v1` observation on every status
read, including native/F0-or-blocked/F4 records, conformance and adapter versions, evidence,
expiry, and the runtime-observed maximum, buffer and concurrency limits. Request admission uses
the same qualification observation and cannot rely on a stale startup snapshot.

`CanonicalFileRecord` contains only canonical domain metadata. The persistence-port envelope `StoredFileRecord` pairs that metadata with an opaque `BlobBinding`; JPA maps both into the same `weave_files_objects` row and transaction. The physical `storage_reference` column and its representation remain private to the persistence adapter, with no storage reference exposed through domain or public Files models.

The incremental qualification records are [Native Files private blob-binding evidence](../evidence/native-files-private-blob-binding.md)
and [Native Files V7 durability evidence](../evidence/native-files-v7-durability.md), plus
[Native Files bounded streaming evidence](../evidence/native-files-bounded-streaming.md).

## Backup and restore contract

The executable recovery slice uses a quiesced canonical Files source and creates two private artifacts:

1. a custom-format PostgreSQL consistency dump without ownership or privilege records;
2. a contained private archive of the native BlobStore root.

It restores both artifacts, including the root authority marker and bound schema receipt, into
independent empty targets and verifies through the Files port:

- the same canonical file ID, path and version;
- identical bytes and content integrity;
- matching metadata and blob inventory;
- successful creation and reading of a new file after restore;
- no mutation of the original source environment.
- the same Files volume/generation authority projection before and after restore.

The authoritative persistence test is `CanonicalFilesBackupRestoreTest`. Real HTTP WebDAV equivalence after isolated restore remains part of the complete #1326 vertical and the final #1412 system E2E.

## Security

Access control is enforced before the BlobStore port. Backup artifacts are private, content is never emitted as support evidence, archive extraction rejects absolute paths, empty or dot segments, duplicates and symlink traversal, and restored files/directories receive private permissions where POSIX permissions exist.

## Fresh-start policy

No legacy provider import, dual write, compatibility reader, hidden provider adoption or historical unreleased Files-store compatibility is introduced by this implementation.
