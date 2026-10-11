# ADR: separate Matrix server protocol and client crypto boundaries

Status: Accepted for the native-provider closure track; qualification remains gated by PR #1325.

## Context

Matrix Client-Server is a permanent northbound Weave Server protocol surface. It is not the canonical Chat provider. Native Chat remains provider-neutral behind `ChatProviderPort`, while Matrix wire shaping and client cryptography have different trust and deployment boundaries.

## Decision

Server protocol and client cryptography are separate Rust crates:

- `rust/matrix-protocol`: Ruma + jni-rs only. It validates/projects Matrix wire values across a closed, versioned JNI operation set. It owns no database, authorization, provider selection or client crypto state.
- `rust/matrix-client`: Matrix SDK / matrix-sdk-crypto + Flutter Rust Bridge. It owns device-private identity/Olm/Megolm state, verification, recovery and the encrypted local crypto store.

The server stores canonical Chat state. For E2EE-enabled rooms it retains ciphertext and non-secret routing state only; it does not decrypt encrypted events or persist private client keys. Authorized non-E2EE business rooms may retain server-readable content under organization policy.

The old shared `rust/matrix-core` runtime is removed. The server JNI artifact is `weave_matrix_protocol`; old `weave_matrix_core`, `matrixCoreLibrary` and `weave.matrix.core.library.path` names are forbidden by CI.

## Matrix facade invariant

Enabling or using the permanent Matrix northbound facade does not select Synapse or MAS. External Matrix providers, where supported, remain southbound provider adapters. There is no server feature switch whose purpose is to disable Matrix in order to select the native provider.

## Qualification

Closure requires independent protocol/client Rust tests, native JNI startup/failure diagnostics, ciphertext-only server evidence for E2EE-enabled rooms and platform artifact provenance. Missing evidence remains a merge blocker rather than a compatibility fallback.
