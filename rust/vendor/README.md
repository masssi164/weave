# Vendored Matrix crypto patch

`matrix-sdk-crypto` 0.18.0 is vendored from the published Apache-2.0 crate at
upstream commit `1c44fb66214667c6d00acaf72ab592493653708b` (tag
`matrix-sdk-crypto-0.18.0`). The upstream behavior gap is tracked in
<https://github.com/matrix-org/matrix-rust-sdk/issues/3356>.
Weave carries one narrow behavior patch in `src/olm/session.rs` and
`src/identities/device.rs`: outbound sends no longer advance the receive
timestamp, and Olm session selection uses that last-successful-receive time
with `creation_time` as a tie-breaker instead of creation time alone.

This matches the Matrix session-selection rule and prevents a newly-created
but unusable session from indefinitely outranking a session that just
decrypted valid traffic. Remove the `[patch.crates-io]` override after an
upstream release contains the equivalent fix and the two-pass Live Stack E2E
gate passes against that release.
