# ISO 9241-110 Dogfood UX Gate

Status: v0.1 product UX contract
Owner: `client/` product UX, with backend/infra evidence for readiness and operator actions
Related issues: #258, #259

## Purpose

Weave v0.1 must open as a daily work product, not as a scaffold, roadmap gallery, or provider demo. This gate turns ISO 9241-110 dialogue principles into release-scope acceptance criteria and promptable planning rules for future agent work.

## Dialogue principles as release checks

- **Suitability for the task**: users can start real project work from Home, enter a channel, chat, use files, see calendar/board/meeting/decision availability, and recover from degraded state without raw provider fallbacks.
- **Self-descriptiveness**: each screen explains current state, user impact, next user action, and admin/operator next action where relevant.
- **Conformity with expectations**: visible labels use Weave product concepts first. Matrix, Nextcloud, OpenProject, LiveKit, and similar provider names appear only where they help admins/operators diagnose readiness.
- **Suitability for learning**: first use teaches through short empty states and next actions, not walls of setup text.
- **Controllability**: users can navigate, retry, cancel, confirm destructive work, and use non-drag alternatives for task movement.
- **Error tolerance**: missing services fail closed with safe fallbacks, support-safe error copy, and no secrets, raw URLs, stack traces, or credential-bearing details.
- **Suitability for individualization**: density, module visibility, theme, and notifications can evolve without making the core workspace unusable.

## Release-scope capability states

Every visible capability must use exactly one of these states. **Preview is not a release-scope state.**

1. **Ready for users**
   - Visible to normal members.
   - Has Weave product UI, backend facade, support-safe errors, health/readiness state, and executable evidence.
2. **Admin setup required**
   - Visible to admins/operators with exact next setup action.
   - Hidden from members unless they need a short impact-level unavailable state.
3. **Disabled by policy**
   - Members see a short policy reason.
   - Admins see where the policy is controlled.
4. **Broken/degraded**
   - Members see impact and safe fallback.
   - Admins/operators see the next diagnostic action and support-bundle/evidence path.
5. **Not in this release**
   - Not visible in normal member navigation.
   - May appear in roadmap/docs/issues, not in product UI.

## Shared vocabulary

Use these labels consistently across Home, channel tabs, settings, Workspace Health, and acceptance evidence.

### Status labels

- Ready
- Admin setup required
- Disabled by policy
- Degraded
- Blocked
- Unknown
- Not in this release

### User actions

- Open
- Create
- Save
- Upload
- Download
- Retry
- Cancel
- Delete
- Restore
- Ask an admin

### Admin/operator actions

- Configure
- Enable by policy
- Check readiness
- Run smoke test
- Generate support bundle
- Review backup
- Restore from backup
- Open diagnostics

### Banned release-scope copy

Do not use these on normal member paths or release-scope screens:

- preview
- scaffold
- coming soon
- roadmap
- future
- raw provider setup instructions
- provider stack traces, secrets, tokens, or credential-bearing URLs

Exceptions must be narrow and explicit, for example message snippet variables named `preview` in code or roadmap documentation outside the product UI.

## Surface classification for v0.1 planning

| Surface | Member state | Admin/operator state | Evidence expectation |
| --- | --- | --- | --- |
| Home | Ready for users | Same plus health impact | Widget/contract test for hierarchy, empty state, and degraded state |
| Channel chat | Ready for users | Same plus E2EE/readiness posture | Chat widget tests and live Matrix evidence |
| Channel files | Ready for users when files facade is ready; otherwise degraded impact copy | Setup/readiness in Workspace Health | Files facade tests and live upload/download evidence |
| Channel boards/tasks | Admin setup required until authenticated writes, authorization, audit, and rollback evidence are ready | Workspace Health shows next setup/diagnostic action | Non-drag task tests and backend facade/audit evidence |
| Channel calendar | Admin setup required until channel event CRUD and readiness evidence are ready | Workspace Health shows calendar readiness | Calendar facade tests and live event/thread evidence |
| Meetings/LiveKit | Admin setup required/fail-closed until token, media, E2EE boundary, accessibility, and support evidence are ready | Workspace Health shows exact readiness blocker | Backend token/readiness tests before member join/start is enabled |
| Decision Ledger | Admin setup required until route, write/read flow, and links are implemented | Health/readiness only if persistence is degraded | Product route and acceptance mapping |
| AI/Weaver | Disabled by policy / not in this release until approval receipts, consent, audit, and sandboxing ADR are accepted | Admin policy surface may explain disabled state | No runtime writes in v0.1 without accepted ADR |
| Provider diagnostics | Hidden from members except impact-level status | Ready in Workspace Health | Role-based widget tests and support-safe DTO tests |

## First-use acceptance criteria

A newly invited member in an admin-provisioned workspace must be able to:

1. Open Weave and understand that Home is the starting point for daily work.
2. Open a channel workspace without learning provider names.
3. Use chat as the reliable center of the channel.
4. Use files through Weave or see a clear degraded impact state.
5. See calendar, boards, meetings, decisions, and AI only when available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later according to the state table.
6. Understand degraded state through plain-language impact and a safe next action.
7. Avoid raw provider setup, raw provider failures, roadmap panels, or preview cards.

An admin/operator must be able to:

1. See Workspace Health as the place for setup/readiness/degraded provider state.
2. Understand what members can currently do before inviting them.
3. Get exact next setup or diagnostic actions without exposing secrets.
4. Generate or find support-safe evidence for failures.

## Prompting checklist for future agent work

When asking an agent to implement or review a UX slice, include:

- The target release-scope state from this document.
- The affected role: member, admin, operator, or all.
- The visible labels and banned copy expectations.
- The backend facade/readiness/audit evidence that makes the surface user-ready.
- The fallback state if evidence is missing.
- The exact tests or acceptance mapping that must fail before the fix and pass after it.

Example prompt shape:

> Implement the Channel Boards member slice as `Admin setup required` until authenticated writes, authorization, audit, and rollback evidence are available. Do not show preview/coming-soon copy. Members see impact-level copy; admins see Workspace Health next action. Add widget/contract tests that fail on banned release-scope wording and prove member/provider-diagnostic role separation.

## Required validation

- `make acceptance-contract` when Gherkin or mappings change.
- `make client-ci` for client/copy/widget changes.
- Targeted widget tests for role separation and copy gates.
- Manual release sign-off must still include the accessibility evidence listed in `docs/accessibility-release-gate.md`.
