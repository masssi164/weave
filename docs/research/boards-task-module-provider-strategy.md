# Boards and Tasks Provider Strategy

Status: active-scope planning recommendation
Date: 2026-05-14
Scope: future Weave tasks/boards module, provider research, accessibility baseline

## Decision

Weave should define its own accessibility-first Tasks/Boards product model and UI, then connect external engines through provider adapters. The product surface must not be named or shaped around a single upstream tool such as Nextcloud Deck.

Boards/tasks are active Weave scope behind reviewed feature flags. Existing Deck/boards code is gated workspace scaffolding until backend facade, provider, accessibility, and E2E gates promote it into normal navigation.

## Rationale

A board/task system is a high-interaction workflow: keyboard navigation, screen-reader structure, drag alternatives, focus management, labels, status, due dates, comments, and notifications must be designed together. Directly exposing an upstream board UI or coupling Flutter to one provider's transport would make accessibility, future migration, and data sovereignty harder.

A Weave-owned model lets the app present one consistent experience while adapters handle provider-specific APIs, auth, sync, and event quirks behind repository/backend boundaries.

## Recommended model boundary

Use provider-neutral domain concepts first:

- workspace/project
- board
- column/list/status
- task/card
- assignee and watcher references
- due/start dates
- labels/tags/priority
- checklist/subtasks where available
- comments/activity
- attachments/links
- provider sync metadata kept out of presentation models

The accessible UI must provide non-drag controls for moving cards, deterministic reading/focus order, visible focus, text scaling tolerance, status not communicated by color alone, and screen-reader-readable updates for creates, moves, errors, and sync conflicts.

See [Boards and Tasks Domain Contract](boards-task-domain-contract.md) and [Boards Provider Spike Artifacts](boards-provider-spike-artifacts.md) for the next planning slice: provider-neutral entities, adapter capabilities, support-safe errors, accessibility requirements, normalized events, and acceptance criteria before implementation.

## Provider research outcome

### First implementation spikes

1. **Vikunja adapter spike** — first strategic spike for a lightweight, self-hostable OSS task engine with an API-oriented surface.
2. **OpenProject accessibility benchmark** — use OpenProject as the accessibility and mature-workflow comparison point, not necessarily the first embedded provider.
3. **Nextcloud Deck bridge spike** — evaluate as a low-friction bridge because Nextcloud is already in the Weave stack, but keep it behind the provider-adapter boundary and do not let Deck define the product model.

See [Boards Provider Spikes: Vikunja, OpenProject, Nextcloud Deck](boards-provider-spikes-119-121.md) and the machine-readable [provider capability matrix](boards-provider-capability-matrix.json) for the issue #119/#120/#121 findings and adapter capability contract.

### Defer or avoid as strategic base

- **Focalboard / Mattermost Boards** — defer; not a strong strategic base for Weave's standalone accessibility-first module direction.
- **Kanboard** — defer; useful reference for simplicity, but not the preferred base.
- **Planka** — defer; attractive UI, weaker fit for Weave's adapter/accessibility/data-sovereignty direction.
- **Taiga** — likely defer as a strategic base unless later research finds a compelling current-maintenance/API/accessibility fit.

## Event normalizer

Future provider work should include a provider-neutral event normalizer before broad UI investment. It should map provider-specific changes into Weave events such as task created, task moved, assignment changed, comment added, due date changed, task completed, and sync/conflict detected. This keeps notifications, recent activity, audit/support diagnostics, and future agent workflows independent from one provider.

## Open follow-up issues

Create or keep issues for:

- [`provider-vikunja-spike`](https://github.com/masssi164/weave/issues/119)
- [`openproject-a11y-benchmark`](https://github.com/masssi164/weave/issues/120)
- [`deck-spike`](https://github.com/masssi164/weave/issues/121)
- [`accessible-board-ui`](https://github.com/masssi164/weave/issues/122)
- [`event-normalizer`](https://github.com/masssi164/weave/issues/123)
