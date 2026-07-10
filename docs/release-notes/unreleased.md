# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet. `v0.1.0-rc.3` is the latest published prerelease; older post-RC2 entries moved into the versioned v0.1 release notes and RC3 evidence audit.

## Added

- Adds a client-owned Matrix E2EE release candidate through the Apache-2.0 Matrix Rust SDK and `flutter_rust_bridge`: encrypted room sync/send, encrypted SQLite state, stable device identity, cross-signing, accessible SAS verification, recovery, lost-device denial, opaque server persistence, and live E2E evidence gates.
- Adds stable physical-iPhone session continuity for in-place TestFlight iterations. The saved organization profile, OIDC refresh session, Matrix device ID, Keychain-held crypto-store passphrase, and encrypted history survive ordinary close, relaunch, and app update; explicit account removal remains the destructive boundary.
- Sprint 22 adds a CI-safe free provider lab gate, manifests, fixture evidence, and operator runbook for Keycloak, Authentik, Matrix/Synapse, Zulip, Nextcloud, MinIO, Radicale, OpenProject, and the Docker Runtime boundary without claiming provider interchangeability or release readiness.
- Sprint 23 adds a CI-safe Chat Provider Switch contract gate for Matrix/Synapse to Zulip canonical object coverage, fixture dry-run/apply evidence, rollback-honesty classification, LossyFieldReport enforcement, scoped claim gating, and support-safe ProviderRef redaction without claiming lossless migration, production apply, production rollback, release readiness, or provider interchangeability.
- Sprint 24 adds a guarded Weaver Runtime Factory provider-lab fixture and gate for per-user runtime lifecycle, desired-state reconciliation, isolation, support-safe redaction, revoke, and claim safety without claiming production PA availability, customer-ready Weaver, release-ready Weaver, or broad autonomous AI availability.
- Sprint 29 adds executable pre-human release validation guards, human UX/accessibility and Weaver evidence templates, and a final decision guard that blocks release-ready wording until automated evidence, human signoff, and release-blocker checks all pass.
- Sprint 28 adds commercial adapter readiness specs, a go/no-go matrix, and a CI guard that keeps Slack and Microsoft Teams implementation starts blocked until provider-specific proof, admin consent, cost, export, retention, and rollback evidence exist. It does not claim Slack or Teams integration availability.
- Sprint 26 adds operator recovery guardrails plus support-safe disposable Backup -> Destroy -> Restore -> Validate evidence for fixture domain data; production restore remains operator-approved and private-evidence scoped.
- Sprint 30 adds the hot-phase dogfood readiness evidence pack, exact agentic AI slogan guard, profile-driven setup fixture for dev/LAN dogfood/public dogfood/production, and governed Weaver contracts for policy, mobile approvals, audit, revocation, and privacy boundaries without claiming public production readiness.
- Sprint 32 adds guarded Beta readiness claim gates, adapter-continuity dry-run evidence, Admin readiness preview, governed Weaver approval-boundary evidence, member Client + Weaver flow, Admin + User + Weaver E2E/accessibility smoke evidence, and refreshed release/demo evidence.

## Changed

- Normal member Files, Calendar, and Chat data planes are now documented coherently as OIDC-gated Weave WebDAV, CalDAV/iCalendar, and Matrix Client-Server facades over canonical domains; obsolete REST event/message data-plane access is not a compatibility target.
- Public docs and README evidence pointers now identify `v0.1.0-rc.3` as the latest published prerelease and link the RC3 evidence audit.
- Sprint 21 product-reality gates now require free/self-hosted provider proof, explicit reality levels, and automated claim blocking before any customer-ready, Weaver-available, provider-interchangeable, production-rollback, or release-ready wording.

## Fixed

- Makes the dedicated Live Stack E2E runner self-maintaining with a 10 GiB initial gate and a 5 GiB pre-test gate that preserves 4 GiB usable headroom plus a monitored 1 GiB recovery reserve, complete targeted stack teardown, sandbox-safe generated public CA injection into Rust without Keychain mutation or disabled TLS validation, and generated-output scrubbing after support-safe acceptance evidence upload.
- Normalizes Nextcloud WebDAV non-finite quota values at the provider adapter boundary so unlimited, unknown, or uncomputed storage no longer breaks the OIDC-gated Files facade with HTTP 502.
- Omits one-time DAV secret fields entirely after credential creation across Files and Calendar list/revoke responses, preserving the revocable native-client contract without serializing credential placeholders.

## Security

- No post-`v0.1.0-rc.3` security release notes yet.

## Accessibility

- Accessibility and assistive-technology readiness remain evidence-gated per current milestone and release criteria; stale historical blocker wording has been removed from the release draft.

## Migration/Operator Notes

- No production provider cutover, migration apply, Terraform/live infrastructure change, or public production release has been performed after `v0.1.0-rc.3`.
- Sprint 30 phone dogfood uses the same profile-driven setup pipeline across profiles. `local-lan-dogfood` may be used for the first real iPhone test over LAN, but phone handoff rejects localhost, `127.0.0.1`, and Mac-only `.local` assumptions.
- Slack and Microsoft Teams remain commercial adapter readiness candidates only; adapter implementation, production migration, rollback, and customer-ready claims are blocked until future `implementation_allowed` and `release_ready` evidence exists.
- Operator backup/restore wording must reference `docs/operator-recovery-known-limitations.md`; Sprint 26 now allows only the scoped disposable fixture-domain restore proof claim, not production restore or E2EE lost-device recovery claims.

## Known Issues

- No active post-`v0.1.0-rc.3` release blocker is listed in this draft. Current blocker truth belongs to GitHub issues, milestones, and the release evidence gate.
