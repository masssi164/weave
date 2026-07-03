# Weave Monorepo

Weave is a provider-neutral collaboration suite for organizations that want to own their collaboration layer instead of being owned by one chat, file, calendar, task, identity, or AI provider.

It gives members stable Weave surfaces while giving admins and operators explicit control over provider posture, readiness, policy, migration risk, audit evidence, and governed AI assistance.

Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

## What It Is

Weave is a monorepo product stack for:

- stable member-facing collaboration across chat, files, calendar, tasks/boards, meetings, decisions, help, search, and support status;
- admin/operator control over identity, roles, provider readiness, capability rollout, migration posture, and support-safe evidence;
- provider-neutral domain contracts that keep Weave semantics stable while provider implementations change behind adapters;
- governed Weaver integration, where optional AI assistance can act only through Weave policy, approvals, audit, and MCP/domain tools.

Weave is not a homelab manager and not a promise that every provider is interchangeable today. Self-hosted provider labs and reference provider packs are evidence substrates for the product; the product boundary is Weave's contracts, member UX, admin control plane, and support-safe operational evidence.

## Who It Serves

- **Members:** one stable workspace even when the organization changes providers underneath.
- **Organization admins:** identity, role, policy, provider, readiness, and capability control before member go-live.
- **Operators:** bootstrap, readiness, backup/restore, support-bundle, migration, and recovery workflows with redacted evidence.
- **Governed Weaver users:** optional, disabled-by-default AI assistance constrained by user rights and organization-whitelisted capabilities.

## Product architecture

Weave separates product truth from implementation detail:

- **Canonical domains:** stable Weave capabilities such as chat, files, calendar, tasks, identity, search, audit, and admin/control-room.
- **Adapters and providers:** implementations with explicit posture, readiness, caveats, evidence, and migration boundaries.
- **MCP/domain tools:** the action surface for reads, writes, sends, deletes, provider switches, migrations, approvals, audit, and evidence.
- **Admin Control Room:** support-safe readiness, diagnostics, policy preview, provider posture, and next safe actions.
- **Weaver:** the governed AI assistant line; runtime execution is opt-in, auditable, disabled by default, and restricted to Weave-provided tools.

## Current Status

Weave is an active dogfood foundation with guarded portability and governance evidence.

Current work proves:

- provider-neutral contracts and member-facing capability boundaries;
- admin readiness and support-safe operational evidence;
- dogfood deployment and local/live evidence gates;
- governed Weaver policy boundaries and approval patterns.

Current work does not claim:

- public production release readiness;
- perfect lossless migration;
- broad provider interchangeability;
- production restore guarantees;
- broadly available autonomous agent operation.

Release and readiness claims are accepted only when their current evidence gates pass. Historical blockers and sprint notes belong in release docs and GitHub issues, not as stale warnings on this front page.

## Provider And Portability Reality

Provider reality vocabulary:

- `contract_only`
- `configured`
- `live_read`
- `live_write`
- `migration_dry_run`
- `migration_apply_ready`
- `rollback_ready`
- `release_ready`

No unaccounted data loss is the portability promise: unsupported fields, conflicts, archive-only records, provider-unexportable data, and rollback limits must be reported before guarded apply paths are promoted. perfect lossless migration is not claimed.

## Read Next

- [Glossary](docs/glossary.md)
- [Product architecture](docs/product-architecture.md)
- [Repository boundary](docs/repository-boundary.md)
- [Contract and docs index](docs/contract-docs-index.md)
- [v0.1 golden path](docs/v0.1-golden-path.md)
- [Release notes](docs/release-notes/index.md)
- [Developer handbook](docs/developer-handbook.md)

## Release notes

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

## Product screenshots

Screenshots are product assets for current dogfood-ready paths and should be read as UI evidence for those paths, not as broad availability claims for guarded capabilities.

- [Setup start](docs/assets/marketing/01-setup-start.svg)
- [Custom Weave chat](docs/assets/marketing/03-chat-room.svg)
- [Files and documents](docs/assets/marketing/04-files-documents.svg)
- [Settings and readiness](docs/assets/marketing/05-settings.svg)

## Repository layout

- `client/` - Flutter member UX and client contracts.
- `server/` - Weave-owned domain facades, authorization, audit, and provider boundaries.
- `admin-console/` - admin readiness, policy, setup, and control-room surfaces.
- `infra/` - Docker/OpenTofu operator stack, profiles, backup/restore, runtime lifecycle, and support bundles.
- `e2e/` - product-language scenarios and sanitized evidence mappings.
- `docs/` - product, architecture, operator, developer, release, and evidence docs.
- `release/` - release manifests, provider-lab evidence, and compatibility metadata.
- `weave-contract/` - shared Java contracts used by Weave and MCP tooling.
- `weave-mcp-server/` - Java MCP server exposing Weave MCP/domain tools.

## v0.1 product truth

The current product truth is dogfood-oriented and evidence-gated. Stable collaboration semantics, provider posture, governed actions, accessibility evidence, and support-safe operations are part of the product contract; public/production readiness remains gated by current release evidence and explicit release ownership.

## Ready / Guarded / Future claim matrix

This matrix stays short on purpose. Deeper evidence belongs in the [product-trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md), release notes, and issue-linked evidence.

| Claim | Status | Boundary |
| --- | --- | --- |
| Weave is a monorepo product stack. | **Ready foundation** | Client, server, admin console, infra, e2e, docs, and release gates ship together. |
| v0.1 is dogfood-production, not preview. | **Ready foundation** | Dogfood foundation only; no broad public production release claim. |
| Members work in provider-neutral Weave domains. | **Ready foundation** | Member UX uses Weave capabilities and states, not raw provider setup. |
| Provider adapters are replaceable behind Weave-owned contracts. | **Guarded** | Replacement needs adapter-specific portability, migration, rollback, exposure, and apply evidence. |
| No unaccounted data loss is the portability promise. | **Guarded** | Lossy fields and unsupported records must be reported; lossless migration is not promised. |
| Calls/meetings use LiveKit readiness today. | **Ready foundation** | Readiness is scoped to current dogfood evidence, not all meeting-provider claims. |
| Workspace/Admin Health is the support-safe readiness and diagnostics control plane. | **Ready foundation** | Raw provider diagnostics and secrets stay operator-only and redacted. |
| Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | **Guarded/future** | Runtime execution requires policy, approvals, revocation, audit, and explicit enablement. |
| Autonomous agent/team writes are available in v0.1. | **Future/blocked** | Write-like tools fail closed without governed approval evidence. |

Provider reality vocabulary: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, `release_ready`.

## Boards and provider boundary

Weave and Weaver are separate repositories with separate responsibilities:

- **Weave (`masssi164/weave`) owns product truth:** domains, adapters/providers, policy, approval rules, audit/evidence, bootstrap/control-plane setup, the Weave MCP/domain-tool server, and signed `WeaverRuntimeProfile` projection.
- **Weaver (`masssi164/weaver`) owns runtime truth:** the OpenClaw-derived per-user runtime, member-mode lockdown, signed profile consumption, generated OpenClaw config, the `weave-chat` channel plugin, and enforcement that all actions go back through Weave MCP/domain tools.
- The stable member entry point is the **Weave channel**. In Weave docs this means the product/channel contract and runtime-profile projection. In Weaver code this is implemented as the `weave-chat` channel plugin.
- Provider-native transports remain Weave backend `providerRef` values. Matrix, Slack, Teams, Telegram, iMessage, and similar transports must not become normal member-editable Weaver channel config.

See [repository boundary](docs/repository-boundary.md), [canonical-domain adapter/provider registry](docs/architecture/canonical-domain-adapter-registry.md), and [MCP/domain-tool action registry](docs/architecture/mcp-domain-tool-action-registry.md).

## Infrastructure and OpenTofu

Bootstrap is a Weave Control concern. It plans, validates, and applies the Control Plane = Weave Server + Admin Console, implemented in `server/` plus `admin-console/`, and optional provider stack according to profile and approval policy. It does not deploy the Flutter/member client and it does not configure Weaver directly. Weaver enablement is expressed through Weave policy, MCP/domain-tool grants, and signed runtime-profile projection.

See [Bootstrap foundation contract](docs/bootstrap-foundation-contract.md).

## Evidence contract

Weave evidence must be support-safe: no raw secrets, private prompts, member payloads, raw provider internals, or unredacted provider errors. Claims about readiness, provider exchange, accessibility, release status, Weaver availability, and production operation are accepted only when the relevant gate and evidence artifact say so.

## Release evidence

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->

## Common local gates

Use the smallest meaningful subset for a change, and `./gradlew ci` for cross-stack work.

```bash
python3 tools/product_architecture_claim_guard.py
python3 tools/product_architecture_claim_guard_test.py
python3 tools/domain_registry_check.py
python3 tools/docs_check.py
./gradlew acceptanceContract docsCheck releaseEvidenceCheck --console=plain
git diff --check
```

## Working agreements

Follow the [developer handbook](docs/developer-handbook.md), [PR workflow](docs/gitflow-pr-workflow.md), and [operating model](docs/weave-operating-model.md). Keep Weave product truth in this repository, Weaver runtime enforcement in `masssi164/weaver`, and provider-specific implementation details behind explicit adapter/provider evidence.

## Contract-First MCP Development

Weave member/Weaver-facing MCP contracts live in `weave-contract`. The Java `weave-mcp-server` exposes MCP tools from that shared metadata and delegates execution to `server/`. See [docs/weave-contract-java-mcp.md](docs/weave-contract-java-mcp.md) for module boundaries, Weaver projection guidance, Docker host networking, and smoke tests.
