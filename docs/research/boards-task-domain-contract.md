# Boards and Tasks Domain Contract

Status: active-scope planning contract
Date: 2026-05-14
Scope: future Weave tasks/boards module, provider-neutral domain model, adapter boundaries, accessibility and event contracts

## Decision

Future tasks/boards work must start from a Weave-owned domain model and accessibility contract. Provider APIs such as Vikunja, OpenProject, or Nextcloud Deck may inform adapters, but they must not define Flutter presentation models, navigation labels, or the user-facing product contract.

This work is active Weave scope behind feature gates. It must stay disabled or hidden from normal navigation until spec, backend facade, accessibility, and provider readiness issues explicitly promote it into the product surface.

## Goals

- Keep the Weave UI provider-neutral and accessibility-first.
- Let backend/provider adapters translate external task engines without leaking provider vocabulary into presentation code.
- Preserve migration and data-sovereignty options by making import/export and provider metadata explicit.
- Prepare normalized task/board events before notifications, recent activity, audit/support diagnostics, or agent workflows depend on provider-specific payloads.

## Non-goals

- Do not ship tasks/boards without the active-scope gates in this contract.
- Do not expose Nextcloud Deck, Vikunja, OpenProject, or another upstream UI as the normal Weave product surface.
- Do not add provider-specific Flutter transport logic before the backend/adapter boundary is reviewed.
- Do not require drag-and-drop as the only way to organize work.

## Repository ownership

| Area | Owner | Notes |
| --- | --- | --- |
| Product UX, accessibility behavior, local UI state | `weave` | Presentation consumes provider-neutral repositories/entities only. |
| Product API, provider adapter orchestration, normalized events | `weave-backend` or later connector/event gateway | Backend is the default BFF boundary when the module becomes real. A sidecar/event gateway may be introduced only by a later cross-repo spec. |
| Provider deployment and secrets | `weave-infra` | Any Vikunja/OpenProject/Deck runtime must be documented, optional, and off by default until promoted. |
| Cross-repo contract | workspace `specs/` | This repo-local planning note should be mirrored into a binding workspace spec before implementation begins. |

## Provider-neutral domain model

The first domain pass should use stable Weave concepts:

| Concept | Required fields | Notes |
| --- | --- | --- |
| Workspace/project | `id`, `name`, `visibility`, `member_refs`, `provider_refs` | A Weave project may map to a Vikunja project, OpenProject project, or Deck board grouping. |
| Board | `id`, `project_id`, `name`, `description`, `columns`, `archived`, `provider_refs` | Board naming stays Weave-owned even when a provider calls the same thing a board/project/list. |
| Column/list/status | `id`, `board_id`, `name`, `position`, `semantic_status`, `wip_limit?` | `semantic_status` should support at least `not_started`, `in_progress`, `blocked`, `done`, and `archived`. |
| Task/card | `id`, `board_id`, `column_id`, `title`, `description`, `status`, `position`, `assignee_refs`, `label_refs`, `priority?`, `start_at?`, `due_at?`, `completed_at?`, `updated_at`, `provider_refs` | Presentation should not depend on provider card/task IDs directly. |
| Label/tag | `id`, `name`, `color?`, `description?` | Color is decorative; labels require text alternatives and non-color meaning. |
| Comment/activity item | `id`, `task_id`, `actor_ref`, `body`, `created_at`, `edited_at?`, `provider_refs` | Rich text support needs sanitization and accessibility review. |
| Attachment/link | `id`, `task_id`, `kind`, `display_name`, `uri`, `size?`, `mime_type?`, `provider_refs` | File ownership must be explicit if backed by Nextcloud or another provider. |
| Provider sync metadata | `provider`, `external_id`, `external_url?`, `version?`, `etag?`, `last_synced_at?` | Metadata stays out of user-facing labels except support-safe diagnostics. |

## Adapter contract

A provider adapter should be able to:

1. List projects, boards, columns, tasks, labels, comments, and attachments through provider-neutral DTOs.
2. Create, update, move, complete/archive, and delete tasks where the provider safely supports those operations.
3. Translate provider errors into support-safe Weave errors: `unauthorized`, `forbidden`, `not_found`, `conflict`, `rate_limited`, `offline`, `validation`, `provider_unavailable`, and `unknown`.
4. Expose pagination/cursor behavior without leaking provider-specific pagination into Flutter state.
5. Preserve provider references for sync, conflict resolution, export, diagnostics, and support.
6. Declare unsupported capabilities explicitly instead of faking them in the UI.

Provider capability examples:

| Capability | Meaning |
| --- | --- |
| `comments` | Provider supports task comments/activity. |
| `attachments` | Provider can attach files or links to tasks. |
| `non_destructive_archive` | Provider can hide/archive without permanent deletion. |
| `webhook_events` | Provider can push change events. |
| `incremental_sync` | Provider can sync from cursors, etags, or update timestamps. |
| `checklists` | Provider supports subtasks/checklists that can be mapped without data loss. |
| `custom_fields` | Provider supports extra fields that need a reviewed Weave mapping. |

The current provider spike fixture lives in [Boards Provider Capability Matrix](boards-provider-capability-matrix.json). Keep it machine-readable and update its static contract test whenever a provider adapter changes capability semantics.

## Accessibility contract

The future board UI must be usable without pointer-only drag-and-drop:

- Columns and cards expose deterministic reading order.
- Every move action has keyboard and screen-reader-operable alternatives such as move menu, move up/down, move to column, and mark done/block.
- Visible focus is preserved across mobile, desktop, and future web targets.
- Status, priority, due date, conflict, and permission states are not communicated by color alone.
- Card actions have at least 48x48 logical touch targets where applicable.
- Screen readers receive meaningful create/update/move/delete/error announcements.
- Empty, loading, offline, sync-conflict, permission-denied, and validation states are plain-language and support-safe.
- Text scaling must not make critical board actions unreachable.

## Normalized events

Adapters should emit provider-neutral events before broader automation depends on them.

Minimum event envelope:

```text
TaskBoardEvent {
  idempotency_key,
  type,
  actor_ref,
  occurred_at,
  workspace_id,
  project_id?,
  board_id?,
  task_id?,
  provider_ref?,
  redaction_level,
  payload
}
```

Initial event types:

- `task.created`
- `task.updated`
- `task.completed`
- `task.archived`
- `task.moved`
- `assignment.changed`
- `label.changed`
- `priority.changed`
- `due_date.changed`
- `comment.added`
- `attachment.changed`
- `sync.conflict_detected`

Event handling must document ordering, idempotency, redaction/privacy, replay behavior, and conflict resolution before powering notifications or recent activity.

Implementation seed: `lib/features/boards/domain/entities/board_activity_event.dart` and the board activity normalizers under `lib/features/boards/data/services/` define the app-layer provider-neutral envelope and sample mappers for static preview fixtures plus external-provider-shaped events. They are deliberately active-preview scaffolding and do **not** claim a live Vikunja, OpenProject, Deck, or gateway integration.

## Spike sequencing

1. Use the Vikunja spike to validate API/auth/sync fit against this contract.
2. Use the OpenProject benchmark to sharpen accessibility and mature workflow expectations, not to choose a provider by default.
3. Use the Nextcloud Deck spike to evaluate bridge/import value while preventing Deck vocabulary from defining the product model.
4. Use the accessible board UI issue to design and test interactions against this contract before enabling any module navigation.
5. Use the event normalizer issue to decide whether first implementation belongs in `weave-backend`, a connector sidecar, or a later interop/event gateway.

## Acceptance criteria before implementation

- A binding workspace spec exists for tasks/boards and references the active-preview boundary.
- Provider spikes map their findings to the Weave concepts in this document.
- Flutter presentation models and tests use provider-neutral names.
- Backend/API contracts include capability discovery, support-safe errors, pagination, sync metadata, and normalized events.
- Accessibility test/audit strategy covers keyboard-only, screen reader, non-drag movement, visible focus, non-color status, and text scaling.

## Concrete spike artifacts

Provider feasibility output, API endpoint evidence, accessibility benchmark checklist, bridge/import notes, and event-normalizer examples are tracked in [Boards Provider Spike Artifacts](boards-provider-spike-artifacts.md).

## Related issues

- [#119 provider-vikunja-spike](https://github.com/masssi164/weave/issues/119)
- [#120 openproject-a11y-benchmark](https://github.com/masssi164/weave/issues/120)
- [#121 deck-spike](https://github.com/masssi164/weave/issues/121)
- [#122 accessible-board-ui](https://github.com/masssi164/weave/issues/122)
- [#123 event-normalizer](https://github.com/masssi164/weave/issues/123)
