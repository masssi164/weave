# Weave

Weave is a provider-neutral collaboration suite for organizations that want one stable work layer across chat, files, calendars, tasks, meetings, decisions, help, identity, operations, and governed AI assistance.

Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

## What Weave Is

Weave gives members a consistent workspace while admins and operators keep control of provider choices, readiness, policy, migration risk, audit evidence, and support-safe operations.

The product is built around Weave-owned domains, not around one fixed provider stack. Current dogfood evidence uses concrete providers where useful, but those providers are evidence substrates behind Weave contracts. They are not the public product boundary.

Governed Weaver assistance is part of the product direction: optional, disabled by default, auditable, constrained by user rights and organization-whitelisted capabilities, and never a claim of broad autonomous agent operation.

The [Bootstrap foundation](docs/bootstrap-foundation-contract.md) keeps setup ownership explicit: Control Plane = Weave Server + Admin Console, while provider-stack infrastructure is profile-driven and evidence-gated.

## Current Maturity

Weave is an active dogfood foundation with guarded portability and governance evidence. Current work proves provider-neutral contracts, member capability boundaries, admin readiness, support-safe evidence, dogfood deployment paths, and governed Weaver policy boundaries.

It does not claim public production release readiness, perfect lossless migration, broad provider interchangeability, production restore guarantees, or broadly available autonomous agent operation. Release and readiness claims are accepted only when current evidence gates pass.

## Product Screenshots

These checked-in SVG assets show current dogfood-ready paths. Treat them as UI evidence for those paths, not as broad availability claims for guarded capabilities.

<p align="center">
  <a href="docs/assets/marketing/01-setup-start.svg"><img src="docs/assets/marketing/01-setup-start.svg" alt="Weave setup start screen showing guided workspace setup for a dogfood organization." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/02-review-service-endpoints.svg"><img src="docs/assets/marketing/02-review-service-endpoints.svg" alt="Weave setup endpoint review showing support-safe service URLs before setup completion." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/03-chat-room.svg"><img src="docs/assets/marketing/03-chat-room.svg" alt="Weave chat room showing message history and a send action in a current dogfood path." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/04-files-documents.svg"><img src="docs/assets/marketing/04-files-documents.svg" alt="Weave files view showing documents, folders, and accessible file actions." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/05-settings.svg"><img src="docs/assets/marketing/05-settings.svg" alt="Weave settings view showing account session controls and configured organization endpoints." width="560"></a>
</p>

## Ready / Guarded / Future Claim Matrix

This matrix is intentionally short. Deeper evidence belongs in the [product-trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md), [release notes](docs/release-notes/index.md), and issue-linked evidence.

| Claim | Status | Boundary |
| --- | --- | --- |
| Weave ships as one product stack. | Ready foundation | Client, server, admin console, infra, e2e, docs, and release gates are coordinated together. |
| v0.1 is dogfood-production, not preview. | Ready foundation | Dogfood foundation only; no broad public production release claim. |
| Members work in provider-neutral Weave domains. | Ready foundation | Member UX uses Weave capabilities and states, not raw provider setup. |
| Provider adapters are replaceable behind Weave-owned contracts. | **Guarded** | Replacement needs adapter-specific portability, migration, rollback, exposure, and apply evidence. |
| No unaccounted data loss is the portability promise. | Guarded | Lossy fields and unsupported records must be reported; perfect lossless migration is not claimed. |
| Calls/meetings use LiveKit readiness today. | Ready foundation | Readiness is scoped to current dogfood evidence, not all meeting-provider claims. |
| Workspace/Admin Health is the support-safe readiness and diagnostics control plane. | Ready foundation | Raw provider diagnostics and secrets stay operator-only and redacted. |
| Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | Guarded/future | Runtime execution requires policy, approvals, revocation, audit, and explicit enablement. |
| Autonomous agent/team writes are available in v0.1. | Future/blocked | Write-like tools fail closed without governed approval evidence. |

Provider reality vocabulary: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, `release_ready`.

## Read Next

- [Documentation home](docs/index.md)
- [v0.1 golden path](docs/v0.1-golden-path.md)
- [Canonical domains](docs/architecture/canonical-domains.md)
- [Provider portability](docs/architecture/provider-portability.md)
- [No-unaccounted-data-loss boundary](docs/architecture/no-unaccounted-data-loss.md)
- [Admin/operator handbook](docs/admin-operator-handbook.md)
- [Bootstrap foundation](docs/bootstrap-foundation-contract.md)
- [User handbook](docs/user-handbook.md)
- [Release notes](docs/release-notes/index.md)
- [Developer handbook](docs/developer-handbook.md)

## Developer Quickstart

For local contribution, start with the [developer handbook](docs/developer-handbook.md). The root Gradle wrapper is the orchestration entry point; use the smallest meaningful gate for the change.

```bash
./gradlew docsCheck releaseEvidenceCheck
python3 tools/readme_release_notes.py --check
python3 tools/release_trust_claim_control_check.py
python3 tools/product_trust_claim_matrix_check.py
git diff --check
```

Use `./gradlew ci` for cross-stack changes. Keep specs unchanged unless the task is explicitly a spec-corpus or conformance update.

## Release Notes

Generated unreleased notes are kept here for release-review traceability. The durable release notes index is [docs/release-notes/index.md](docs/release-notes/index.md).

<details>
<summary>Show generated unreleased draft</summary>

<!-- WEAVE_RELEASE_NOTES_START -->
_Generated release notes are review artifacts. A release maintainer may update this block with `python3 tools/readme_release_notes.py --update --source <generated-notes>` before opening the release-draft review._

Use this page for release-affecting changes that have merged but are not included in a tagged release yet. `v0.1.0-rc.3` is the latest published prerelease; older post-RC2 entries moved into the versioned v0.1 release notes and RC3 evidence audit.

## Added

- Sprint 22 adds a CI-safe free provider lab gate, manifests, fixture evidence, and operator runbook for Keycloak, Authentik, Matrix/Synapse, Zulip, Nextcloud, MinIO, Radicale, OpenProject, and the Docker Runtime boundary without claiming provider interchangeability or release readiness.
- Sprint 23 adds a CI-safe Chat Provider Switch contract gate for Matrix/Synapse to Zulip canonical object coverage, fixture dry-run/apply evidence, rollback-honesty classification, LossyFieldReport enforcement, scoped claim gating, and support-safe ProviderRef redaction without claiming lossless migration, production apply, production rollback, release readiness, or provider interchangeability.
- Sprint 24 adds a guarded Weaver Runtime Factory provider-lab fixture and gate for per-user runtime lifecycle, desired-state reconciliation, isolation, support-safe redaction, revoke, and claim safety without claiming production PA availability, customer-ready Weaver, release-ready Weaver, or broad autonomous AI availability.
- Sprint 29 adds executable pre-human release validation guards, human UX/accessibility and Weaver evidence templates, and a final decision guard that blocks release-ready wording until automated evidence, human signoff, and release-blocker checks all pass.
- Sprint 28 adds commercial adapter readiness specs, a go/no-go matrix, and a CI guard that keeps Slack and Microsoft Teams implementation starts blocked until provider-specific proof, admin consent, cost, export, retention, and rollback evidence exist. It does not claim Slack or Teams integration availability.
- Sprint 26 adds operator recovery guardrails plus support-safe disposable Backup -> Destroy -> Restore -> Validate evidence for fixture domain data; production restore remains operator-approved and private-evidence scoped.
- Sprint 30 adds the hot-phase dogfood readiness evidence pack, exact agentic AI slogan guard, profile-driven setup fixture for dev/LAN dogfood/public dogfood/production, and governed Weaver contracts for policy, mobile approvals, audit, revocation, and privacy boundaries without claiming public production readiness.
- Sprint 32 adds guarded Beta readiness claim gates, adapter-continuity dry-run evidence, Admin readiness preview, governed Weaver approval-boundary evidence, member Client + Weaver flow, Admin + User + Weaver E2E/accessibility smoke evidence, and refreshed release/demo evidence.

## Changed

- Public docs and README evidence pointers now identify `v0.1.0-rc.3` as the latest published prerelease and link the RC3 evidence audit.
- Sprint 21 product-reality gates now require free/self-hosted provider proof, explicit reality levels, and automated claim blocking before any customer-ready, Weaver-available, provider-interchangeable, production-rollback, or release-ready wording.

## Fixed

- No post-`v0.1.0-rc.3` bugfix release notes yet.

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
<!-- WEAVE_RELEASE_NOTES_END -->

</details>

## Release Evidence

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->
