# Boards Provider Spike Artifacts

Status: concrete spike output informing the v0.1 Boards workspace provider boundary
Date: 2026-05-14
Scope: Issues [#119](https://github.com/masssi164/weave/issues/119), [#120](https://github.com/masssi164/weave/issues/120), [#121](https://github.com/masssi164/weave/issues/121), [#123](https://github.com/masssi164/weave/issues/123)

## Guardrails

- Boards/tasks stay feature-gated until provider contracts are ready.
- Flutter remains provider-neutral: no Vikunja, OpenProject, or Deck transport/client code belongs in presentation state.
- Backend/adapter boundaries own provider auth, sync, error mapping, normalized activity events, and provider references.
- No live instance, credentials, or provider secrets were used for this spike. Findings are based on public API/user/developer documentation fetched on 2026-05-14.

## Adapter contract now reflected in code

The matching backend worktree adds disabled adapter contracts and a normalizer boundary:

- `BoardsRepository` remains the provider-neutral port.
- `VikunjaBoardsRepository` is the first disabled provider boundary.
- `OpenProjectBoardsRepository` is a disabled benchmark adapter contract.
- `NextcloudDeckBoardsRepository` is a disabled bridge/import adapter contract.
- `TaskBoardEventNormalizer` maps provider activity into provider-neutral `TaskBoardEvent` envelopes with stable idempotency keys and support-safe redaction.

The Flutter Boards workspace renders provider-neutral board/task copy and accessible non-drag task actions through the backend facade.

## #119 Vikunja first adapter spike

### API evidence

Public Vikunja API docs expose the basic shape needed for a first adapter:

- Auth: JWT or scoped API token using `Authorization: Bearer <token>`.
- Pagination: `page`/`per_page` style endpoints with `x-pagination-total-pages` and `x-pagination-result-count` headers.
- Projects/tasks: `/projects`, `/projects/{id}`, `/projects/{id}/tasks`, `/tasks`, `/tasks/{id}`.
- Board columns: project views and buckets via `/projects/{id}/views`, `/projects/{id}/views/{view}/buckets`, and bucket task endpoints.
- Movement/order: `/tasks/{id}/position` and bucket task endpoints can support non-drag move commands.
- Labels/comments/attachments: `/labels`, `/tasks/{task}/labels`, `/tasks/{taskID}/comments`, `/tasks/{id}/attachments`.
- Events: project webhooks are exposed under `/projects/{id}/webhooks` and event discovery under `/webhooks/events` and `/user/settings/webhooks/events`.

### Mapping contract

- Vikunja project -> Weave project and default board.
- Vikunja bucket -> Weave column with `semantic_status` inferred conservatively from column title.
- Vikunja task -> Weave task with status, position, assignee refs, label refs, priority, due/start/completion timestamps, and provider sync metadata.
- Vikunja webhook or polling change -> normalized `TaskBoardEvent` with `sourceProvider=vikunja` and a provider event id when present.

### Feasibility result

Vikunja is feasible as the first adapter because it is lightweight, self-hostable, API-first, and exposes tasks, buckets, labels, comments, attachments, webhooks, and move/order endpoints. The first implementation should still be backend-only and disabled until auth, route DTOs, OpenAPI publication, smoke/E2E coverage, export/backup, and conflict semantics are specified.

### Risks/blockers

- Need a disposable test instance before claiming live integration.
- Need precise webhook event names and payload samples from a live/current instance.
- Need conflict behavior around `etag`, task update timestamps, and move order tested against real concurrent edits.
- Need rate-limit/offline behavior captured in support-safe errors.

## #120 OpenProject accessibility benchmark

### API/workflow evidence

OpenProject API v3 exposes work packages and activities suitable for comparison:

- Work packages are available through `/api/v3/work_packages` and include subject, description, dates, percentage done, priority/status/type links, assignee/responsible links, relations, attachments, watchers, and optimistic `lockVersion`.
- Activity records are linked to work packages and can carry comments/attachments.
- OpenProject documents an accessibility checklist targeting WCAG 2.1 AA, including logical reading order, keyboard availability, no keyboard traps, visible focus, labels, non-color-only state, and text scaling/readability.

### Benchmark checklist for Weave boards

Use OpenProject as a bar to beat, not as the product model:

- Keyboard-only navigation reaches every board column, task, action menu, create/edit action, move command, filter, and error state.
- No task movement requires drag-and-drop; every move is available through a menu/command path.
- Screen readers get deterministic column/task counts, status, assignee, due date, priority, conflict, and permission summaries.
- Focus is visible and stable after create, move, complete, block, archive, validation error, and provider conflict.
- Status and priority are text plus shape/icon, never color alone.
- Text at 200% keeps critical controls reachable.
- Provider/API errors are support-safe and actionable without raw provider messages or URLs with credentials.

### Feasibility result

OpenProject is valuable as an accessibility and mature-workflow benchmark. It is not the preferred first Weave runtime adapter because its project/work-package model is heavier and would pull more workflow vocabulary into the product unless aggressively contained behind the backend port.

### Risks/blockers

- Need hands-on screen-reader/keyboard testing against a current OpenProject board/work-package flow before recording benchmark results as pass/fail.
- Need exact API filters for board/status views and activity synchronization from a live instance.
- Need a mapping for custom fields and work package types before adapter promotion.

## #121 Nextcloud Deck bridge spike

### API evidence

Deck exposes an API under `/index.php/apps/deck/api/v1.0` and OCS endpoints under `/ocs/v2.php/apps/deck/api/v1.0`:

- Boards: `GET/POST /boards`, `GET/PUT/DELETE /boards/{boardId}`, board ACL endpoints, undo delete.
- Stacks: `GET/POST /boards/{boardId}/stacks`, `GET/PUT/DELETE /boards/{boardId}/stacks/{stackId}`, archived stacks.
- Cards: `GET/POST/PUT/DELETE /boards/{boardId}/stacks/{stackId}/cards/{cardId}`.
- Movement/order: `PUT /boards/{boardId}/stacks/{stackId}/cards/{cardId}/reorder`.
- Labels/users: card label assign/remove and user assign/unassign endpoints.
- Attachments/comments: card attachment endpoints and OCS card comment endpoints.
- Import: `/boards/import/getSystems`, `/boards/import/config/system/{schema}`, `/boards/import`.

### Mapping contract

- Deck board -> Weave board inside a Weave project/workspace selected by the backend.
- Deck stack -> Weave column.
- Deck card -> Weave task.
- Deck labels, assigned users, comments, and attachments -> Weave labels, assignee refs, comments, and attachments.
- Deck vocabulary remains provider metadata only; Flutter continues to say board/column/task.

### Feasibility result

Deck is feasible as a bridge/import path because it is already adjacent to Nextcloud deployments and exposes board, stack, card, labels, users, attachments, comments, reorder, and import endpoints. It should not be the base product model: Deck API terms and Nextcloud-specific OCS/session behavior must stay backend-owned.

### Risks/blockers

- Need to confirm current Deck API version and auth behavior in the target Nextcloud stack.
- Need to test deletion/archive/restore semantics to avoid destructive surprises.
- Need to test comments and attachments ownership when files live in Nextcloud.
- No webhook/incremental-sync claim yet; treat Deck as polling/import-first until proven otherwise.

## #123 Event normalizer artifact

Initial normalized event examples now covered by backend tests:

- Vikunja webhook move -> `task.moved` with source event id idempotency.
- OpenProject activity due date change -> `due_date.changed` without leaking Action Board vocabulary.
- Deck polling label change -> `label.changed` with deterministic fallback idempotency.
- Provider conflict -> `sync.conflict_detected` with support-safe redaction of tokens, secrets, authorization headers, raw messages, and provider URLs.

The normalizer is not wired to notifications, webhooks, audit streams, or enabled runtime routes.
