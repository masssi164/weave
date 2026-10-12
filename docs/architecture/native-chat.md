# Native Chat provider

## Boundary

Chat is a provider-neutral canonical domain. Matrix Client-Server is a permanent northbound Weave Server protocol surface; it is not a provider and cannot be disabled as a provider-selection mechanism.

```text
Matrix Client-Server
  -> MatrixProtocolCodec                    (Infrastructure Port)
    -> Ruma/JNI protocol adapter            (Infrastructure Adapter)
      -> canonical Chat application/domain
        -> ChatProviderPort                 (Provider Port)
          -> weave-native                   (Provider Adapter, selected default)
          -> optional Synapse/Matrix-backed or future providers
```

The project-wide provider/infrastructure terminology is defined in [`provider-and-infrastructure-boundaries.md`](provider-and-infrastructure-boundaries.md).

Changing the selected provider must not change canonical conversation/event IDs, authorization semantics, Matrix URLs, application contracts or member-facing protocol behavior.

## Native persistence

`weave-native` owns canonical rooms/conversations, memberships, immutable events, relations, redactions, receipts, idempotency, logical sync revisions, provider mappings and outbox state in Weave PostgreSQL/JPA persistence.

Repository interfaces are persistence Infrastructure Ports. JPA/Hibernate/PostgreSQL implementations are Infrastructure Adapters below the native Provider Adapter.

Matrix routing/public-or-encrypted-key metadata is normalized separately from canonical Chat state. Tenant-wide serialized snapshots are not an authority. Process-local structures may only be bounded derived caches or support-safe counters.

## Matrix protocol boundary

`MatrixProtocolCodec` is the server-side Matrix Infrastructure Port. `rust/matrix-protocol` provides its Infrastructure Adapter using Ruma for Matrix protocol types/validation/serialization and jni-rs for the Java boundary.

Ruma is a protocol infrastructure library, not a Chat provider. The Ruma/JNI adapter does not own authorization, canonical Chat state, persistence, provider selection or client cryptography.

The Ruma/JNI native library is a required server artifact because the Matrix facade is permanent. Missing/incompatible native artifacts fail closed with actionable diagnostics.

## Client cryptography

`rust/matrix-client` owns Matrix SDK / matrix-sdk-crypto and Flutter Rust Bridge integration. The client owns private identity keys, Olm/Megolm state, verification, recovery and encrypted local crypto storage.

For an E2EE-enabled room, the canonical Chat store retains ciphertext and non-secret state only; the server never decrypts room events or stores private client keys. For an authorized non-E2EE business room, canonical server-readable content may support retention, search and compliance under organization policy. Encrypted rooms cannot be downgraded to obtain that content.

## Idempotency and sync

Transaction-bearing operations bind idempotency to tenant, authenticated user/device, normalized endpoint, transaction ID and provider-binding revision. Exact committed responses are replayed for the same key and digest; a different digest fails without mutation.

Canonical Chat and Matrix routing streams use explicit logical high-waters rather than incidental generated IDs. Stream heads are advanced transactionally so concurrent commits and pagination cannot permanently skip committed state.

## Security boundary

Access control remains a canonical Weave concern. Ruma/JNI parses, validates and projects Matrix wire data but does not decide tenant scope, membership, authorization, visibility or provider selection.

For this release, the Weave-owned Flutter client uses its normal member OIDC/PKCE session at the Matrix facade after one sign-in. The facade still checks current organization, membership, resource and device scope. A separate Matrix OAuth client/audience, dynamic registration and independent third-party Matrix-client interoperability are deferred; admin and workload credentials remain separate from member authorization.

## Fresh-start policy

No Synapse/MAS history import, database compatibility, dual write or hidden provider-data adoption is introduced. Optional provider adapters remain southbound behind `ChatProviderPort`.
