# Third-party notices

Weave contains third-party software that is not relicensed under the EUPL. Its existing copyright notices and licence terms remain in force. This notice records the incorporated material identified here; it is not a complete audit of all transitive dependencies.

## Vendored Matrix SDK crypto

- Source tree: `rust/vendor/matrix-sdk-crypto/**`.
- Upstream: [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk), `matrix-sdk-crypto` 0.18.0.
- Package licence: **Apache-2.0**; see the retained [vendored licence](rust/vendor/matrix-sdk-crypto/LICENSE).
- Individual source files also contain MIT grants and specific authorship notices. Those notices are retained and are not replaced by the package-level description.

The Weave-authored modifications in this vendored tree and the corresponding patch series under `rust/vendor/patches/matrix-sdk-crypto-0.18.0/**` are licensed under **Apache-2.0**, rather than the repository-default EUPL, to preserve their upstream contribution path. Existing MIT-licensed portions retain their notices and grants.

The exact patch scope and origin are recorded in [the provenance manifest](rust/vendor/matrix-sdk-crypto.weave-provenance.json) and [the vendor documentation](rust/vendor/README.md). This licensing change does not modify the vendored files, patches, licence, or checksums.

## Vendored Matrix SDK modifications

- Source tree: `rust/vendor/matrix-sdk/**`.
- Upstream: [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk), `matrix-sdk` 0.18.0.
- Package licence: **Apache-2.0**; see the retained [vendored licence](rust/vendor/matrix-sdk/LICENSE). Existing source-file notices and grants remain in place.

The Weave-authored modifications in this vendored tree and their patches under `rust/vendor/patches/matrix-sdk-0.18.0/**` are licensed under **Apache-2.0** to preserve the upstream contribution path. They comprise the OAuth scope correction and the device-continuity signature helper, which keeps the installed device's private key inside its crypto store. The [provenance manifest](rust/vendor/matrix-sdk.weave-provenance.json) pins the published crate checksum, upstream commit, each patch checksum and the complete changed-file allowlist. [Upstream PR #7134](https://github.com/matrix-org/matrix-rust-sdk/pull/7134) tracks the stable-scope correction; the signature helper is a recorded downstream patch and is not claimed to be merged upstream. Existing source-file notices and grants remain intact.

## Dependencies and imported material

Package-manager dependencies and generated or incorporated third-party material remain subject to their respective licences. The Weave licence does not replace them. Preserve all applicable licence texts and required attribution in distributed source and binary packages.

When adding vendored or substantially copied material, identify its origin, preserve its notices, document the applicable licence, and verify the intended redistribution. Do not label third-party material as solely Weave-authored.
