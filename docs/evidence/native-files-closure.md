# Native Files transition evidence

Status: historical partial structural qualification on PR #1442 under issue #1326. This is not
complete Files closure evidence. Later incremental evidence is linked below; issue #1326 remains
open until its real-HTTP WebDAV, portability and recovery requirements pass on one exact final head.

## Architecture under test

- canonical Files behavior is implemented by `CanonicalFilesQueries`, `CanonicalFilesCommands` and `CanonicalFilesTreeCommands` behind `FilesProviderPort`;
- `WeaveNativeFilesAdapter` is the sole `weave-native` `FilesProviderPort` bean and is only a Spring boot/error-translation composition over those canonical use cases;
- Apache OpenDAL filesystem storage is the initial private `BlobStorePort` implementation;
- a future S3-compatible store belongs below `BlobStorePort`; it is not a parallel canonical Files provider;
- PostgreSQL/JPA owns canonical metadata, provider-binding revision, lifecycle and locks behind `FilesAuthorityRepository`;
- blob references are opaque and scoped; canonical member paths are not blob keys;
- reconciliation owns bounded orphan cleanup and missing or corrupt blob detection.

## Required evidence

Every PR #1442 head proposed for merge must prove:

```text
Core architecture
native-providers
postgres-flyway
PostgreSQL persistence
Full Compose E2E
Release Notes Label Check
```

The transition-specific tests prove:

- the complete Spring application context contains exactly one native `FilesProviderPort` bean, `weaveNativeFilesAdapter`;
- every current native list/read/write/create/COPY/MOVE/DELETE operation delegates through the canonical application layer;
- provider health instrumentation observes the active `FilesProviderPort` rather than a concrete implementation;
- immutable blob publication followed by failed metadata activation leaves no active canonical file and bounded reconciliation removes the orphan;
- the obsolete second native composition/configuration is absent while the retained adapter remains thin.

Previously merged Files qualification continues to cover restart, tenant isolation, immutable publication and digest verification, concurrent activation, path containment, size/streaming bounds, and quiesced PostgreSQL-plus-blob backup/restore. Those results are prerequisites, not proof of the remaining #1326 vertical.

The later [private blob-binding evidence](native-files-private-blob-binding.md) and
[V7 durability evidence](native-files-v7-durability.md) cover subsequent implementation slices
that were still open at PR #1442. Complete #1326 closure still requires real-HTTP WebDAV
equivalence after isolated restore, provider connector round-trip conformance, two-instance and
restart proof, and the remaining accepted WebDAV use cases.

## Commands

```bash
./gradlew :weave-files-core:test
./gradlew :server:compileJava :server:compileTestJava
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.*'
./gradlew :server:test --tests 'com.massimotter.weave.backend.config.ProviderHealthActuatorMetricsTest'
./gradlew coreArchitectureCi
```

## Exact-head evidence record

The authoritative head and check URLs are the GitHub PR #1442 head and its attached check suite at merge time. A durable PR comment must record that exact SHA and the successful run URLs before merge; this file cannot truthfully self-reference the hash of the commit that contains it.

- transition PR: https://github.com/masssi164/weave/pull/1442
- governing issue and remaining work: https://github.com/masssi164/weave/issues/1326

No external-provider or S3-adapter result may be cited as proof of canonical native Files behavior.
