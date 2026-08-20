# Native Files V7 durability evidence

Status: qualification in progress for the stream, finalization and cleanup portion of issue #1326.
This is incremental evidence, not complete Files or WebDAV closure evidence.

## Scope under test

- Flyway V7 adds adapter-private Files stream heads, immutable mutation plans and targets, a
  gap-free change journal, and terminal blob-cleanup dispositions. V1 through V6 remain immutable.
- The V7 transition is fresh-start only: relational Files state and the private native blob root
  must be empty before the migration is first applied. No historical Files data is backfilled.
- V7 creates one adapter-private Files volume-authority table without inferring an identity. The
  accepted one-shot initializer mints the row and immutable reserved root marker; schema receipt
  v6 binds both digests, and normal startup only validates them.
- Native PUT, MKCOL, COPY, MOVE and DELETE commit a complete sealed plan before blob access and use
  one stream-head-locked final transaction for metadata, private bindings, lock movement, journal,
  head, intent success and the reserved outbox row.
- The explicit native binding bootstrap provisions the default Space head idempotently before
  serving. It repairs only a pristine first-provision crash gap; after any Files activity,
  bootstrap, first execution, retry and LOCK fail closed rather than recreating a missing head.
- Blob publication is idempotent. Partial subtree COPY retries verify existing receipts and publish
  only missing planned results. Publish, read, receipt and existing-key verification use bounded
  streams. Reserved staging is outside member scopes; a cross-process owner lock protects every
  active publisher, and age-fenced bounded scavenging removes only unlocked crash-left entries.
  File, staging-directory, publication-directory or deletion-directory
  durability-sync failure prevents durable success evidence and remains retryable.
- Finalization rechecks live EDIT authorization, source snapshots, parents and applicable locks.
  Concurrent ranges are serialized without gaps or interleaving.
- Native LOCK, refresh and UNLOCK use the same scope head and atomically commit lock state, intent
  and one outbox row without creating a plan, journal row or revision.
- Change reads capture a stable high-water and use an opaque HMAC continuation bound to the exact
  tenant, Space, page and cursor state.
- Nonterminal plan bindings and failed-plan bindings awaiting a complete disposition set are
  protected from orphan deletion. Cleanup uses the closed precedence
  `STILL_REFERENCED` -> `STILL_PROTECTED` -> `DELETED`/`ALREADY_ABSENT`, with insert-only evidence.
- Cleanup requeue, exhaustion and delivery settlement all require the exact unexpired lease;
  expired work can advance only after a fresh eligibility check and lease token.
- Uncertain relational outcomes are probed by operation reference; exact success, clean non-commit,
  terminal failure and corruption are distinct outcomes. Retries reuse the same plan and recorded
  result.

No mutation-plan target, private binding, cleanup binding digest, provider reference or member
content is exposed through Files domain values, WebDAV responses, audit payloads or support-safe
progress results.

## Required executable evidence

Every candidate head proposed for merge must run at least:

```text
./gradlew :weave-application-core:test
./gradlew :weave-files-core:test
./gradlew :server:compileJava :server:compileTestJava
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.WeaveNativeFilesDurableRecoveryTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.files.application.NativeFilesChangeReaderTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinatorTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupOutboxDispatcherTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.files.application.NativeFilesBindingScopeObserverTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.schema.NativeFilesVolumeAuthorityTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.FilesystemBlobStoreTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.architecture.ServerArchitectureBoundaryTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.CanonicalNativeFilesPrimarySelectionTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepositoryPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.JpaNativeFilesLockRepositoryPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.FilesBlobCleanupDispositionPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.NativeFilesCleanupOutboxDispatcherPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.application.FilesMutationIntentServicePostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.schema.SchemaAuthorityInitializerPostgresTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.schema.CanonicalPostgresBackupRestoreTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.service.files.CanonicalFilesBackupRestoreTest'
./gradlew coreArchitectureCi
./gradlew specCorpusConformance acceptanceContract
```

The scheduled outbox dispatcher and both cleanup PostgreSQL tests are registered in
`:server:postgresJpaTest`. Real PostgreSQL execution is mandatory; H2 or compile-only evidence
cannot close this slice.

## Exact-head record

Before merge, the pull request must record the exact commit SHA and successful protected-check URLs.
This page cannot truthfully self-reference the commit that contains it.

## Explicitly remaining under issue #1326

- authenticated PostgreSQL-plus-BlobStore real-HTTP qualification of the complete accepted WebDAV
  profile; the bounded-content slice has a real-socket controller test but does not claim that full
  integrated topology;
- canonical search, change/sync projection, import/export and remaining rights semantics;
- two-instance protocol concurrency and complete process-restart proof;
- deterministic provider A-to-canonical-to-provider-B roundtrip with complete fidelity accounting;
- isolated PostgreSQL plus BlobStore restore followed by equivalent real-WebDAV read and mutation.

The V7 slice must not be cited as complete Files, provider portability, WebDAV conformance or
production-readiness evidence.
