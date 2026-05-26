# Sprint 4 plan: accessible work rooms and governed Weaver scout

Status: superseded by closure evidence, 2026-05-26. See [Sprint 4 closure report](sprint-4-closure-report.md).

## Product intent

Sprint 4 turns the Sprint 3 provider-neutral foundation into a usable work-room experience. A normal member should open Weave, find the right workspace/channel, understand active decisions and meetings, and ask a governed Weaver scout for read-only context without triggering silent writes.

## Milestone and issue graph

Milestone: `Sprint 4 — Accessible Work Rooms & governed Weaver scout`.

Primary Sprint 4 issues:

- #323 — Sprint 4 umbrella.
- #324 — finalize Sprint 3 report and seed Sprint 4 plan.
- #325 — Weave Home for DMs, favorites, channels, and AI chats.
- #326 — accessible channel work-room tabs.
- #327 — channel Decision Ledger MVP.
- #328 — channel Meeting Capsule MVP.
- #329 — read-only Weaver channel scout with approval receipts.
- #330 — Sprint 4 ISO 9241 dogfood evidence gate.
- #331 — release labels, required checks, and Gradle gate discipline.

Existing epics and guardrails moved into the milestone:

- #210, #211, #259.
- #251, #219, #249.
- #252, #214.
- #253, #257, #51.
- #288, #292.

## Development handbook rules

Sprint 4 follows the developer handbook:

- Start short-lived PR branches from `main`.
- Keep changes issue/spec-driven.
- Every PR must choose exactly one release-notes label before review or merge:
  - `release-notes-feature`
  - `release-notes-bugfix`
  - `release-notes-skip`
- Request Copilot review on every review-ready PR.
- Update product-facing Gherkin and scenario mappings when user journeys change.
- Keep cross-layer contracts atomic across `client/`, `server/`, `infra/`, `e2e/`, docs, and release evidence when boundaries change.
- Use root `./gradlew ci` as the monorepo aggregate gate; do not add new root Make-only build logic.

## Track A — Weave Home and Channel Work Rooms

Goal: make Weave feel like a real workspace, not a provider setup shell.

Scope:

- Weave Home groups DMs, favorites, channels, and AI/Weaver chats.
- Channel Work Room exposes Chat, Files, Boards/Tasks, Calendar/Events, and future Decisions/Meetings/Weaver context through accessible tabs or sections.
- Member UX uses Weave domain vocabulary and safe capability states, not provider setup copy.
- Product acceptance flows and scenario mappings cover Home and channel navigation.

Acceptance:

- A normal member can enter Home and a channel work room without encountering preview/provider setup surfaces.
- Keyboard and screenreader traversal is deterministic.
- Empty, degraded, disabled, and policy-blocked states are concise, localizable, and support-safe.

## Track B — Decision Ledger and Meeting Capsule

Goal: create first-class work objects that preserve why work happened, not only chat messages.

Scope:

- Decision Ledger MVP with channel/source/author/time/status fields.
- Decision states: proposed, accepted, superseded, rejected.
- Meeting Capsule MVP tied to workspace/team/channel context with agenda, join/start state, participants/roles, and follow-up references.
- Clear separation between Matrix chat E2EE and LiveKit/media protection claims.

Acceptance:

- Users can create/read decisions without visual-only affordances.
- Meeting Capsule can be opened from a channel through backend-owned facades.
- Decision and meeting objects can link to source/evidence references.
- Screenreader flows expose state and actions clearly.

## Track C — Governed Weaver scout

Goal: introduce Weaver as useful read-only context assistance without agentic write risk.

Scope:

- Weaver can answer questions such as “what is open in this channel?” from allowed context.
- Summaries cite source objects/messages/files/tasks/meetings.
- Writes are drafts/proposals or require an explicit approval/receipt path.
- Capability usage is visible in a screenreader-friendly way.

Governance rule:

- Sprint 4 uses the direction `user-rights, org-whitelisted tools`.
- No silent team-room mutations.

Acceptance:

- Weaver can summarize allowed channel context with source references.
- Weaver cannot mutate team-room data without policy and receipt path.
- Receipts include actor, requested action, approved action, target, timestamp, and result category.
- Failure output remains support-safe.

## Track D — Accessibility and dogfood evidence

Goal: make Sprint 4 strong enough for blind-user dogfood, not only “looks accessible”.

Scope:

- ISO 9241-style perceptibility/usefulness evidence for Home, Channel Work Rooms, Decisions, Meetings, and Weaver.
- Keyboard-only and screenreader/Braille-first evidence shape.
- No color-only state, drag-only task movement, or noisy live-region spam.
- Document which checks are automated, manual, or review-artifact based.

Acceptance:

- Critical Sprint 4 flows have linked a11y evidence before review.
- Status/empty/error/success states use concise, localizable copy.
- Evidence artifacts are text-first and navigable.

## Track E — DevOps and release discipline

Goal: keep Sprint 4 evidence reliable and release notes reconstructable.

Scope:

- Confirm branch protection or rulesets require core CI and release-notes label checks.
- Keep Release Notes Label Check non-bypassable for PRs.
- Keep root Gradle as the source of truth for aggregate gates.
- Ensure docs/report PRs use `release-notes-skip`; user-facing slices use `release-notes-feature` or `release-notes-bugfix` as appropriate.

Acceptance:

- `main` protection/ruleset status is documented or configured.
- Release Notes Label Check cannot be accidentally bypassed.
- CI evidence paths remain sanitized and uploaded.

## Non-goals

- Do not turn Sprint 4 into another provider-specific expansion sprint.
- Do not claim broad provider marketplaces, Teams/Slack migration tooling, generic provider swaps, media recording/transcription/caption support, or autonomous Weaver writes without separate contracts and evidence.
- Do not publish a GitHub release as part of Sprint 4 planning unless explicitly requested.
