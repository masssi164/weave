# Chat ownership

The root library owns the actual Chat serving runtime: canonical conversation use cases,
User/Admin and Matrix facade controllers, wire contracts, provider adapters, configuration,
workspace capabilities, Space relationships and the Rust/Ruma JNI build. App composes this
library; Chat has no App dependency and needs no App component scan.

`application` owns authorization-aware orchestration, canonical state and Matrix public-envelope
synchronization. `api` owns HTTP projection. `port` contains canonical Chat/provider contracts,
conversation admission for conversation-attached future features and the supported typed Matrix
protocol codec. Immutable protocol envelope values belong to that codec contract; controllers
and use cases do not depend on the concrete JNI implementation. `provider` owns native Chat,
Synapse transport and JNI/Ruma translation. The single `backend.matrix.NativeMatrixCore` Java
name is retained for its Rust JNI symbol ABI; its source, JAR and build belong exclusively here.

`persistence` owns canonical ledger mappings, Matrix identity projections and the 15 normalized
Matrix public-envelope tables. Root runtime adapters use those owning mappings; ordinary data
access remains JPA/Spring Data. The sole PostgreSQL transaction advisory-lock statement fences
writers when no aggregate row exists to lock. One-time key claims retain SKIP LOCKED semantics.
The architecture guard permits exactly that statement, not a parallel CRUD layer.

Runtime and persistence auto-configuration register through their own Boot imports. Explicit
serving imports are servlet-only; non-web schema/operator contexts load mappings without
constructing controllers, providers or JNI. The selected Synapse graph constructs without token
mounts or remote access. Real serving startup validates both distinct mounted Application Service
tokens before ordinary startup workflows, and first use also fails closed. Successful validation
caches the same token snapshot used for outbound and callback authentication.

Chat owns its adapter declarations. Runtime diagnostics retain the existing native/Synapse,
Slack and Teams aliases. Future replacement preflight additionally retains `nextcloud-talk`;
that historical preflight candidate does not become a configured runtime adapter. Shared Core
selection policy supplies the same admin-selection projection used by platform diagnostics.
Missing required Chat adapters fail wiring rather than producing synthetic readiness.

Decision Ledger and Meeting Capsules have actual future runtime owners at `../decisions` and
`../meetings`. Their handlers retain the accepted full API paths while their implementation,
wire records and state are outside Chat. They consume only Chat's conversation admission API.
Their in-memory state now uses organization, Space and conversation keys; equal conversation IDs
cannot leak records across tenants or Spaces. The original unused seed-message scaffolding and
combined `ChatFacadeService` were removed. Future-only legacy `channel-general` handling remains
with those future features, not in canonical Chat admission.

Shared member/session revocation remains Core Security and works without Chat. Private device
keys, cryptographic sessions, decrypted payloads and recovery secrets remain client state.
These Server checks do not establish client cryptographic, native-platform or live E2E acceptance.

From the repository root:

```sh
./gradlew :server:domains:chat:test
./gradlew :server:domains:chat:contextTest
./gradlew :server:domains:chat:check
./gradlew :server:domains:check -Pdomains=chat
./gradlew :server:app:postgresJpaTest --tests '*MatrixE2eeRelationalStorePostgresTest' -Pdomains=all
```

The owning unit task builds the pinned Rust/Ruma bridge it exercises. Context tests have their own
report; persistence has separate unit/context checks. App-coupled full-policy and PostgreSQL
harnesses remain in App under owning namespaces, using shared Chat test fixtures where needed.
The retired App-filtered Chat unit bridge has no replacement wrapper. Reduced App packaging and
API/schema activation remain guarded until actual selected composition and specification approval.

See [Server ownership](../../../docs/architecture/server-domain-ownership.md) for the single
execution plan and [Java/Spring standard](../../../docs/java-spring-standard.md) for shared rules.
