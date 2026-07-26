# Weave v0.1 Dogfood Production Release Plan

Status: implementation baseline for the monorepo refoundation.

Latest prerelease audit: `v0.1.0-rc.3` was published on 2026-06-01 from `2f0794c46cf8ecc91697b930d27b443c12fdeec2` under the historical live-stack contract. See [v0.1.0-rc.3 release evidence](release-v0.1-rc3-evidence.md). New candidates use the credential-free Fresh product flow (`testApp`) plus a separate physical AppAuth gate. Current public/production release signoff requires current CI, product-flow, release notes, accessibility, support-bundle, audit/export, migration, Weaver, and release-owner evidence; historical Sprint 18 blocker accounting is not current release truth.

## Goal

Ship Weave as a daily work tool for a real project, not as a demo stack.

UX release quality is gated by [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md): visible release-scope surfaces use available/disabled_by_policy/not_configured/degraded/unavailable/coming_later states, not preview or scaffold wording.

v0.1 must support a complete project loop after admins/operators provision the workspace:

1. Open Weave Home as a normal member without OIDC/provider/infra setup prompts.
2. Enter a workspace/channel.
3. Chat, handle files, plan events, run meetings, move board tasks, and record decisions through Weave product concepts.
4. See only complete capabilities or simple impact/fallback states as a member when something is broken.
5. Inspect Workspace Health as an admin/operator control plane for category-based provider setup, readiness, degraded provider state, backup/restore, support bundles, and release evidence.
6. Deploy, update, backup, restore, and roll back the stack with operator evidence.

## Non-goals for v0.1

- Asking normal organization members to configure OIDC, provider URLs, realms, service endpoints, backup/restore, or infrastructure readiness.
- Product agent runtime integration.
- Autonomous, group, or team-scoped agent writes.
- Public connector SDK.
- Teams/Slack migration tooling.
- Broad SaaS administration beyond safe self-hosted boundaries.

Agent integration requires a separate research/ADR track covering OpenClaw sandboxing, tool whitelisting, organization-wide policy, Matrix-plugin policy surfaces, consent, audit, and secret handling.

## Phase 1 — Monorepo foundation

Deliverables:

- `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` are in one repository.
- Backend and infra history are imported under stable prefixes.
- Root `Makefile` coordinates client/server/infra/e2e gates.
- Root/client/server/infra/e2e `AGENTS.md` files encode monorepo rules.
- CI runs acceptance, client, server, and infra static gates from the monorepo.
- The Fresh product-flow workflow consumes monorepo paths and invokes the same `testApp` Gradle task used locally.

Exit gate:

- `make acceptance-contract`
- `make infra-static`
- client/server CI green in GitHub
- Fresh product flow green from the dedicated self-hosted runner

## Phase 2 — Compose and desired-state infrastructure

Deliverables:

- One common Compose model exposes exactly the `dev`, `test`, and `prod` runtime profiles; Git delivery remains `dev` → `dogfood` → `main`.
- CI normalizes every profile, rejects unresolved inputs, and runs infrastructure contract tests.
- Rootless one-shot Identity Ops uses pinned `kcadm` against stock Keycloak and converges the
  canonical desired-state baseline plus the closed environment overlay.
- Migration from former infrastructure state is backup/restore rehearsed, adopted once, and retained only as restricted evidence.

Exit gate:

- `./gradlew :infra:composeDevConfig`
- `WEAVE_ENV_FILE=<reviewed-test.env> ./gradlew :infra:composeTestConfig`
- `WEAVE_ENV_FILE=<reviewed-prod.env> ./gradlew :infra:composeProdConfig`
- `./gradlew serverDevH2Test serverPostgresIntegrationTest`
- desired-state render and security-floor validation
- zero-diff second Keycloak reconciliation plan
- support-bundle redaction tests pass

## Phase 3 — Professional ATDD spine

Deliverables:

- Gherkin scenarios under `e2e/features/` describe v0.1 user behavior.
- `e2e/scenario_mappings.json` maps every scenario to executable evidence.
- Existing live-stack markers are preserved.
- New v0.1 scenarios are added only with failing/then-passing evidence.

Required scenario groups:

- Weave Home.
- Space control room navigation from one canonical Space identity.
- Files.
- Calendar.
- Boards writes with audit.
- Meeting Capsule.
- Decision Ledger.
- Workspace/Admin Health.
- Deploy/backup/restore smoke.

Exit gate:

- no unmapped scenarios
- no missing evidence markers
- sanitized artifact generation works even on failure

## Phase 4 — Product surfaces

### Weave Home

- Favorites/recent channels.
- Open tasks.
- Upcoming events/meetings.
- Recent decisions.
- Actionable health warnings.

### Space control room

- One member-visible Space control room route anchors Chat, Decisions, Files, Board, and Calendar to one support-safe Space ID.
- Tabs for Chat, Decisions, Files, Board, Calendar, and Meetings; Weaver is a workload-only boundary and has no member tab.
- Keyboard and screen-reader navigation across tabs.
- Empty, disabled_by_policy, not_configured, degraded, unavailable, coming_later, and evidence-linked states that explain what is safe now without provider-shaped wording.
- Support-safe evidence refs record the Space identity, linked domain objects, and final Decision state without raw provider identifiers, diagnostics, URLs, or private content.

### Files

- Browse, upload, download, delete, rename/move when supported.
- Backend facade only.
- Support-safe errors.

### Calendar

- Workspace/team/channel events.
- Event create/read/update/delete.
- Meeting thread/capsule reference stays stable.

### Boards

Boards with user writes are release scope, not a demo-only demonstration.

- Replace release-scope demo-only behavior with explicit user writes.
- Create task, move task/status, update title/body, comment or decision-link.
- Authorization and audit before provider mutation.
- Fail closed when provider, scope, permission, or audit is unavailable.

### Meetings

- Start/join from channel or event.
- LiveKit token facade only; no client-side provider secrets.
- Meeting Capsule includes agenda, files, decisions, and follow-up tasks.
- Media encryption boundary is explicit and evidenced.

### Decision Ledger

- Record decisions with context, evidence, risks, questions, and follow-up tasks.
- Link decisions to channels, meetings, events, files, and board items.

### Workspace/Admin Health

- Admin-provisioned first-use boundary from [Admin-provisioned first use boundary](admin-provisioned-first-use.md): members must not see provider setup diagnostics, while admins/operators use Workspace Health as the setup/readiness control plane.
- Platform identity/security is a fixed Keycloak boundary. Selectable provider categories are chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Agent Runtime Control.
- Keycloak/Auth is the platform identity authority and is not a patch-panel
  provider selection. Current dogfood domain defaults are Matrix for chat,
  Nextcloud for files and calendar backing, OpenProject for boards/tasks,
  MatrixRTC Profile 0 for calls signaling, a replaceable SFU for media, and
  guarded OpenClaw as the first Agent Runtime Control provider.
- Agent Runtime Control is entitlement-bound, workload-only, and fail-closed until its Keycloak entitlement, signed RuntimeProfile v2, external encrypted state, per-cell workload identity, and lifecycle reconciliation gates are current.
- Provider readiness is support-safe and admin/operator-facing; member UI shows ready product workflows or impact/fallback states only, never raw provider setup, service endpoints, provider secrets, or diagnostics.

Exit gate:

- one real project can be run in Weave for a week without falling back to raw providers for core work.

## Phase 5 — Deployable dogfood release

Deliverables:

- Install runbook.
- Update runbook.
- Backup runbook.
- Restore test.
- Rollback path.
- Secret/certificate handling.
- Release notes with honest limitations.
- Support bundle and smoke-test artifacts.
- Exact-candidate RC evidence with tag, commit, CI, credential-free Fresh product flow, separate physical AppAuth/assistive-technology evidence, blocker state, release-owner signoff, and rollback note.

Exit gate:

- fresh deploy works
- update works
- restore smoke passes
- support bundle is redacted
- `./gradlew testApp` passes against the release manifest
- `./gradlew releaseReadinessCheck` is ready for the exact candidate, with support-safe CI, product-flow, release notes, and blocker evidence

### Keycloak/RBAC and capability whitelisting acceptance

Before Agent Runtime Control can provision a runtime cell, Weave must prove Keycloak-backed RBAC capability profiles and category whitelisting in the backend/admin contract. Keycloak is the platform authority; external OIDC/SAML and LDAP/AD systems integrate upstream through Keycloak brokering or federation rather than a Weave identity-provider patch panel. Unknown roles and groups are denied by default. Admin/operator views expose support-safe effective policy state; member views expose only provider-neutral capability states: available, disabled by policy, not configured, degraded, unavailable, or coming later. Human roles do not imply `agent-runtime.entitled`.

## Agent Runtime Control policy evidence

Agent Runtime Control derives a per-person cell only from current Keycloak entitlement and backend policy. It signs RuntimeProfile v2 as short-lived desired state, keeps runtime state encrypted outside the disposable cell, provisions a unique `private_key_jwt` workload client, exposes no human MCP access, and emits support-safe lifecycle and audit evidence. RuntimeProfile v1 and its compatibility readers do not exist.
