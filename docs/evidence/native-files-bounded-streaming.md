# Native Files bounded streaming evidence

Status: incremental candidate evidence for the bounded PUT, GET and HEAD portion of issue #1326.
This is not complete WebDAV, Files portability or production-readiness evidence.

## Proven boundary

- `ReplayableFileContent` carries exact size, SHA-256 digest, deterministic media type and a fresh
  bounded stream factory; V7 planning never opens the stream.
- Native PUT blob effects and COPY transfer in fixed-capacity 65,536-byte chunks and verify exact
  size/digest receipts without whole-content heap buffers.
- `VerifiedFileRead` is binding-free. It captures one exact metadata/binding observation internally,
  exposes representation headers, and performs no blob read until transfer.
- The native private content store is fenced to the validated volume/generation, uses cross-process
  owner and capacity locks, private permissions, atomic publication and directory durability sync,
  bounded state reads, exact ingress/egress reservations, and support-safe errors.
- Fixed and unknown-length PUT requests reject invalid framing, unsupported coding, oversize,
  admission pressure and storage exhaustion before durable mutation using the accepted 400, 415,
  413, 503 and 507 distinctions.
- Current ETag preconditions are evaluated before ingress or request-body open, then evaluated again
  against the pinned binding before Tx1 seals its immutable fences.
- Tx1 binds ingress to the operation reference while continuously holding its owner lock. A bounded
  relational query—not spool inventory—selects nonterminal PUT recovery work and reopens the exact
  same-generation content without client resend.
- GET pre-verifies private egress before HTTP success; HEAD and matching If-None-Match perform no
  body preparation. Egress remains reserved until stream close/disconnect.
- Non-qualified adapters fail closed and receive no native WebDAV byte-array fallback.
- The selected native adapter publishes a fresh expiring CapabilityProfile v1 with the actual
  runtime maximum, transfer-buffer and ingress/egress concurrency limits; integrity degradation
  removes verified streaming qualification until an explicit clean reconciliation proves recovery.
- A production scheduled, bounded, age-fenced scavenger holds the private owner lock and performs an
  immediate relational-protection check before reclaiming crash-left ingress or egress.

## Executable evidence

```text
./gradlew :weave-files-core:test :weave-files-core:check
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.BoundedNativeFilesContentStoreTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.NativeFilesStreamingRecoveryDispatcherTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.NativeFilesContentScavengerTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.config.ProviderCoreConfigurationTest' --tests 'com.massimotter.weave.backend.service.ProviderCapabilityHealthServiceTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.WeaveNativeFilesAdapterTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.controller.FilesWebDavControllerTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.controller.FilesWebDavRealSocketStreamingTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.FilesFacadeNativeAuditIdempotencyTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.WeaveNativeFilesDurableRecoveryTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.architecture.ServerArchitectureBoundaryTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepositoryPostgresTest'
./gradlew specCorpusConformance
python3 tools/files_webdav_facade_acceptance_check.py
```

The real-socket test starts an actual HTTP server and proves a 2 MiB verified GET, metadata-only
HEAD/304 and unknown-length chunked PUT through the controller projection. It deliberately replaces
the facade with a test seam, so it cannot be cited as authenticated real-HTTP PostgreSQL/blob or
two-instance evidence. The core, private-store, facade and PostgreSQL tests qualify those lower
boundaries independently; a protected integrated E2E is still required before closing issue #1326.

## Remaining

- authenticated real-HTTP execution against PostgreSQL plus the actual BlobStore;
- client-abort proof through the integrated native facade and process restart without resend;
- two-instance ingress/egress admission and mutation concurrency;
- complete SEARCH/change-sync/rights profile and canonical import/export;
- provider A-to-canonical-to-provider B fidelity and restored real-WebDAV mutation proof.

Before merge, the PR must record its exact commit SHA and successful protected-check URLs. This
page cannot self-reference the commit that contains it.
