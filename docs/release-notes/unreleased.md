# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet. `v0.1.0-rc.3` is the latest published prerelease; older post-RC2 entries moved into the versioned v0.1 release notes and RC3 evidence audit.

## Added

- Sprint 22 adds a CI-safe free provider lab gate, manifests, fixture evidence, and operator runbook for Keycloak, Authentik, Matrix/Synapse, Zulip, Nextcloud, MinIO, Radicale, OpenProject, and the Docker Runtime boundary without claiming provider interchangeability or release readiness.
- Sprint 23 adds a CI-safe Chat Provider Switch contract gate for Matrix/Synapse to Zulip canonical object coverage, fixture dry-run/apply evidence, rollback-honesty classification, LossyFieldReport enforcement, scoped claim gating, and support-safe ProviderRef redaction without claiming lossless migration, production apply, production rollback, release readiness, or provider interchangeability.
- Sprint 24 adds a guarded Weaver Runtime Factory provider-lab fixture and gate for per-user runtime lifecycle, desired-state reconciliation, isolation, support-safe redaction, revoke, and claim safety without claiming production PA availability, customer-ready Weaver, release-ready Weaver, or broad autonomous AI availability.
- Sprint 29 adds executable pre-human release validation guards, human UX/accessibility and Weaver evidence templates, and a final decision guard that blocks release-ready wording until automated evidence, human signoff, and release-blocker checks all pass.
- Sprint 26 adds operator recovery guardrails for `BackupManifest`, `RestoreReceipt`, support-bundle redaction reports, and `docs/operator-recovery-known-limitations.md` without claiming release-ready restore proof.

## Changed

- Public docs and README evidence pointers now identify `v0.1.0-rc.3` as the latest published prerelease and link the RC3 evidence audit.
- Sprint 21 product-reality gates now require free/self-hosted provider proof, explicit reality levels, and automated claim blocking before any customer-ready, Weaver-available, provider-interchangeable, production-rollback, or release-ready wording.

## Fixed

- No post-`v0.1.0-rc.3` bugfix release notes yet.

## Security

- No post-`v0.1.0-rc.3` security release notes yet.

## Accessibility

- Sprint 18 release-trust claim control keeps manual assistive-technology signoff explicitly blocked by #591; automated tests, support-safe artifacts, release notes, and green Live Stack E2E cannot substitute for real AT reviewer evidence.

## Migration/Operator Notes

- No production provider cutover, migration apply, Terraform/live infrastructure change, or public production release has been performed after `v0.1.0-rc.3`.
- Operator backup/restore wording must reference `docs/operator-recovery-known-limitations.md`; Sprint 26 release promotion remains blocked until a live disposable destroy/restore rehearsal proves recovered domain data.

## Known Issues

- #591 remains a release blocker: actual manual assistive-technology signoff is required before Sprint 18 milestone closure or public/production release signoff, unless release ownership explicitly splits the remaining manual signoff into a separate accepted blocker.
- #642 remains a release blocker for operator recovery until `RestoreReceipt` evidence includes a performed destroy step and recovered domain-data validation.
