# Native Files closure evidence

Status: qualification in progress under issue #1326. This document becomes immutable closure evidence only after the complete WebDAV vertical is green on its final exact head.

## Architecture under test

- canonical Files is implemented by `CanonicalFilesQueries`, `CanonicalFilesCommands` and `CanonicalFilesTreeCommands`;
- `CanonicalNativeFilesComposition` is the only native `FilesProviderPort` bean;
- no provider-shaped native adapter owns Files data behavior;
- Apache OpenDAL filesystem storage is the initial private `BlobStorePort` implementation;
- a future S3-compatible store belongs below `BlobStorePort` and is not a parallel canonical Files provider;
- PostgreSQL/JPA owns canonical metadata persistence, provider-binding revision, lifecycle and locks behind `FilesAuthorityRepository`;
- blob references are opaque and scoped; canonical paths are not blob keys;
- reconciliation owns bounded orphan cleanup and missing or corrupt blob detection.

## Required evidence

Every structural Files head must prove:

```text
Core CI
Native Provider Gate
Native Persistence Closure
Full Compose E2E
```

The Files-specific tests cover or must cover:

- create, write, read and restart;
- tenant isolation;
- immutable publication and digest verification;
- concurrent activation safety;
- COPY with independent canonical IDs;
- MOVE with stable canonical IDs;
- DELETE with tombstones and unreferenced-blob cleanup;
- overwrite and expected-version preconditions;
- symlink and path-containment rejection;
- bounded orphan reconciliation and missing-blob detection;
- configured maximum blob size;
- bounded streaming for large payloads;
- PostgreSQL-plus-blob backup and restore through the direct canonical composition;
- absence of `WeaveNativeFilesAdapter` and obsolete native S3 configuration.

The complete #1326 closure additionally requires real-HTTP WebDAV equivalence after isolated restore, a durable Files journal and stream head, coherent metadata/journal/outbox/intent transactions, and provider connector round-trip conformance.

## Commands

```bash
./gradlew :weave-files-core:test
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.*'
./gradlew :server:postgresJpaTest
./gradlew coreArchitectureCi
```

## Final run references

To be filled only after the complete #1326 head is stable and green:

- final PR head: pending
- Core CI: pending
- Native Provider Gate: pending
- Native Persistence Closure: pending
- Full Compose E2E: pending

No external-provider result may be cited as native Files evidence.
