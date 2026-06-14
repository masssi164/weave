# Weave Monorepo

**Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.**

Weave is a provider-neutral organization collaboration suite. Members work in stable Weave domains; admins and operators bind replaceable adapters, readiness, policy, audit, migration evidence, and support-safe diagnostics behind those domains.

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

## Product front door

Start with the [product architecture SSOT](docs/product-architecture.md), [canonical terminology](docs/glossary.md), [canonical domain registry](docs/domain-registry-v1.md), and [contract index](docs/contracts-index.md). The v0.1 operational path is the [golden path](docs/v0.1-golden-path.md).

Weave does **not** claim a universal compliance shield, universal sovereignty, broad provider interchangeability, public-production release approval, perfect lossless migration, or generally available autonomous AI writes. It makes adapter exchange and provider/jurisdiction exposure visible, then requires domain-specific evidence before stronger readiness or migration claims are promoted.

## Product architecture

- **Weave App**: member and guest surfaces for Spaces, chat, files, documents, calendars, boards/tasks, calls, decisions, notifications, profile, settings, and policy-visible capability states.
- **Weave Server**: Weave-owned domain facades, authorization, audit, provider registry, readiness, support-safe errors, portability reports, and migration boundaries.
- **Admin Console and `weavectl`**: organization setup, adapter selection, readiness, diagnostics, policy, approvals, support bundles, and evidence.
- **Adapters**: provider implementations behind canonical domains. Sovereignty/data-sovereignty posture belongs to each adapter/provider implementation and its evidence, not to domains as a blanket claim.
- **Weaver**: optional later per-user personal-assistant runtime. Weaver uses the Weave chat channel plus Weave-owned domain tools/facades. OpenClaw is a governed runtime/harness adapter candidate, not product truth.

## Ready / Guarded / Future claim matrix

| Claim | Status | Boundary |
| --- | --- | --- |
| Weave is a monorepo product stack. | **Ready foundation** | Client, server, admin console, infra, e2e, docs, and release gates ship together. |
| v0.1 is dogfood-production, not preview. | **Ready foundation** | Dogfood foundation only; no broad public production release claim. |
| Members work in provider-neutral Weave domains. | **Ready foundation** | Member UX uses Weave capabilities and states, not raw provider setup. |
| Provider adapters are replaceable behind Weave-owned contracts. | **Guarded** | Replacement needs adapter-specific portability, migration, rollback, exposure, and apply evidence. |
| No unaccounted data loss is the portability promise. | **Guarded** | Unsupported fields, lossy mappings, conflicts, archive-only data, and rollback limits must be reported; perfect lossless migration is not claimed. |
| Calls/meetings use LiveKit readiness today. | **Ready foundation** | Readiness is scoped to current dogfood evidence, not all meeting-provider claims. |
| Workspace/Admin Health is the support-safe readiness and diagnostics control plane. | **Ready foundation** | Raw provider diagnostics and secrets stay operator-only and redacted. |
| Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | **Guarded/future** | Runtime execution is not broadly available and requires policy, consent where required, approvals, revocation, and audit. |
| Autonomous agent/team writes are available in v0.1. | **Future/blocked** | Write-like tools fail closed without governed approval evidence. |

Provider reality vocabulary: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, and `release_ready`.

## Release notes

Published notes live in [v0.1 release notes](docs/release-notes/v0.1.md), with the audit in [v0.1.0-rc.3 release evidence](docs/release-v0.1-rc3-evidence.md). Maintainers update the managed block below with checked-in release-note tooling.

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

## Changed

- Public docs and README evidence pointers now identify `v0.1.0-rc.3` as the latest published prerelease and link the RC3 evidence audit.
- Sprint 32 post-PR-706 setup wording now treats the DNS-first `*.weave.test` onboarding baseline as implementation evidence while keeping Massimo-owned LAN validation and #591 manual assistive-technology signoff outside automated sprint completion.
- Sprint 21 product-reality gates now require free/self-hosted provider proof, explicit reality levels, and automated claim blocking before any customer-ready, Weaver-available, provider-interchangeable, production-rollback, or release-ready wording.

## Fixed

- No post-`v0.1.0-rc.3` bugfix release notes yet.

## Security

- Sprint 32 extends release-readiness claim control with negative checks for public/production release approval, full accessibility, broad provider interchangeability, production restore, and unsupported governed-PA availability wording; support evidence remains limited to redacted summaries that exclude credentials, provider bodies, private prompts, member data, and raw runtime settings.

## Accessibility

- Sprint 18 release-trust claim control keeps manual assistive-technology signoff explicitly blocked by #591; automated tests, support-safe artifacts, release notes, and green Live Stack E2E cannot substitute for real AT reviewer evidence.

## Migration/Operator Notes

- No production provider cutover, migration apply, Terraform/live infrastructure change, or public production release has been performed after `v0.1.0-rc.3`.
- Sprint 30 phone dogfood uses the same profile-driven setup pipeline across profiles. `local-lan-dogfood` may be used for the first real iPhone test over LAN, but phone handoff rejects localhost, `127.0.0.1`, and Mac-only `.local` assumptions.
- Slack and Microsoft Teams remain commercial adapter readiness candidates only; adapter implementation, production migration, rollback, and customer-ready claims are blocked until future `implementation_allowed` and `release_ready` evidence exists.
- Operator backup/restore wording must reference `docs/operator-recovery-known-limitations.md`; Sprint 26 now allows only the scoped disposable fixture-domain restore proof claim, not production restore or E2EE lost-device recovery claims.

## Known Issues

- #591 remains a release blocker: actual manual assistive-technology signoff is required before Sprint 18 milestone closure or public/production release signoff, unless release ownership explicitly splits the remaining manual signoff into a separate accepted blocker.
- #591 remains the active human/manual release blocker; #642 operator recovery is satisfied only for disposable fixture-domain restore evidence and does not authorize production restore claims.
<!-- WEAVE_RELEASE_NOTES_END -->


## Product screenshots

Screenshots are deterministic, checked-in assets for current dogfood-ready paths and must not imply guarded capabilities are shipped.

- [Setup start](docs/assets/marketing/01-setup-start.svg)
- [Custom Weave chat](docs/assets/marketing/03-chat-room.svg)
- [Files and documents](docs/assets/marketing/04-files-documents.svg)
- [Settings and readiness](docs/assets/marketing/05-settings.svg)

## Repository layout

- `client/` — Flutter member app, product UI, accessibility checks, localization, and client contracts.
- `server/` — Spring Boot product API/BFF, provider facades, authorization, audit, readiness, and backend contract tests.
- `admin-console/` — admin readiness, setup, policy, and posture surfaces.
- `infra/` — Docker/OpenTofu operator stack, deployment scripts, profiles, backup/restore, and support bundles.
- `e2e/` — product-language Gherkin scenarios and sanitized evidence contracts.
- `docs/` — MkDocs-backed product, admin/operator, developer, architecture, evidence, and release docs.
- `release/` — release manifests, stack compatibility metadata, and claim gates.

## v0.1 product truth

v0.1 is dogfood-production foundation, not a broad public production claim. Ready foundation covers the monorepo stack, provider-neutral domain vocabulary, guarded member surfaces, admin/operator readiness, dogfood infrastructure paths, and automated claim gates. Guarded/future work includes production provider cutover, broad commercial adapters, broad Weaver runtime execution, public/production release signoff, and manual assistive-technology signoff.

## Boards and provider boundary

Boards/tasks, chat, files, documents, calendar, calls, decisions, notifications, health, and Weaver use provider-neutral domain contracts. Provider names belong in admin/operator architecture examples and adapter candidates, not normal member-facing setup requirements.

## Infrastructure and OpenTofu

Bootstrap foundation ([contract](docs/bootstrap-foundation-contract.md)) is evidence-driven through `tools/weavectl bootstrap plan --profile <profile> --target <provider-lane>` followed by `tools/weavectl bootstrap apply --plan <plan-ref>`. Control Plane = Weave Server + Admin Console. Apply remains dry-run/validate-only unless an operator explicitly adds `--execute --approval-ref <approval-ref>`.

## Evidence contract

Evidence lives in specs, e2e mappings, release manifests, docs, claim gates, and deterministic tooling. Search and indexing are derived/rebuildable, not canonical business records. Domain contracts record objects, capabilities, open protocols, auth assumptions, audit requirements, portability/export, jurisdiction/vendor exposure, Weaver mode, and adapter candidates.

## Release evidence

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->

## Common local gates

Use the smallest meaningful subset for a change, and `./gradlew ci` for cross-stack work.

```bash
python3 tools/domain_registry_check.py
python3 tools/product_trust_claim_matrix_check.py
python3 tools/release_claim_matrix_check.py
python3 tools/docs_check.py
./gradlew acceptanceContract docsCheck releaseEvidenceCheck --console=plain
git diff --check
```

## Working agreements

Follow [Developer handbook](docs/developer-handbook.md), [Gitflow and PR workflow](docs/gitflow-pr-workflow.md), [Operating model](docs/weave-operating-model.md), and [Product line and Weaver plan](docs/product-line-and-weaver-plan.md). Keep provider-specific raw errors, secrets, tenant URLs, logs, diagnostics, private prompts, member content, and runtime details out of member UX and public claims.
