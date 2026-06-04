# Weave Monorepo

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

**Weave is a provider-neutral organization suite with an admin/operator control plane.** Weave lets organizations own their collaboration layer: one Weave-owned product layer for collaboration, readiness, policy, evidence, and future governed assistance while keeping concrete providers behind adapters.

Weave is not a re-skinned Matrix, Nextcloud, Microsoft 365, Slack, Jira, or AI-agent bundle. Providers are implementation choices. Members use Weave product surfaces. Admins and operators control setup, provider binding, policy, readiness, audit, support evidence, and migration boundaries.

## Product line

- **Weave Control / `weavectl`** is for owners, admins, and operators. It owns organization bootstrap, deploy-new / attach-existing / hybrid setup, CI/CD target binding, approved dispatch, readiness, diagnostics, support bundles, and evidence.
- **Weave Server** is the separately deployable Java domain facade. It owns policy, provider registry, capability states, authorization, audit, SecretRef/CredentialRef handling, readiness, and support-safe errors.
- **Weave App** is for members and guests. Members join through an organization URL, invite link, deep link, or SSO and then see Weave capabilities, not raw provider setup.
- **Weaver** is the future optional governed PA line. It is disabled by default and must stay behind organization policy, user opt-in where required, tool allowlists, sandboxing, approval receipts, revocation, and audit.

## Bootstrap target

The product goal is a customer-simple bootstrap path:

1. Start Weave Control through the Admin Console or `weavectl`.
2. Choose setup mode per domain:
   - `deploy_new`: Weave provisions approved resources.
   - `attach_existing`: Weave binds existing customer systems without redeploying them.
   - `hybrid`: some domains are new, some are attached.
3. Select the CI/CD or GitOps target, such as local Forgejo for dogfood or another supported provider when evidence exists.
4. Review the support-safe plan, SecretRef/CredentialRef posture, member impact preview, rollback/support boundary, and blocked claims.
5. Approve apply.
6. Observe pipeline and readiness through Admin Console and/or concise shell evidence.
7. Create, activate, or invite first users.
8. Members open Weave App, complete SSO, and enter product surfaces.

Normal members must not configure raw OIDC clients, provider URLs, service endpoints, CI/CD targets, Matrix/Nextcloud/OpenProject/LiveKit internals, SecretRefs, bootstrap diagnostics, backup/restore, or provider readiness.

Primary contracts:

- [Weave Control bootstrap-to-client contract](docs/weave-control-bootstrap-to-client-contract.md)
- [Admin-provisioned first use](docs/admin-provisioned-first-use.md)
- [Admin-Suite readiness and setup contract](docs/admin-suite-readiness-setup-contract.md)
- [Control-plane infra bootstrap](docs/control-plane-infra-bootstrap.md)

## Product architecture

Weave keeps product domains separate from provider implementations.

- Members use Weave domains: identity, people, spaces, chat, files, documents, calendar, boards, calls, decisions, notifications, health, and future Weaver.
- Weave Server owns provider mapping, readiness, authorization, audit, migration evidence, SecretRefs, and support-safe error boundaries.
- Weave Control exposes selected adapters, readiness, diagnostics, policy, approval, dispatch, backup/restore, and evidence.
- Weave App exposes provider-neutral states such as `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`.
- Provider-specific raw errors, secrets, tenant URLs, logs, and diagnostics stay admin/operator-only and redacted.

Architecture docs:

- [Architecture](docs/architecture.md)
- [Canonical domains](docs/architecture/canonical-domains.md)
- [Canonical feature models](docs/canonical-feature-models.md)
- [Provider portability contract](docs/architecture/provider-portability.md)
- [Diagrams](docs/diagrams/index.md)

## Release notes

The managed block below is the README-facing draft of merged changes after the latest tagged prerelease. Published notes live in [v0.1 release notes](docs/release-notes/v0.1.md), with the audit in [v0.1.0-rc.3 release evidence](docs/release-v0.1-rc3-evidence.md). Maintainers update this block with checked-in release-note tooling; do not edit inside the markers by hand.

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
- Slack and Microsoft Teams remain commercial adapter readiness candidates only; adapter implementation, production migration, rollback, and customer-ready claims are blocked until future `implementation_allowed` and `release_ready` evidence exists.
- Operator backup/restore wording must reference `docs/operator-recovery-known-limitations.md`; Sprint 26 now allows only the scoped disposable fixture-domain restore proof claim, not production restore or E2EE lost-device recovery claims.

## Known Issues

- #591 remains a release blocker: actual manual assistive-technology signoff is required before Sprint 18 milestone closure or public/production release signoff, unless release ownership explicitly splits the remaining manual signoff into a separate accepted blocker.
- #591 remains the active human/manual release blocker; #642 operator recovery is satisfied only for disposable fixture-domain restore evidence and does not authorize production restore claims.
<!-- WEAVE_RELEASE_NOTES_END -->

## Product screenshots

Screenshots are deterministic, checked-in assets for current dogfood-ready product paths. They show Weave-owned surfaces and must not imply that guarded or roadmap capabilities are shipped.

- [Setup start](docs/assets/marketing/01-setup-start.svg) — admin-provisioned setup starts before normal member join.
- [Custom Weave chat](docs/assets/marketing/03-chat-room.svg) — chat is a Weave product experience backed by the selected chat adapter.
- [Files and documents](docs/assets/marketing/04-files-documents.svg) — files use Weave-owned routes and backend facades.
- [Settings and readiness](docs/assets/marketing/05-settings.svg) — readiness states explain configured, disabled, degraded, or unsupported capabilities without secrets or raw provider errors.

## Repository layout

- `client/` — Flutter app, product UI, accessibility checks, and client-side contract tests.
- `server/` — Spring Boot product API/BFF, provider facades, authorization, audit, support-safe errors, and backend acceptance tests.
- `infra/` — Docker/OpenTofu operator stack, deployment scripts, provider profiles, backup/restore, smoke checks, and support bundles.
- `e2e/` — product-language Gherkin scenarios, scenario mappings, and sanitized evidence contracts.
- `docs/` — MkDocs-backed product, user/admin/operator/developer docs, architecture, release scope, evidence, roadmap boundaries, and research notes.
- `release/` — release manifests and stack compatibility metadata.
- `.forgejo/` and `.github/` — CI/CD workflow targets. A workflow in one provider is not automatically evidence for another provider.

## v0.1 product truth

Weave v0.1 is a **dogfood-production foundation**, not a broad public production claim.

Ready or foundation-level:

- one monorepo product stack: `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/`;
- provider-neutral domain vocabulary and member/admin boundary contracts;
- Weave App member surfaces guarded by capability states;
- backend/domain facade direction for provider boundaries;
- admin/operator readiness, support-safe diagnostics, and evidence contracts;
- local/dev and dogfood infrastructure paths with explicit release blockers;
- release-note, acceptance, docs, and evidence gates.

Guarded or future:

- production provider cutover and rollback are evidence-scoped, not generally claimed;
- Slack, Teams, Microsoft 365, SharePoint, Jira, and similar adapters are candidates until promoted by adapter-specific evidence;
- Office/document editing is not generally shipped;
- Weaver runtime execution is not broadly available and remains disabled by default;
- human/manual assistive-technology release signoff remains blocked until real evidence exists.

Reality and claim boundaries:

- [Product reality foundation](docs/product-reality-foundation.md)
- [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md)
- [Quality and acceptance evidence](docs/quality-and-evidence.md)
- [v0.1 release evidence](docs/release-v0.1-rc3-evidence.md)

## Ready / Guarded / Future claim matrix

This matrix is intentionally compact. It exists to keep release checks and marketing language honest; it is not the product narrative.

| Claim | Status | Evidence or boundary |
| --- | --- | --- |
| Weave is a monorepo product stack with client, server, infra, e2e, docs, and release gates. | **Ready** | Developer Handbook, build/evidence delivery, and root CI gates. |
| v0.1 is dogfood-production, not preview/scaffold UX for normal members. | **Ready foundation** | Golden path readiness plus dogfood UX gates. |
| Members work in provider-neutral Weave domains. | **Ready foundation** | Canonical domains, canonical feature models, and architecture docs. |
| Provider adapters are replaceable behind Weave-owned contracts. | **Guarded** | Reality levels prevent contract-only candidates from being marketed as generally available: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, `release_ready`. |
| No unaccounted data loss is the portability promise. | **Guarded** | Unsupported fields, conflicts, archive-only data, and provider-unexportable data must be reported; perfect lossless migration is not claimed. |
| Calls/meetings use LiveKit readiness today. | **Guarded** | Join/media claims remain limited to implemented readiness and token facade evidence. |
| Workspace/Admin Health is the support-safe readiness and diagnostics control plane. | **Ready foundation / expanding** | Admin-Suite readiness and setup contract plus quality/evidence gates. |
| Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | **Future foundation** | Runtime execution is not shipped. |
| Autonomous agent/team writes are available in v0.1. | **Not shipped** | Future writes require admin-approved tools, opt-in where required, approval policy, audit, sandbox, and support-safe evidence. |

## Provider and data boundary

Weave can model three provider postures:

- **Dogfood/default:** self-hosted identity, chat, files/calendar, boards validation, calls readiness, and local Forgejo/GitOps evidence where available.
- **Attach existing:** customer-owned providers are bound behind Weave domains without pretending Weave owns their internals.
- **Hybrid:** each domain chooses deploy-new or attach-existing, while members receive one coherent Weave manifest.

Provider portability is governed by a **no unaccounted data loss** principle. Exports, imports, lossy fields, conflicts, unsupported provider features, rollback limits, and archive-only paths must be reported before claims or apply actions are promoted.

## Boards and provider boundary

Boards/tasks are Weave product surfaces. The client talks to Weave Server facades, not task providers directly. Provider reads stay behind runtime gates, context authorization, support-safe metadata, and backend-held tokens. Provider writes remain disabled/fail-closed unless future promotion proves authorization, consent, audit, support-bundle redaction, and rollback behavior.

## Infrastructure and OpenTofu

OpenTofu is the operator tool for Weave infrastructure. CI runs format/validation-oriented checks; state-destructive operations require operator confirmation plus backup, restore, or rollback evidence. Operator-specific setup belongs in the [Admin/Operator Handbook](docs/admin-operator-handbook.md).

## Weaver boundary

Weaver is part of the product direction, but not a default-shipped runtime.

The target is an organization-governed, per-user assistant derived from OpenClaw-style runtimes:

- per-user isolation and revocation;
- organization-provided skill/tool packages;
- deny-by-default capability policy;
- scoped connectors and secrets;
- approval receipts for sensitive actions;
- group-chat consent and audit;
- support-safe observability.

Until those gates exist, Weaver remains optional, governed, and disabled by default.

Weaver docs:

- [Weaver OpenClaw-derived runtime profile](docs/architecture/weaver-openclaw-profile.md)
- [Product line and Weaver integration plan](docs/product-line-and-weaver-plan.md)
- [Governed Weaver runtime security contract](docs/governed-weaver-runtime-security-contract.md)

## Documentation map

- [Documentation landing page](docs/index.md) — audience-based entry points.
- [User Handbook](docs/user-handbook.md) — joining and using an already-provisioned organization.
- [Admin/Operator Handbook](docs/admin-operator-handbook.md) — setup, providers, readiness, policy, audit, backup/restore, and support bundles.
- [Developer Handbook](docs/developer-handbook.md) — local prerequisites, Gradle gates, generated code, and evidence expectations.
- [Architecture](docs/architecture.md) — product-first, provider-neutral architecture and integration layering.
- [Canonical domains](docs/architecture/canonical-domains.md) — domain registry and product-owned contracts.
- [Provider portability contract](docs/architecture/provider-portability.md) — adapter manifests, mapping tables, reports, and no-unaccounted-data-loss rules.
- [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md) — what is ready, guarded, future, or intentionally not shipped.
- [Release notes](docs/release-notes/index.md) — release-note process and checked-in notes.

## Evidence contract

- Gherkin scenarios are product contracts. Update `e2e/features/`, `e2e/scenario_mappings.json`, and executable evidence together.
- Support bundles must redact secrets, tokens, raw provider payloads, credential-bearing URLs, tenant URLs, cookies, private keys, generated credentials, member content, and personal data.
- Release evidence is a review trail, not marketing copy.
- Manual assistive-technology signoff cannot be claimed without real human evidence or an explicit scoped waiver.
- Keep documentation honest about shipped, guarded, disabled, degraded, and future surfaces.

## Release evidence

Release evidence is the review and verification trail for release-note automation and release-candidate promotion. Maintainers inspect checked-in artifacts, CI, and sanitized live-stack evidence or an explicit release-owner waiver.

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
<!-- WEAVE_RELEASE_NOTES:END -->

## Common local gates

Fastest honest evaluation path and local gate map:

1. Read [v0.1 Golden Path readiness](docs/v0.1-golden-path.md).
2. Read [Weave Control bootstrap-to-client contract](docs/weave-control-bootstrap-to-client-contract.md).
3. Read [Quality and acceptance evidence](docs/quality-and-evidence.md).
4. Run docs and acceptance gates:

   ```bash
   make docs-check
   make acceptance-contract
   ./gradlew docsCheck
   ./gradlew acceptanceContract
   ```

5. For broader changes, run the matching gate:

   ```bash
   make client-ci
   make server-ci
   make infra-static
   make ci

   ./gradlew releaseEvidenceCheck
   ./gradlew ci
   ```

Use Java 21+ for Gradle gates. On macOS with Homebrew JDK 21:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

## Working agreements

- Product story first; implementation detail second.
- Keep normal-member UX provider-neutral.
- Keep provider specifics in admin/operator readiness and support-safe diagnostics.
- Keep Weaver governed, optional, and disabled by default until its gates exist.
- Prefer accessible headings, concise bullets, descriptive links, and alt text over dense copy.
- Do not expose provider secrets or service tokens to Flutter, support bundles, app config, logs, screenshots, or docs.
- Follow [Developer Handbook](docs/developer-handbook.md) and [Trunk-based PR and release workflow](docs/gitflow-pr-workflow.md) before opening, reviewing, or merging PRs.
