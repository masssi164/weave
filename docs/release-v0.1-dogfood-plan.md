# Weave v0.1 Dogfood Production Release Plan

Status: implementation baseline for the monorepo refoundation.

## Goal

Ship Weave as a daily work tool for a real project, not as a demo stack.

UX release quality is gated by [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md): visible release-scope surfaces use ready/admin-setup-required/disabled/degraded/hidden states, not preview or scaffold wording.

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
- Live Stack E2E consumes monorepo paths.

Exit gate:

- `make acceptance-contract`
- `make infra-static`
- client/server CI green in GitHub
- Live Stack E2E manually green with explicit runner budget

## Phase 2 — OpenTofu-first infra

Deliverables:

- Operator scripts default to `${WEAVE_IAC_BIN:-tofu}`.
- CI sets up OpenTofu and runs format validation.
- Infrastructure docs use OpenTofu-first language.
- State migration/compatibility notes are explicit.

Exit gate:

- `tofu fmt -check -recursive`
- `tofu init -backend=false`
- `tofu validate`
- support-bundle redaction tests pass

## Phase 3 — Professional ATDD spine

Deliverables:

- Gherkin scenarios under `e2e/features/` describe v0.1 user behavior.
- `e2e/scenario_mappings.json` maps every scenario to executable evidence.
- Existing live-stack markers are preserved.
- New v0.1 scenarios are added only with failing/then-passing evidence.

Required scenario groups:

- Weave Home.
- Channel workspace navigation.
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

### Channels as workspaces

- Tabs for Chat, Files, Board, Calendar, Meetings, Decisions.
- Keyboard and screen-reader navigation across tabs.
- Empty/error/recovery states that explain what to do next.

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
- Provider categories are first-class product/admin concepts: identity/IDM, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Weaver.
- Current dogfood defaults map into those categories as provider selections and readiness signals: Keycloak/Auth for identity/IDM, Matrix for chat, Nextcloud for files and calendar backing, OpenProject for boards/tasks validation, and LiveKit for meetings readiness.
- Weaver is represented only as a disabled-by-default category until the later governed per-user PA runtime track is explicitly enabled by admin policy.
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

Exit gate:

- fresh deploy works
- update works
- restore smoke passes
- support bundle is redacted
- Live Stack E2E passes against release manifest

### IDM/RBAC and capability whitelisting acceptance

Before Weaver runtime work, Weave must prove IDM/RBAC capability profiles and category whitelisting in the backend/admin contract. Keycloak is the self-hosted default IDM, while OIDC/SAML adapters remain provider-neutral. Unknown roles and groups are denied by default. Admin/operator views expose support-safe effective policy state; member views expose only ready, disabled, degraded, or policy-blocked impact states. Weaver remains disabled by policy until a later governed runtime profile is implemented.
