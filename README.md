# Weave

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="180">
</p>

Provider-neutral collaboration for organizations that need control, portability, and governed assistance.

**Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.**

Weave is a provider-neutral collaboration suite and governed workspace for organizations that want collaboration to stay portable, reviewable, and under their own rules.

It gives members one place for chat, files, calendars, tasks, decisions, meetings, help, and workspace context, while giving admins and operators a controlled way to connect providers, review readiness, keep evidence support-safe, and change direction without pretending provider changes are risk-free.

Weaver, the AI assistance layer, follows that product order: optional, policy-bound, auditable, and disabled until an organization chooses to enable it.

## Enterprise Workflow

1. **Buyer and transformation lead** align the collaboration domains that matter: identity, chat, files, calendar, boards/tasks, meetings, decisions, and governed assistance.
2. **Admin and operator** prepare the organization through one control path: connect provider categories, review readiness, preview policy impact, and keep diagnostics and evidence support-safe before member go-live.
3. **Member** enters through an organization URL, invite link, or deep link, lands in Weave without raw provider setup, and works through stable Weave surfaces for daily collaboration.
4. **Governance and change** stay explicit: provider changes are reviewed through dry-run evidence, approvals, audit, and member-impact boundaries before they become rollout decisions.

## Product Screenshots

These checked-in visuals are support-safe proof assets for the current dogfood path. They show what Weave can demonstrate today for setup, member work, and workspace governance. The evidence manifest is [docs/assets/screenshot-evidence.json](docs/assets/screenshot-evidence.json).

- [Admin setup start](docs/assets/marketing/01-setup-start.svg): guided workspace setup for admins preparing a Weave organization.
- [Service review](docs/assets/marketing/02-review-service-endpoints.svg): support-safe provider endpoint review before setup completion.
- [Chat room](docs/assets/marketing/03-chat-room.svg): member chat with message history, workspace context, and a send action.
- [Files and documents](docs/assets/marketing/04-files-documents.svg): document and folder surface with accessible file actions.
- [Settings](docs/assets/marketing/05-settings.svg): account session controls and configured workspace services.

<p align="center">
  <a href="docs/assets/marketing/01-setup-start.svg"><img src="docs/assets/marketing/01-setup-start.svg" alt="Guided Weave workspace setup screen for admins preparing a workspace." width="420"></a>
  <a href="docs/assets/marketing/03-chat-room.svg"><img src="docs/assets/marketing/03-chat-room.svg" alt="Weave chat room with message history, workspace context, and a send action." width="420"></a>
</p>

## What Works Today

- The current frontdoor proves a provider-neutral member path with guided setup, service review, chat, files, and settings visuals backed by checked-in evidence.
- Weave treats admin/operator readiness as part of the product: provider categories, policy boundaries, evidence, and support-safe diagnostics belong in the control plane, not in member setup.
- The release track already carries product-level proof for dogfood collaboration, governed assistance boundaries, portability dry-runs, operator recovery guardrails, and release-claim control.

## What Is Guarded

Weave is in active dogfood and does not claim public production readiness. The portability promise is no unaccounted data loss; perfect lossless migration is not claimed. Provider changes remain a governed dry-run and review path, so universal provider interchangeability is not claimed. Weaver remains optional and policy-bound, and unrestricted autonomous agents are not part of the current public claim.

The detailed boundary lives in the [product trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md), the [provider portability docs](docs/architecture/provider-portability.md), and the [roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md).

## For Members

Members should experience Weave as one workspace instead of a tour through provider setup. The current product path starts with an organization entry point, then moves into Weave-owned collaboration surfaces and clear capability states such as `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`, or `unsupported`.

Start with the [user handbook](docs/user-handbook.md) and the [v0.1 golden path](docs/v0.1-golden-path.md).

## For Admins And Operators

Admins and operators use Weave as the governance layer for provider choice, readiness, policy, evidence, and change control. The [Bootstrap foundation](docs/bootstrap-foundation-contract.md) explains how the control plane is staged, while the admin/operator docs explain readiness, evidence handling, and support-safe operations.

Start with the [admin/operator handbook](docs/admin-operator-handbook.md), [Bootstrap foundation](docs/bootstrap-foundation-contract.md), [quality and evidence guide](docs/quality-and-evidence.md), and [provider portability](docs/architecture/provider-portability.md).

## For Developers

Developers should treat this repository as implementation and evidence truth, with product/domain truth pinned through the spec corpus. The shortest path in is the [developer handbook](docs/developer-handbook.md), followed by the [PR workflow](docs/gitflow-pr-workflow.md), the [operating model](docs/weave-operating-model.md), and [spec-driven development](docs/spec-driven-development.md).

## Release Notes

The frontdoor keeps the current release track visible here; the full chronology stays in the versioned release notes and evidence docs.

- **Published prerelease, 2026-06-01:** [`v0.1.0-rc.3`](docs/release-v0.1-rc3-evidence.md) added the provider-neutral suite foundation, Admin/Workspace Health readiness boundary, first governed Weaver slice, and green CI plus Live Stack evidence for that candidate.
- **Guarded Beta slice, refreshed 2026-06-18:** the [Sprint 32 closure report](docs/sprint-32-closure-report.md) captures Admin readiness preview, adapter-continuity dry-run, approval-required Weaver actions, member Client + Weaver flow, and Admin + User + Weaver E2E/accessibility smoke. It is ready for #836 review, not an overall Sprint 32 completion claim.
- **Active dogfood stream:** [Unreleased](docs/release-notes/unreleased.md) tracks current merged highlights, including free-provider lab coverage, provider-switch contract gates, human validation gates, commercial-adapter readiness guards, operator recovery guardrails, and refreshed Beta evidence.

<!-- WEAVE_RELEASE_NOTES_START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Latest release index: [Release notes](docs/release-notes/index.md)
<!-- WEAVE_RELEASE_NOTES_END -->

## Release Evidence

Every public claim in this README is supposed to terminate in a support-safe artifact, release note, or claim-boundary document. Release evidence stays separate from marketing copy so the frontdoor can stay readable while reviewers still have a precise path to the underlying proof.

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline release-note fixture review artifact: `build/release-notes/unreleased.md`
- Release evidence check: deterministic CI/local gate for README markers, release-note structure, label policy, and release evidence fixtures.
<!-- WEAVE_RELEASE_NOTES:END -->
