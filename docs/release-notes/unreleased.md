# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet. `v0.1.0-rc.3` is the latest published prerelease; older post-RC2 entries moved into the versioned v0.1 release notes and RC3 evidence audit.

## Added

- Adds a versioned enterprise dogfood readiness manifest and ordered candidate chain covering exact-commit three-user collaboration, non-destructive persistent deployment, TestFlight distribution, and mandatory physical-iPhone VoiceOver signoff before any human-testing-ready or main-promotion claim.
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

- Separates process liveness, local backend readiness, and cached provider capability health; provider probes are single-flight, rate-limit aware, support-safe, and no longer run on every readiness poll.
- Normal member Files, Calendar, and Chat data planes are now documented coherently as OIDC-gated Weave WebDAV, CalDAV/iCalendar, and Matrix Client-Server facades over canonical domains; obsolete REST event/message data-plane access is not a compatibility target.
- Public docs and README evidence pointers now identify `v0.1.0-rc.3` as the latest published prerelease and link the RC3 evidence audit.
- Sprint 21 product-reality gates now require free/self-hosted provider proof, explicit reality levels, and automated claim blocking before any customer-ready, Weaver-available, provider-interchangeable, production-rollback, or release-ready wording.

## Fixed

- Bounds iPhone Simulator VM-service discovery and replays only the exact same-process launch event when Xcode 26.5 live unified logging misses it, preventing Live Stack E2E from hanging before application assertions without persisting the service URI or weakening product checks.
- Removes the obsolete first-run client path permanently, upgrades legacy stored state into the normal AppShell without discarding the current OIDC session, and exposes support-safe client build identity for in-place dogfood verification.
- Makes Live Stack collaboration use three disposable identities, fresh encrypted Matrix rooms, real cross-user Files/Calendar/Home observations, isolated authorization probes, exact artifact cleanup, a real Calendar outage/recovery fixture, and a fresh per-run iPhone Simulator.
- Preserves stable canonical context, channel, and meeting-thread identifiers across Calendar CalDAV query, read, sync, create, and update flows without leaking those northbound fields into southbound provider adapters.
- Preserves the device-bound mobile profile and saved OIDC refresh session when relaunch refresh fails transiently; only an explicit rejected refresh grant clears the unusable local network session.
- Allows ciphertext-only canonical Chat events to pass through the Rust Matrix `/sync` projection after encrypted sends, while plaintext message events still require a bounded non-empty body and supported Matrix message type.
- Makes Live Stack E2E reclaim only its restorable Flutter tool cache when the dedicated runner remains below the 10 GiB preflight, retains support-safe Rust phase codes when encrypted room or timeline loading fails, and emits Ruma-valid full room events from the Matrix `/messages` facade.
- Adds the Ruma `GET /_matrix/client/v3/rooms/{roomId}/members` projection required by the Rust Matrix SDK before encrypted send, using canonical Chat memberships without exposing a provider homeserver.
- Makes the dedicated Live Stack E2E runner self-maintaining with a 10 GiB initial gate and a 5 GiB pre-test gate that preserves 4 GiB usable headroom plus a monitored 1 GiB recovery reserve, complete targeted stack teardown, sandbox-safe generated public CA injection into Rust without Keychain mutation or disabled TLS validation, and generated-output scrubbing after support-safe acceptance evidence upload.
- Normalizes Nextcloud WebDAV non-finite quota values at the provider adapter boundary so unlimited, unknown, or uncomputed storage no longer breaks the OIDC-gated Files facade with HTTP 502.
- Omits one-time DAV secret fields entirely after credential creation across Files and Calendar list/revoke responses, preserving the revocable native-client contract without serializing credential placeholders.

## Security

- Preserves Nextcloud brute-force protection while correcting stable backend credentials and exact trusted-proxy forwarding, and records only sanitized cached provider-health, authorization, identity-hash, and security-audit evidence.

## Accessibility

- Makes Home, Chat, Files, Calendar, Settings, Profile, session upgrade, and physical-device VoiceOver acceptance individually release-blocking for the enterprise dogfood candidate.
- Accessibility and assistive-technology readiness remain evidence-gated per current milestone and release criteria; stale historical blocker wording has been removed from the release draft.

## Migration/Operator Notes

- Persistent dogfood deployment now runs twice under a non-cancelling lock, verifies OpenTofu idempotency plus human-subject, Mailpit, TLS, and active-session invariants, and never creates or resets disposable automation identities in that environment.
- No production provider cutover, migration apply, Terraform/live infrastructure change, or public production release has been performed after `v0.1.0-rc.3`.
- Sprint 30 phone dogfood uses the same profile-driven setup pipeline across profiles. `local-lan-dogfood` may be used for the first real iPhone test over LAN, but phone handoff rejects localhost, `127.0.0.1`, and Mac-only `.local` assumptions.
- Slack and Microsoft Teams remain commercial adapter readiness candidates only; adapter implementation, production migration, rollback, and customer-ready claims are blocked until future `implementation_allowed` and `release_ready` evidence exists.
- Operator backup/restore wording must reference `docs/operator-recovery-known-limitations.md`; Sprint 26 now allows only the scoped disposable fixture-domain restore proof claim, not production restore or E2EE lost-device recovery claims.

## Known Issues

- No active post-`v0.1.0-rc.3` release blocker is listed in this draft. Current blocker truth belongs to GitHub issues, milestones, and the release evidence gate.
