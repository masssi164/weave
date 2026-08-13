# Native Files closure evidence

Status: qualification in progress on PR #1325. This document becomes immutable closure evidence only after all referenced runs are green on the final PR head.

## Architecture under test

- canonical Files remains behind `FilesProviderPort`;
- `weave-native` is the default provider implementation;
- Apache OpenDAL filesystem storage is the only private blob I/O backend used by `weave-native`;
- S3 is a separate provider adapter and is not native blob storage;
- PostgreSQL/JPA owns canonical metadata, provider binding revision, lifecycle and locks;
- blob references are opaque and scoped; canonical member paths are not blob keys;
- reconciliation owns bounded orphan cleanup and missing-blob detection.

## Required evidence

The final head must prove:

```text
Native Provider Gate
Native Persistence Closure
regular CI
```

The Files-specific tests must cover:

- write/read/restart;
- tenant isolation;
- immutable publication and digest verification;
- concurrent publication safety;
- move/copy with stable canonical identity semantics;
- symlink/path-containment rejection;
- orphan reconciliation and missing-blob detection;
- configured maximum blob size;
- bounded streaming path for large payloads;
- absence of the obsolete native S3 store/config/test path.

## Commands

```bash
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.*'
./gradlew :server:compileJava :server:compileTestJava
./gradlew serverCi
```

## Final run references

To be filled only after the final PR head is stable and green:

- final PR head: pending
- Native Provider Gate: pending
- Native Persistence Closure: pending
- regular CI: pending

No S3-provider result may be cited as native Files evidence.