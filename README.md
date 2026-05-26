# Weave Monorepo

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

Weave is an accessibility-first, self-hostable organization operating layer for workspaces, communication, knowledge, provider governance, and operator health. Chat, files, shared calendars, boards/tasks, meetings, and decisions are Weave-owned product domains inside that operating layer, not a fixed bundle of provider screens.

Weave is provider-neutral by design: an organization can keep its chosen identity, chat, file, calendar, collaboration, and project systems while exposing them through coherent Weave product concepts. The default dogfood stack can use Keycloak, Matrix, Nextcloud, OpenProject, and LiveKit, but those are adapter/admin/operator choices behind Weave-owned domain contracts, not the member-facing product identity. Provider swaps are governed admin/operator capabilities that ship only when a supported migration contract, readiness evidence, authorization, audit trail, and rollback path exist.

This repository is the single source of truth for the product stack. Treat client, backend, infrastructure, acceptance evidence, documentation, and release metadata as one release unit. The active product-line reference is [Weave product line and Weaver integration plan](docs/product-line-and-weaver-plan.md). For a fast professional-demo review, start with the [v0.1 Golden Path readiness](docs/v0.1-golden-path.md) page; it summarizes what is ready, admin-setup-required, disabled, degraded, hidden, and evidenced. Documentation and README release-note embedding are tracked in [Manuals and release notes integration](docs/manuals-and-release-notes-integration.md).

## v0.1 Golden Path quick read

- **What it is:** a provider-neutral organization operating layer, not a fixed bundle of provider screens.
- **Member entry:** organization URL, invite link, or deep link after admin provisioning; no normal-member raw provider setup.
- **Demo spine:** Weave Home, personal messages, channels/workspaces, chat, files, boards/tasks, calendar, meetings, decisions, and support-safe health impact.
- **Admin/operator spine:** Workspace/Admin Health for provider categories, readiness, policy, backup/restore, smoke evidence, and support bundles.
- **Evidence posture:** live E2E is a standard release-evidence path on the dedicated live runner when scheduled/manual, not a solar-budget exception.

See [v0.1 Golden Path readiness](docs/v0.1-golden-path.md) for the concise status map and reviewer checklist.

## Product architecture

- **Domain-first provider facades:** clients consume Weave domains such as Chat, Files, Calendar, Boards/Tasks, Meetings, Decisions, and Health. Provider mapping, migrations, secrets, readiness, and lossy conversion details stay server/admin/operator side.
- **Admin-governed adapter control:** admins configure provider categories and readiness through supported admin/operator paths; members keep using Weave concepts instead of vendor-specific flows.
- **Evidence-scoped surfaces:** shipped member UX uses stable Weave states; guarded provider paths fail closed; future capabilities such as the governed Weaver PA runtime remain outside v0.1 until their contracts are promoted.
- **Accessible embedded manuals:** member help and admin/operator manuals are shipped from MkDocs and embedded in the client/admin console with the same design tokens and corporate design.
- **Release evidence in public docs:** release notes are generated from release metadata and injected into README/release surfaces by automation, then reviewed before publication.

## Release notes

The generated block below is the public README release-note draft for merged, unreleased changes. It tells readers what changed; it is not the release gate and it is not proof that a release was published. The block is updated from `docs/release-notes/unreleased.md` or generated release-note artifacts by the release workflow; do not hand-maintain the block contents outside the managed markers.

<!-- WEAVE_RELEASE_NOTES_START -->
_Generated release notes are review artifacts. A release maintainer may update this block with `python3 tools/readme_release_notes.py --update --source <generated-notes>` before opening the release-draft review._

Use this page for release-affecting changes that have merged but are not included in a tagged release yet.

## Added

- MkDocs documentation site foundation with handbook navigation, diagrams, GitFlow/PR workflow, and release notes process.
- Root Gradle wrapper and orchestration tasks for delegated server, client, admin, infra, docs, acceptance, CI, and release-notes checks.
- Local release notes generator for merged PR metadata grouped by release-notes labels.

## Changed

- Added organization-embedding, identity-provisioning, and provider-replacement strategy contracts to make provider neutrality, mixed self-hosted/cloud/external deployments, and adapter replacement explicit before new feature slices.
- Documentation validation now has a dedicated docs check/build path.
- PR CI now enforces exactly one release-notes label before review/merge.
- `make release-notes-check` now verifies release-notes label edge cases and generator fixture output.

## Fixed

- Nothing yet.

## Security

- Nothing yet.

## Accessibility

- Nothing yet.

## Migration/Operator Notes

- Operators can build the documentation site locally with `python3 -m pip install -r docs/requirements.txt` and `make docs-build`.

## Known Issues

- Nothing yet.
<!-- WEAVE_RELEASE_NOTES_END -->

## Product screenshots

These deterministic screenshots use checked-in Weave assets. They show the active product-maturity track through Weave-owned surfaces that are suitable for the README showcase: admin-provisioned setup, service endpoint review, custom chat, files, and settings/readiness. They are not a promise that roadmap or guarded provider surfaces are shipped; those stay separated in [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md).

Future README showcase updates should keep this acceptance checklist:

- Keep project naming in text (`# Weave Monorepo` and body copy), not image-only branding.
- Use existing deterministic screenshot assets with descriptive alt text that states the user task.
- Keep showcase surfaces limited to current dogfood-ready product paths (setup, endpoint review, chat, files, settings/readiness).
- Keep preview-only, guarded, or admin-only provider surfaces explicitly out of the normal user path and linked from roadmap docs instead.

### Admin-provisioned setup

[<img src="docs/assets/marketing/01-setup-start.svg" alt="Weave setup start screen inviting an admin to configure a self-hosted workspace." width="680">](docs/assets/marketing/01-setup-start.svg)

Setup is admin-provisioned; normal users join after provisioning and are not asked to configure raw providers.

### Service endpoint review

[<img src="docs/assets/marketing/02-review-service-endpoints.svg" alt="Weave setup review screen listing canonical local service endpoints before finishing configuration." width="680">](docs/assets/marketing/02-review-service-endpoints.svg)

Endpoint review keeps canonical identity, API, chat, files, and calendar surfaces explicit before the workspace is used. Concrete provider names belong in setup/readiness and operator docs, not as the member-facing product model.

### Custom Weave chat

[<img src="docs/assets/marketing/03-chat-room.svg" alt="Custom Weave chat room showing a release room conversation and accessible message composer." width="680">](docs/assets/marketing/03-chat-room.svg)

Chat is a Weave product experience backed by the selected chat provider. Matrix is the current dogfood provider; raw provider administration is not the normal user path.

### Weave files through the backend facade

[<img src="docs/assets/marketing/04-files-documents.svg" alt="Weave files screen listing folders and files through the backend files facade." width="680">](docs/assets/marketing/04-files-documents.svg)

Files use Weave-owned product routes and backend facades instead of promoting raw provider UX such as Nextcloud or SharePoint as the everyday UX.

### Settings and readiness

[<img src="docs/assets/marketing/05-settings.svg" alt="Weave settings screen showing saved local service configuration and sign-out controls." width="680">](docs/assets/marketing/05-settings.svg)

Settings and readiness surfaces should explain configured, disabled, degraded, or unsupported modules without exposing secrets or raw provider errors.

## Repository layout

- `client/` — Flutter app, product UI, accessibility checks, and client-side contract tests.
- `server/` — Spring Boot product API/BFF, provider facades, authorization, audit, support-safe errors, and backend acceptance tests.
- `infra/` — Docker/OpenTofu operator stack, local and single-host deployment scripts, provider profiles, backup/restore, smoke checks, and support bundles.
- `e2e/` — product-language Gherkin scenarios, scenario mappings, and sanitized evidence contracts.
- `docs/` — MkDocs-backed product, user/admin/operator/developer handbooks, architecture, release scope, acceptance flows, roadmap boundaries, release notes, and research notes.
- `release/` — release manifests and stack compatibility metadata.

## v0.1 product truth

v0.1 is a dogfood-production release, not a preview showcase. A surface belongs in the release only when it is useful for daily project work and backed by executable evidence. Preview-only or guarded surfaces stay fail-closed and out of the normal user UX until their product contract is promoted.

Required v0.1 surfaces:

- Admin-provisioned organization setup with provider categories for identity/IDM, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Weaver disabled by default until a later governed PA-runtime policy enables it.
- Weave Home for recent work, next actions, and health warnings.
- Channels as workspaces with accessible tabs for chat, files, boards/tasks, calendar, meetings, and decisions.
- Files through Weave-owned backend/product routes, not raw provider UX as the normal path.
- Workspace, team, and channel calendar events through the backend calendar facade.
- Boards/Tasks as an active Weave workspace facade with explicit user actions, authorization, audit, accessible non-drag task work, and fail-closed runtime gates.
- Meeting Capsules through the meetings facade when readiness proves the selected media provider path; the dogfood path uses the LiveKit token facade and keeps provider secrets, room tokens, media URLs, and recording/transcription/caption claims behind explicit evidence gates.
- Decision Ledger entries linked to workspace context.
- Workspace/Admin Health for readiness, degraded states, backups, smoke evidence, and support-bundle status.
- Deploy, update, backup, restore, rollback, smoke-test, and support-bundle paths for operators.

Out of v0.1:

- Product agent runtime integration. Weaver is a later governed per-user PA layer, not the foundation of the current product architecture.
- Autonomous, group, or team-scoped agent writes.
- Public connector SDK.
- Teams/Slack migration tooling.
- Full generic provider marketplace/admin console beyond the category seams needed for the current stack.
- Broad SaaS administration beyond safe self-hosting boundaries.

## Boards and provider boundary

Boards/Tasks is a Weave product surface. The client talks to the Weave backend facade and must not call OpenProject, Vikunja, Nextcloud Deck, or another task provider directly.

The current provider-backed validation path is OpenProject workspace sync:

- OpenProject is the first real provider-backed path for validating workspace-sync behavior.
- OpenProject is not the visible product UX and is not a direct client dependency.
- Provider reads remain behind runtime, Context/Space authorization, support-safe metadata, and backend-held tokens.
- Provider writes remain disabled/fail-closed unless a future promotion proves authorization, user consent, audit publication, support-bundle redaction, and rollback behavior.
- Local workspace user writes are v0.1 scope when they are explicit, authenticated, authorized, audited, and covered by tests.

## Infrastructure and OpenTofu

OpenTofu is the operator tool for Weave infrastructure.

The `infra/` tree still contains Terraform-compatible HCL internals where that naming is part of the ecosystem, such as `terraform {}` blocks, provider lock files, and `TF_VAR_*` environment variables. User-facing workflows, CI, and docs should use OpenTofu language and `tofu` commands unless they are explicitly describing compatibility details.

Infrastructure rules:

- CI uses `opentofu/setup-opentofu` and runs `tofu fmt`/validation-oriented checks.
- Operator scripts should use `${WEAVE_IAC_BIN:-tofu}` only when an explicit compatibility fallback is needed.
- State-destructive operations require operator confirmation plus a backup, restore, or rollback path.
- Support bundles must redact secrets, tokens, raw provider errors, provider URLs, cookies, private keys, and generated credentials.

## Evidence contract

Gherkin scenarios are product contracts, not decorative documentation.

For behavior changes:

1. Write or update the product scenario in `e2e/features/`.
2. Map it in `e2e/scenario_mappings.json`.
3. Add executable unit, contract, widget, integration, server, or infra evidence.
4. Keep live-stack E2E sparse and focused on critical end-to-end contracts.
5. Store only sanitized evidence artifacts. Never include secrets, tokens, cookies, private keys, raw provider errors, or personal data.

## Release evidence

Release evidence is the review and verification trail for release-note automation. It is intentionally separate from the public release-note draft above: the links and commands here show which checked-in artifacts and gates a maintainer must inspect before publishing release material.

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->

## Common local gates

Run the smallest meaningful gate for your change. `./gradlew ci` is the canonical cross-stack entry point; `make` targets are temporary compatibility aliases that delegate to Gradle during the transition:

```bash
make acceptance-contract
make docs-check
make client-ci
make server-ci
make infra-static
make ci

./gradlew doctor
./gradlew acceptanceContract
./gradlew docsCheck
./gradlew serverCi
./gradlew ci
```

Use these defaults:

- `make acceptance-contract` for Gherkin or scenario mapping changes.
- `make client-ci` for Flutter/client changes.
- `make server-ci` for backend/provider changes.
- `make infra-static` for infrastructure, operator scripts, and OpenTofu-facing changes.
- `make docs-check` for documentation site, diagrams, or release notes changes.
- `./gradlew ci` when a change crosses product-stack boundaries.
- `./gradlew releaseEvidenceCheck` for release notes, README release markers, and release-label evidence changes.

Live-stack E2E is intentionally opt-in. Run it only with explicit runner power/storage budget and sanitized evidence handling.

## Working agreements

- Keep user-facing documentation honest about what is shipped, gated, disabled, or future work.
- Prefer accessible headings and bullets over dense tables.
- Plan Weave product-first: organization setup, provider categories, admin policy, and readiness before Weaver/agent runtime work.
- Do not promote raw provider screens as Weave product UX.
- Do not expose provider secrets or service tokens to Flutter, support bundles, app config, or logs.
- Treat `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` changes as one coherent product story.
