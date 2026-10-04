# Native Chat boundary

The accepted #1470 release profile is the pinned corpus file `steering/release-2026-10-product-consolidation.md`. Matrix is the current Chat protocol. Flutter's `rust/matrix-client` Matrix SDK owns sync, message transport, and E2EE; Weaver/OpenClaw keeps its established Matrix channel. Weave Server owns authorized product handoff and stable Space/room associations. It does not replace Matrix with a proprietary message API or maintain a second message ledger.

The public platform configuration supplies a Matrix Client-Server homeserver URL separate from the Weave User API origin. Native sign-in uses supported OAuth with a Matrix session and audience distinct from the human Weave API, admin, and Weaver workload sessions. A Weave API bearer token is never treated as a Matrix token. Keycloak remains the default identity backbone, with MAS as the Matrix authorization layer where configured. Federation is disabled by default.

## Client cryptography and recovery

`rust/matrix-client` and the Flutter Rust bridge retain private identity keys, Olm/Megolm state, verification and recovery state, and encrypted local crypto storage. Weave Server must not receive decrypted room bodies or private client keys. Encrypted-room behavior, device verification/recovery, accessible interaction, restart, and restore need live evidence before E2EE can be called complete.

## Retired server projection

The former `/_matrix/client/**` Weave Server projection is disabled by default. Normal server startup, OpenAPI export, tests, and image construction do not build or load its Rust/Ruma JNI library. The isolated `:server:legacyMatrixFacadeTest` task builds that library solely for historical regression checks. Its code and persistence fixtures are retained until their substantive security, integrity, and recovery requirements have been mapped to independent native Matrix tests. This projection is not the current member endpoint.

Weave's current Chat control and association APIs still enforce server-side organization, membership, and resource authorization. Matrix itself enforces room membership and device access on the message path. Provider status and diagnostics must stay support-safe, and no provider credential or Matrix session may be substituted for current Weave resource authorization.
