# Native Chat provider

## Boundary

Chat is a provider-neutral canonical domain. Matrix Client-Server is a permanent northbound Weave Server protocol surface; it is not a provider and cannot be disabled as a provider-selection mechanism.

```text
Matrix Client-Server
  -> Ruma/JNI protocol projection
    -> canonical Chat application/domain
      -> ChatProviderPort
        -> weave-native (selected default)
        -> optional Synapse/Matrix-backed or future providers
```

Changing the selected provider must not change canonical conversation/event IDs, authorization semantics, Matrix URLs, application contracts or member-facing protocol behavior.

## Native persistence

`weave-native` owns canonical rooms/conversations, memberships, immutable events, relations, redactions, receipts, idempotency, logical sync revisions, provider mappings and outbox state in Weave PostgreSQL/JPA persistence.

Matrix routing/public-or-encrypted-key metadata is normalized separately from canonical Chat state. Tenant-wide serialized snapshots are not an authority. Process-local structures may only be bounded derived caches or support-safe counters.

## Matrix protocol boundary

`rust/matrix-protocol` is the server protocol crate. It uses Ruma for Matrix protocol types/validation/serialization and jni-rs for the Java boundary. It does not own authorization, persistence, provider selection or client cryptography.

The Ruma/JNI native library is a required server artifact because the Matrix facade is permanent. Missing/incompatible native artifacts fail closed with actionable diagnostics.

## Client cryptography

`rust/matrix-client` owns Matrix SDK / matrix-sdk-crypto and Flutter Rust Bridge integration. The client owns private identity keys, Olm/Megolm state, verification, recovery and encrypted local crypto storage.

The server stores/routes only public or opaque encrypted protocol metadata and ciphertext. It never decrypts room events or stores private client keys.

## Idempotency and sync

Transaction-bearing operations bind idempotency to tenant, authenticated user/device, normalized endpoint, transaction ID and provider-binding revision. Exact committed responses are replayed for the same key and digest; a different digest fails without mutation.

Canonical Chat and Matrix routing streams use explicit logical high-waters rather than incidental generated IDs. Stream heads are advanced transactionally so concurrent commits and pagination cannot permanently skip committed state.

## Fresh-start policy

No Synapse/MAS history import, database compatibility, dual write or hidden provider-data adoption is introduced. Optional provider adapters remain southbound behind `ChatProviderPort`.