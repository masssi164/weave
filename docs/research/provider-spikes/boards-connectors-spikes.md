# Boards connector spikes: Vikunja, OpenProject, Nextcloud Deck

Status: evidence artifact and adapter-shape proposal for post-Release-1 boards/tasks work  
Date: 2026-05-14  
Related issues: [#119](https://github.com/masssi164/weave/issues/119), [#120](https://github.com/masssi164/weave/issues/120), [#121](https://github.com/masssi164/weave/issues/121), [#123](https://github.com/masssi164/weave/issues/123)

## Scope boundary

Boards/tasks remain post-Release-1. This spike does not promote a live provider integration, does not add provider credentials, and does not bind the Weave UI to Vikunja, OpenProject, or Deck vocabulary.

The testable code artifact is a provider-neutral adapter/event shape:

- `lib/features/boards/domain/adapters/board_provider_adapter.dart`
- `lib/features/boards/domain/entities/task_board_event.dart`
- `lib/features/boards/data/normalizers/task_board_event_normalizer.dart`
- `test/features/boards/domain/task_board_event_normalizer_test.dart`

## Minimal adapter contract

Provider adapters must expose Weave concepts only:

- provider refs, not provider-native IDs in UI state;
- explicit capabilities instead of pretending every provider supports comments, attachments, webhooks, or incremental sync;
- support-safe errors: unauthorized, forbidden, not found, conflict, rate limited, offline, validation, provider unavailable, unknown;
- paged list/sync responses with cursors, ETags, or timestamps;
- normalized `TaskBoardEvent` output for automation, notifications, recent activity, audit, and support diagnostics.

## Vikunja spike (#119)

Recommendation: keep Vikunja as the first implementation candidate, but require reconciliation in addition to webhooks.

Findings:

- API/auth fit is strong: Vikunja documents instance-local Swagger/OpenAPI at `/api/v1/docs`, a public docs instance, `/api/v1/docs.json`, and API-token Bearer auth as the recommended mode. Source: <https://vikunja.io/docs/api-documentation/>
- Coverage is broad for the first adapter: the OpenAPI paths include projects, project views/buckets, tasks, task position, assignees, comments, labels, attachments, relations, and project/user webhooks. Source: <https://try.vikunja.io/api/v1/docs.json>
- Pagination/error fit needs adapter wrapping: API responses expose paginated collection endpoints plus provider-specific error bodies/codes, so Weave should surface typed adapter errors rather than raw Vikunja failures. Source: <https://try.vikunja.io/api/v1/docs.json>
- Webhook payloads already use task-like event names such as `task.created`, include ISO timestamps, and include `task` plus `doer` objects; this maps cleanly into the Weave event envelope. Source: <https://vikunja.io/docs/webhooks/>
- Risk: webhook delivery is documented as at-most-once with no retry on failed deliveries. Therefore `webhookEvents` is useful but not enough; adapters need periodic incremental reconciliation and idempotency keys.
- Data portability is better than average: Vikunja documents full data export/import for backup and migration, and the OpenAPI includes `/user/export*` plus multiple migration endpoints. Sources: <https://vikunja.io/help/import-and-export/>, <https://try.vikunja.io/api/v1/docs.json>
- Licensing/deployment note: Vikunja is self-hostable as a single binary or Docker container, and the upstream repository reports AGPL-3.0. Sources: <https://vikunja.io/docs/installing/>, <https://github.com/go-vikunja/vikunja>
- Product-model note: Vikunja project/view/bucket/task terms should map to Weave workspace/project/board/column/task behind provider refs.

## OpenProject accessibility benchmark (#120)

Recommendation: use OpenProject as an accessibility and mature-workflow benchmark only for now; do not choose it as the first provider until a later auth/sync spike.

Findings:

- OpenProject's accessibility checklist targets WCAG 2.1 AA and explicitly calls out logical reading/navigation order, non-color-only information, doubled text size, full keyboard availability, no keyboard traps, visible focus, and informative labels. Source: <https://raw.githubusercontent.com/opf/openproject/dev/docs/development/accessibility-checklist/README.md>
- OpenProject documents keyboard shortcuts/access keys for global search, work package navigation, new work package, edit/preview, more menu, and list item movement. Weave should prefer discoverable command menus and avoid shortcuts that conflict with screen readers. Source: <https://raw.githubusercontent.com/opf/openproject/dev/docs/user-guide/keyboard-shortcuts-access-keys/README.md>
- OpenProject's design docs use ARIA live regions for dynamic announcements. Weave's future board UI should similarly announce create/move/complete/conflict outcomes without moving focus. Source: <https://raw.githubusercontent.com/opf/openproject/dev/lookbook/docs/patterns/accessibility/18-aria-live.md.erb>
- OpenProject boards are mature workflow references: Basic boards can move cards without mutating work-package fields, while Action boards map lists to status/assignee/version/subproject fields and movement changes those fields. Source: <https://www.openproject.org/docs/user-guide/agile-boards/>
- API fit is likely rich but complex: work packages expose attachments, comments/activities, status, assignee, priority, project, custom fields, watchers, relations, optimistic lock version, and update links. Source: <https://www.openproject.org/docs/api/endpoints/work-packages/>

Pitfalls to avoid:

- do not require drag-and-drop for movement;
- do not hide state changes behind color-only chips;
- do not move focus unexpectedly after card mutations;
- do not rely on unannounced background refreshes for conflict or permission outcomes.

Concrete Weave UI requirements from this benchmark:

- keyboard-only creation, opening, moving, assigning, completing, and filtering;
- visible focus and deterministic reading order across columns/cards;
- non-drag alternatives for every move operation;
- status, priority, due-date, conflict, and permission states as text, not color alone;
- screen-reader announcements for create/update/move/delete/conflict;
- text scaling tolerance so action menus remain reachable.

## Nextcloud Deck bridge spike (#121)

Recommendation: Deck is suitable as a bridge/import or low-friction fallback adapter because Nextcloud is already in Weave's stack, but it should not define the Weave product surface.

Findings:

- Deck's API is authenticated through the Nextcloud app route and requires `OCS-APIRequest: true`; it defines board/stack/card/label naming and exposes API versions. Source: <https://raw.githubusercontent.com/nextcloud/deck/main/docs/API.md>
- API coverage is good for a bridge: boards, stacks, cards, labels, assignments, archives/unarchives, reorder, comments, attachments, config, and import endpoints are documented. Source: <https://raw.githubusercontent.com/nextcloud/deck/main/docs/API.md>
- Incremental sync is plausible through `If-Modified-Since`, `If-None-Match`, ETags on board/stack/card/attachment endpoints, and parent ETag propagation when child elements change. Source: <https://raw.githubusercontent.com/nextcloud/deck/main/docs/API.md>
- Event gap: no first-class Deck webhook source was found in the API notes. The prototype treats Deck events as polling/snapshot-derived events with ETag-backed idempotency.
- Auth/session fit: Deck should reuse the existing Nextcloud session/facade direction instead of adding Flutter-side raw credentials.
- Product risk: Deck-specific terms and limitations must stay in adapter docs/support diagnostics only; Weave UI remains provider-neutral.

Gap categories:

- API: good CRUD coverage for bridge use; comments/activity and attachments are available, but Deck's board/stack/card model needs translation.
- Auth: likely reusable through the existing Nextcloud session/BFF direction; do not add raw Deck credentials in Flutter.
- Sync/event: polling/ETag reconciliation is required unless a later server-side Deck event source is proven.
- Accessibility: API docs do not prove accessible workflows; Weave must keep its own keyboard/screen-reader/non-drag requirements.
- Product-model mismatch: Deck may be an import/bridge surface, not the normal Weave boards vocabulary or UX.

## Event normalizer (#123)

Recommendation: the first production event normalizer belongs in the backend-owned BFF/interop boundary, eventually aligning with the disabled-by-default interop gateway foundation in `weave-backend` [#38](https://github.com/masssi164/weave-backend/issues/38). A public connector SDK remains deferred until real connectors prove the internal framework, matching `weave-backend` [#43](https://github.com/masssi164/weave-backend/issues/43).

This PR keeps a Flutter-side, testable domain prototype so presentation state can consume normalized events without provider-specific logic when the backend contract arrives.

Required event envelope:

- idempotency key;
- event type;
- actor ref;
- occurred-at timestamp;
- workspace/project/board/task identifiers;
- provider ref with external ID, optional URL/version/ETag/raw type;
- redaction level;
- support-safe payload.

Initial event types:

- `task.created`, `task.updated`, `task.completed`, `task.archived`, `task.moved`;
- `assignment.changed`, `label.changed`, `priority.changed`, `due_date.changed`;
- `comment.added`, `attachment.changed`;
- `sync.conflict_detected`.

Operational requirements before production:

- store idempotency keys and dedupe before notifications/recent activity;
- preserve provider ordering metadata but tolerate out-of-order deliveries;
- reconcile webhook gaps with periodic sync for at-most-once or polling-only providers;
- redact user content from support bundles unless explicitly consented;
- keep raw provider payloads out of Flutter state and support logs;
- represent conflict events before overwriting local or provider state.
