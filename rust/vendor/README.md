# Vendored Matrix SDK patches

## Stable native OAuth scopes

`matrix-sdk` 0.18.0 is vendored from the published Apache-2.0 crate at upstream
commit `1c44fb66214667c6d00acaf72ab592493653708b` (tag
`matrix-sdk-0.18.0`). Its OAuth login still requests the unstable MSC2967 API
and device scope spellings. The single focused patch under
`patches/matrix-sdk-0.18.0/` requests the stable Matrix scope names and updates
the upstream authorization URL test to assert exactly one stable device scope
and no legacy scopes. [Upstream PR #7134](https://github.com/matrix-org/matrix-rust-sdk/pull/7134)
tracks the equivalent correction.

`matrix-sdk.weave-provenance.json` pins the official crate archive checksum,
upstream commit, patch checksum and complete difference allowlist. The same
`matrixSdkVendorProvenanceCheck` gate reconstructs the vendored tree from that
archive and patch. Remove this temporary override only after an accepted
upstream release requests the stable scopes and the native Matrix OAuth journey
passes against that release. This scope correction does not by itself qualify
Weave's Matrix OAuth issuer, client registration, audience, device binding or
independent-client interoperability.

## Crypto session recovery

`matrix-sdk-crypto` 0.18.0 is vendored from the published Apache-2.0 crate at
upstream commit `1c44fb66214667c6d00acaf72ab592493653708b` (tag
`matrix-sdk-crypto-0.18.0`). The upstream behavior gaps are tracked in
<https://github.com/matrix-org/matrix-rust-sdk/issues/3356> and
<https://github.com/matrix-org/matrix-rust-sdk/issues/3427>.
Weave carries three narrow, sequential behavior patches under
`patches/matrix-sdk-crypto-0.18.0/`. The provenance guard applies that series
to the checksum-verified upstream crate and requires it to reconstruct every
vendored source difference exactly:

- outbound sends no longer advance the receive timestamp;
- Olm session selection uses that last-successful-receive time with
  `creation_time` as a tie-breaker instead of creation time alone; and
- the first real `SessionWedged` failure for a device immediately uses the
  SDK's standard one-time-key claim and encrypted `m.dummy` recovery path.
  The last attempt is persisted on that device so repeat attempts remain
  limited to once per hour across app restarts.

This matches the Matrix session-selection rule, prevents a newly-created but
unusable session from indefinitely outranking a session that just decrypted
valid traffic, and avoids waiting an hour before the first evidence-triggered
repair. Remove the `[patch.crates-io]` override after an upstream release
contains the equivalent fixes and the two-pass Live Stack E2E gate passes
against that release.

Patch disposition is temporary-retain-and-upstream: keep the current 0.18.0
pin with provenance until each focused patch is submitted or superseded by an
accepted Matrix Rust SDK release. Do not replace duplicate one-time-key errors
with synthetic success, and do not upgrade blindly while the currently mapped
upstream issues remain open. The manifest records the upstream issue,
invariant, exact patch checksum, changed source paths, and focused regression
test for each change.

`matrix-sdk-crypto.weave-provenance.json` pins the Cargo.lock version, official
release, published crate archive checksum, and the complete allowlist of
intentional differences. Run
`./gradlew matrixSdkVendorProvenanceCheck` to compare the vendor tree with the
Cargo cache or the checksum-verified crates.io archive. Any new or stale
difference fails closed and must be reviewed explicitly.
