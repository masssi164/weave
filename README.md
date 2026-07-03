# Weave

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="180">
</p>

Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.

Weave is a provider-neutral collaboration suite and governed workspace for organizations that want collaboration to stay portable, reviewable, and under their own rules.

Members get one place for chat, files, calendars, tasks, decisions, meetings, help, and workspace context. Admins get a controlled way to connect providers, review readiness, keep evidence, and change direction without pretending provider changes are risk-free.

Weaver, the AI assistance layer, is part of that direction: optional, policy-bound, auditable, and disabled until an organization chooses to enable it.

Weave is in active dogfood. The current release track proves the foundation with real product paths and guarded claims; it does not claim public production readiness, lossless provider migration, unrestricted autonomous agents, or universal provider interchangeability.

## Product Screenshots

These screenshots are checked-in product evidence for current workspace paths. The evidence manifest is [docs/assets/screenshot-evidence.json](docs/assets/screenshot-evidence.json).

<p align="center">
  <a href="docs/assets/marketing/03-chat-room.svg"><img src="docs/assets/marketing/03-chat-room.svg" alt="Weave chat room showing message history, linked workspace context, and a send action." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/04-files-documents.svg"><img src="docs/assets/marketing/04-files-documents.svg" alt="Weave files view showing documents, folders, and accessible file actions." width="560"></a>
</p>

<p align="center">
  <a href="docs/assets/marketing/05-settings.svg"><img src="docs/assets/marketing/05-settings.svg" alt="Weave settings view showing account session controls and configured workspace services." width="560"></a>
</p>

## What Works Today

- Guided setup, chat, files, and settings have current product evidence.
- Admin/operator readiness is treated as part of the product, not as a pile of raw provider diagnostics.
- Provider integrations sit behind Weave-owned domains so the member experience does not become provider setup.
- Release claims are checked by CI, evidence manifests, and claim-boundary tests before they are allowed to stand.

## What Is Guarded

- Provider replacement is a governed workflow, not a blanket promise that every provider can be swapped today.
- The portability promise is no unaccounted data loss; perfect lossless migration is not claimed.
- Weaver assistance is policy-bound and optional; broad autonomous write access is not part of the current public claim.
- Production release readiness requires current release evidence, accessibility evidence, and explicit blocker review.

The detailed claim boundary lives in the [product trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md), the [provider portability docs](docs/architecture/provider-portability.md), and the [release notes](docs/release-notes/index.md).

## For Members

Weave should feel like one workspace instead of a tour through provider setup screens. Members see stable Weave capabilities and clear states: available, disabled by policy, not configured, degraded, unavailable, coming later, or unsupported.

Start with the [user handbook](docs/user-handbook.md) and the [v0.1 golden path](docs/v0.1-golden-path.md).

## For Admins And Operators

Weave is designed so setup and operations are reviewable: connect providers, inspect readiness, keep secrets out of support evidence, understand migration consequences, and avoid customer-facing claims that the current evidence does not support.

Start with the [admin/operator handbook](docs/admin-operator-handbook.md), [Bootstrap foundation](docs/bootstrap-foundation-contract.md), and [quality and evidence guide](docs/quality-and-evidence.md).

## For Developers

Start with the [developer handbook](docs/developer-handbook.md). Use the smallest meaningful gate for the change; use the full CI gate for cross-stack work.

```bash
./gradlew docsCheck releaseEvidenceCheck
python3 tools/readme_release_notes.py --check
python3 tools/screenshot_evidence_check.py
git diff --check
```

Keep specs unchanged unless the task is explicitly a spec-corpus or conformance update.

## Release Notes

Release notes live in [docs/release-notes](docs/release-notes/index.md). The README keeps only a compact pointer so the project frontdoor stays readable.

<!-- WEAVE_RELEASE_NOTES_START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Latest release index: [Release notes](docs/release-notes/index.md)
<!-- WEAVE_RELEASE_NOTES_END -->

## Release Evidence

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->
