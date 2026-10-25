# Chat ownership

Chat owns canonical ledger and Matrix identity projection persistence, plus the 15 normalized
Matrix public-envelope tables. Shared member/session revocation belongs to Core Security and
works independently of Chat. Private device keys, cryptographic sessions, decrypted payloads
and recovery secrets remain client state; Server persists existing public keys/opaque envelopes.

`persistence` owns the JPA mappings, repositories and `MatrixE2eeRelationalStore` implementation.
The existing Matrix envelope port is physically here as well. Typed JPQL handles normal CRUD,
tenant/device/user scope, bounded claim reads, monotonic progress, idempotent appends and
backups. The single PostgreSQL transaction advisory-lock statement fences aggregate writers
when no row exists to lock; one-time key claiming also retains row-level SKIP LOCKED semantics.
The architecture guard permits exactly that scoped statement, never the surrounding CRUD.

Both persistence and actual serving-store auto-configuration register module-locally through
Boot imports. Non-web schema/operator contexts see mappings without constructing the serving
adapter. Discovery tests use real Boot imports and assert all 15 mapped types and a single
serving port bean. PostgreSQL tests cover generated-baseline indexes/checks, isolation,
concurrency, rollback, claims, recovery and repeated mutations within one transaction.

This remains incomplete Chat runtime extraction: Matrix/JNI facades, services, controllers,
provider integration and selection are still transitional in App. Presence of normalized
persistence does not establish client cryptography or full product acceptance.

From the repository root:

```sh
./gradlew :server:domains:chat:persistence:check
./gradlew :server:app:postgresJpaTest --tests '*MatrixE2eeRelationalStorePostgresTest'
```

See [Server ownership](../../../docs/architecture/server-domain-ownership.md) for the single
execution plan and [Java/Spring standard](../../../docs/java-spring-standard.md) for shared rules.
