# Weave Monorepo

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

**Weave lets organizations own their collaboration layer.** Members work in stable Weave domains such as chat, files, documents, calendar, boards, calls, decisions, notifications, and health. Admins and operators control provider adapters, readiness, policy, migration, audit, backup/restore, and support evidence.

Weave is not a re-skinned provider bundle. It is a sovereign, accessibility-first organization suite where provider adapters sit behind Weave-owned domain contracts. Normal members see capability states and work surfaces; they do not configure raw provider endpoints, secrets, OIDC clients, migration tools, or diagnostics.

## Why Weave exists

Most collaboration stacks force organizations to choose between vendor lock-in, fragmented self-hosted tools, or member-facing setup complexity. Weave's position is different:

- **Domains before providers.** Chat, files, documents, calendar, boards, calls, decisions, notifications, people, spaces, and health are product contracts. Matrix, Nextcloud, Keycloak, OpenProject, LiveKit, Microsoft 365, Slack, Teams, SharePoint, Jira, and other systems are adapters behind those contracts.
- **Provider portability without overclaiming.** Weave uses a **no unaccounted data loss** principle: exports, imports, lossy fields, conflicts, unsupported provider features, and rollback limits must be reported and approved. It does not claim magical perfect migration.
- **Admin/operator control plane.** Provider choice, readiness, policy, audit, support-safe diagnostics, backups, restore drills, and release evidence belong in the admin/operator layer.
- **Evidence-scoped maturity.** A claim is ready only when the repo, docs, CI, support-safe evidence, or guarded/future status backs it.
- **Weaver later, governed by Weave.** Weaver is the optional personal-assistant line: OpenClaw-derived, per-user, isolated, policy-generated, auditable, disabled by default, and blocked until admin enablement, member opt-in, approval, sandbox, and audit gates exist.

## Product architecture

Weave ships as one product unit: `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` move together. Members consume Weave domains through client and backend facades. Admins/operators govern providers, policy, readiness, diagnostics, backup/restore, and release evidence. Concrete providers are adapters behind server-owned contracts, not Flutter-to-provider product dependencies.

For the full architecture path, see [Architecture](docs/architecture.md), [Canonical domains](docs/architecture/canonical-domains.md), [Canonical feature models](docs/canonical-feature-models.md), and [Diagrams](docs/diagrams/index.md).

## Canonical domains

The current foundation defines these Weave-owned domains:

| Domain | What it owns |
| --- | --- |
| `identity` | Authentication subjects, groups, roles, sessions, and capability profiles. |
| `people` | People, memberships, profiles, guests, service accounts, and contact visibility. |
| `spaces` | The cross-domain anchor for teams, projects, rooms, channels, boards, calendars, file roots, calls, and decisions. |
| `chat` | Conversations, messages, threads, reactions, presence, memberships, and attachments. |
| `files` | Drives, folders, files, versions, permissions, shares, locks, and quotas. |
| `documents` | Collaborative document metadata, edit sessions, comments, suggestions, coauthoring, and exports. |
| `calendar` | Team/channel calendars, events, availability, resources, recurrence, and meeting bindings. |
| `boards` | Boards, lists, tasks, statuses, assignments, comments, dependencies, and custom fields. |
| `calls` | Meeting sessions, join grants, participants, recording/caption policy, and consent. |
| `decisions` | Durable proposals, approvals, rationale, evidence references, and supersession. |
| `notifications` | Preferences, subscriptions, delivery channels, digests, and delivery evidence. |
| `health` | Readiness, diagnostics, support bundles, backup/restore, release evidence, and operator actions. |
| `weaver` | Optional per-user assistant runtime profiles, tool grants, approvals, audit, and opt-in state. |

See [Canonical domains](docs/architecture/canonical-domains.md), [Provider portability contract](docs/architecture/provider-portability.md), and [Weaver OpenClaw-derived runtime profile](docs/architecture/weaver-openclaw-profile.md).

## Provider adapters and data sovereignty

Weave can run with a self-hosted dogfood stack, an existing enterprise stack, or a hybrid stack. Provider names are implementation choices, not the member-facing product model.

Examples:

- **Dogfood/default posture:** Keycloak for identity, Matrix/Synapse for chat, Nextcloud for files/calendar backing, OpenProject for boards validation, and LiveKit for calls readiness.
- **Existing-provider posture:** Entra ID, Teams, SharePoint/OneDrive, Microsoft 365, Jira/Planner, Slack, or other adapters can be modeled behind the same domains once adapter manifests, readiness, risk notes, and migration evidence exist.
- **Hybrid posture:** An organization can combine self-hosted identity with external chat/files/tasks providers while preserving Weave capability states and admin/operator risk visibility.

Every adapter must publish a support-safe manifest, mapping table, readiness checks, unsupported-field notes, migration limits, audit events, and a secret boundary. Provider switches remain admin/operator actions until dry-run, apply, rollback/retention, authorization, audit, and member-impact evidence are implemented for that domain. Sprint 15 proves the organization-embedding and provider-neutral replacement boundary with Matrix Chat as the first dry-run consequence slice; production apply/cutover and E2EE history migration remain blocked.

## Admin/operator control plane

The Weave Client is the member surface. The Organization/Admin Console and operator documentation own organization bootstrap and governance:

- identity/IDM setup, group and role mapping, guests, service principals, deprovisioning, and break-glass posture;
- provider category selection, endpoint rotation, readiness, and support-safe diagnostics;
- deny-by-default capability policy, RBAC profiles, whitelisting, and audit;
- backup, restore, support bundles, release-candidate evidence, and waiver rules;
- future Weaver tool/capability allowlists and runtime readiness blockers.

Start with the [Admin/Operator Handbook](docs/admin-operator-handbook.md), [Organization embedding contract](docs/organization-embedding-contract.md), [Identity provisioning strategy](docs/identity-provisioning-strategy.md), [Admin-Suite readiness and setup contract](docs/admin-suite-readiness-setup-contract.md), and [Control-plane infra bootstrap](docs/control-plane-infra-bootstrap.md).

## v0.1 product truth

v0.1 is dogfood-production, not preview. A normal member should see Weave-owned work surfaces and effective capability states, not raw provider configuration, service secrets, provider diagnostics, or scaffold copy. Guarded surfaces remain fail-closed until their evidence exists. Future surfaces, including Weaver runtime execution, stay explicitly future/disabled rather than half-shipped.

## Ready / Guarded / Future claim matrix

| Claim | Status | Evidence or boundary |
| --- | --- | --- |
| Weave is a monorepo product stack with client, server, infra, e2e, docs, and release gates. | **Ready** | [Developer Handbook](docs/developer-handbook.md), [Build/evidence delivery system](docs/build-evidence-delivery-system.md), `./gradlew ci`. |
| v0.1 is dogfood-production, not preview/scaffold UX for normal members. | **Ready** | [v0.1 Golden Path readiness](docs/v0.1-golden-path.md), [ISO 9241-110 dogfood UX gate](docs/iso-9241-110-dogfood-ux-gate.md). |
| Members work in provider-neutral Weave domains. | **Ready foundation** | [Canonical domains](docs/architecture/canonical-domains.md), [Canonical feature models](docs/canonical-feature-models.md), [Architecture](docs/architecture.md). |
| Normal members do not configure raw providers, secrets, OIDC clients, or diagnostics. | **Ready boundary** | [Admin-provisioned first use](docs/admin-provisioned-first-use.md), [Admin/Operator Handbook](docs/admin-operator-handbook.md). |
| Provider adapters are replaceable behind Weave-owned contracts. | **Guarded** | [Provider portability contract](docs/architecture/provider-portability.md), [Provider replacement and anti-silo contract](docs/provider-replacement-and-anti-silo-contract.md). Provider reality levels (`contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, `release_ready`) prevent contract-only candidates from being marketed as generally available; replacement apply is limited to domains with explicit dry-run/apply/rollback evidence. |
| No unaccounted data loss is the portability promise. | **Guarded** | [Provider portability contract](docs/architecture/provider-portability.md). Unsupported fields, conflicts, archive-only data, and provider-unexportable data must be reported; perfect lossless migration is not claimed. |
| Calls/meetings use LiveKit readiness today. | **Guarded** | [Meeting architecture decision record](docs/meeting-architecture-decision.md). Join/media claims remain limited to implemented readiness and token facade evidence. |
| Document editing/Office launch is generally available. | **Not shipped** | Office remains `contract_only`/`coming_later` until a backend-owned runtime, callback URL, JWT/session secret, storage binding, permission model, health check, and release gates exist. The disabled provider fails closed and exposes only support-safe readiness. |
| Workspace/Admin Health is the support-safe readiness and diagnostics control plane. | **Ready foundation / expanding** | [Admin-Suite readiness and setup contract](docs/admin-suite-readiness-setup-contract.md), [Quality and acceptance evidence](docs/quality-and-evidence.md). |
| Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | **Future foundation** | [Weaver OpenClaw-derived runtime profile](docs/architecture/weaver-openclaw-profile.md), [Product line and Weaver plan](docs/product-line-and-weaver-plan.md). Runtime execution is not shipped. |
| Autonomous agent/team writes are available in v0.1. | **Not shipped** | [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md). Future writes require admin-approved tools, member opt-in where required, approval policy, audit, sandbox, and support-safe evidence. |

## Release notes

The managed block below is the README-facing draft of merged changes after the latest tagged prerelease. The published `v0.1.0-rc.3` notes live in [v0.1 release notes](docs/release-notes/v0.1.md), with the audit in [v0.1.0-rc.3 release evidence](docs/release-v0.1-rc3-evidence.md). Maintainers update this block with checked-in release-note tooling; do not edit inside the markers by hand.


<!-- WEAVE_RELEASE_NOTES_START -->
_Generated release notes are review artifacts. A release maintainer may update this block with `python3 tools/readme_release_notes.py --update --source <generated-notes>` before opening the release-draft review._

Use this page for release-affecting changes that have merged but are not included in a tagged release yet. `v0.1.0-rc.3` is the latest published prerelease; older post-RC2 entries moved into the versioned v0.1 release notes and RC3 evidence audit.

## Added

- Sprint 22 adds a CI-safe free provider lab gate, manifests, fixture evidence, and operator runbook for Keycloak, Authentik, Matrix/Synapse, Zulip, Nextcloud, MinIO, Radicale, OpenProject, and the Docker Runtime boundary without claiming provider interchangeability or release readiness.
- Sprint 23 adds a CI-safe Chat Provider Switch contract gate for Matrix/Synapse to Zulip canonical object coverage, fixture dry-run/apply evidence, rollback-honesty classification, LossyFieldReport enforcement, scoped claim gating, and support-safe ProviderRef redaction without claiming lossless migration, production apply, production rollback, release readiness, or provider interchangeability.

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

## Known Issues

- #591 remains the current release blocker: actual manual assistive-technology signoff is required before Sprint 18 milestone closure or public/production release signoff, unless release ownership explicitly splits the remaining manual signoff into a separate accepted blocker.
<!-- WEAVE_RELEASE_NOTES_END -->

## Product screenshots

Screenshots are deterministic, checked-in assets for current dogfood-ready product paths. They show Weave-owned surfaces and must not imply that guarded or roadmap capabilities are shipped.

### Admin-provisioned setup

[<img src="docs/assets/marketing/01-setup-start.svg" alt="Weave setup start screen inviting an admin to configure a self-hosted workspace." width="680">](docs/assets/marketing/01-setup-start.svg)

Normal members join after setup; raw provider configuration is not the member path.

### Custom Weave chat

[<img src="docs/assets/marketing/03-chat-room.svg" alt="Custom Weave chat room showing a release room conversation and accessible message composer." width="680">](docs/assets/marketing/03-chat-room.svg)

Chat is a Weave product experience backed by the selected chat adapter. For v0.1 dogfood release evidence, Matrix/Synapse is the current real chat provider path; Slack, Teams, and Nextcloud Talk remain contract-only portability targets until separately promoted with adapter and E2EE-safe evidence.

### Weave files through the backend facade

[<img src="docs/assets/marketing/04-files-documents.svg" alt="Weave files screen listing folders and files through the backend files facade." width="680">](docs/assets/marketing/04-files-documents.svg)

Files use Weave-owned routes and backend facades instead of exposing storage-provider UX as the everyday product identity.

### Settings and readiness

[<img src="docs/assets/marketing/05-settings.svg" alt="Weave settings screen showing saved local service configuration and sign-out controls." width="680">](docs/assets/marketing/05-settings.svg)

Settings and readiness surfaces explain configured, disabled, degraded, or unsupported states without exposing secrets or raw provider errors.

## Boards and provider boundary

Boards/Tasks is a Weave product surface. The client talks to the Weave backend facade and must not call task providers directly. Provider reads stay behind runtime gates, context authorization, support-safe metadata, and backend-held tokens. Provider writes remain disabled/fail-closed unless future promotion proves authorization, consent, audit, support-bundle redaction, and rollback behavior.

## Infrastructure and OpenTofu

OpenTofu is the operator tool for Weave infrastructure. CI runs format/validation-oriented checks; state-destructive operations require operator confirmation plus backup, restore, or rollback evidence. Support bundles must redact secrets, tokens, raw provider errors, provider URLs, cookies, private keys, generated credentials, and personal data. Operator-specific setup belongs in the [Admin/Operator Handbook](docs/admin-operator-handbook.md).

## Evidence contract

Gherkin scenarios are product contracts. For behavior changes, update product-language scenarios under `e2e/features/`, map them in `e2e/scenario_mappings.json`, add executable evidence, and keep live-stack artifacts sparse, sanitized, and release-candidate focused. Permanent docs should link to evidence procedures rather than transient artifact URLs. See [Quality and acceptance evidence](docs/quality-and-evidence.md) and [Acceptance contracts](docs/acceptance-contracts.md).

## Quick evaluation path

1. Read [v0.1 Golden Path readiness](docs/v0.1-golden-path.md) for what can be evaluated today.
2. Read [Canonical domains](docs/architecture/canonical-domains.md) and [Provider portability contract](docs/architecture/provider-portability.md) for the foundation vocabulary.
3. Read [Quality and acceptance evidence](docs/quality-and-evidence.md) and [v0.1.0-rc.3 release evidence](docs/release-v0.1-rc3-evidence.md) for evidence handling and the latest published prerelease audit.
4. Build docs locally:

   ```bash
   make docs-check
   ```

5. Run the relevant gate before contributing:

   ```bash
   ./gradlew docsCheck
   ./gradlew acceptanceContract
   ./gradlew releaseEvidenceCheck
   ./gradlew ci
   ```

## Release evidence

Release evidence is the review and verification trail for release-note automation and release-candidate promotion. Maintainers inspect checked-in artifacts, CI, and sanitized live-stack evidence or an explicit release-owner waiver.

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->


## Common local gates

Use Java 21+ for Gradle gates. On macOS with Homebrew JDK 21, for example:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

Run the smallest meaningful gate for the change:

```bash
make docs-check
make acceptance-contract
make client-ci
make server-ci
make infra-static
make ci

./gradlew docsCheck
./gradlew acceptanceContract
./gradlew releaseEvidenceCheck
./gradlew ci
```

Defaults: docs/site/release-marker changes use `make docs-check` plus `./gradlew releaseEvidenceCheck`; scenario mapping changes use `./gradlew acceptanceContract`; client/backend/admin/infra changes use the matching `clientCi`, `serverCi`, `adminCi`, or `infraStatic`; cross-stack changes use `./gradlew ci`.

## Documentation map

- [Documentation landing page](docs/index.md) — audience-based entry points.
- [User Handbook](docs/user-handbook.md) — joining and using an already-provisioned organization.
- [Admin/Operator Handbook](docs/admin-operator-handbook.md) — setup, providers, readiness, policy, audit, backup/restore, and support bundles.
- [Developer Handbook](docs/developer-handbook.md) — local prerequisites, Gradle gates, generated code, and evidence expectations.
- [Architecture](docs/architecture.md) — product-first, provider-neutral architecture and integration layering.
- [Canonical domains](docs/architecture/canonical-domains.md) — domain registry and product-owned contracts.
- [Provider portability contract](docs/architecture/provider-portability.md) — adapter manifests, mapping tables, reports, and no-unaccounted-data-loss rules.
- [Weaver OpenClaw-derived runtime profile](docs/architecture/weaver-openclaw-profile.md) — future optional PA runtime foundation and blockers.
- [Product line and Weaver integration plan](docs/product-line-and-weaver-plan.md) — active product ordering.
- [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md) — what is guarded, future, or intentionally not shipped.
- [Release notes](docs/release-notes/index.md) — release-note process and checked-in notes.

## Repository layout

- `client/` — Flutter app, product UI, accessibility checks, and client-side contract tests.
- `server/` — Spring Boot product API/BFF, provider facades, authorization, audit, support-safe errors, and backend acceptance tests.
- `infra/` — Docker/OpenTofu operator stack, deployment scripts, provider profiles, backup/restore, smoke checks, and support bundles.
- `e2e/` — product-language Gherkin scenarios, scenario mappings, and sanitized evidence contracts.
- `docs/` — MkDocs-backed product, user/admin/operator/developer documentation, architecture, release scope, evidence, roadmap boundaries, and research notes.
- `release/` — release manifests and stack compatibility metadata.


## Working agreements

- Keep documentation honest about shipped, guarded, disabled, degraded, and future surfaces.
- Keep normal-member UX provider-neutral; provider specifics belong in admin/operator readiness and support-safe diagnostics.
- Keep Weaver product-first and later: organization setup, provider categories, admin policy, readiness, and whitelisting come before PA runtime work.
- Prefer accessible headings, concise bullets, descriptive links, and alt text over dense copy.
- Do not expose provider secrets or service tokens to Flutter, support bundles, app config, logs, screenshots, or docs.
- Follow [Developer Handbook](docs/developer-handbook.md) and [Trunk-based PR and release workflow](docs/gitflow-pr-workflow.md) before opening, reviewing, or merging PRs.
