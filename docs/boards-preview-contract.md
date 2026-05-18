# Boards/Tasks preview contract

Boards/Tasks is a provider-neutral, active feature-gated preview slice. It is **not** a live product surface. The only reachable runtime slice is a hidden, authenticated, feature-flagged local preview facade; live provider adapters remain disabled until a later promotion spec defines provider auth, DTO compatibility, OpenAPI publication, runtime setup, smoke/E2E, and accessibility gates.

This document is intentionally separate from Product screenshots and product-surface documentation. Do not add Boards/Tasks screenshots to the main Product screenshots section unless the module is fully navigable and release-enabled by a later spec.

## Scope in this backend slice

Implemented now:

- Provider-neutral Java domain records for projects, boards, columns, tasks, labels, comments, attachments, provider refs, capabilities, and normalized events.
- A repository port that hides provider pagination, IDs, vocabulary, and raw errors from callers.
- A support-safe error vocabulary aligned with the workspace spec.
- A Vikunja HTTP/status error mapper that converts provider failures into Weave codes without leaking raw provider messages, URLs, or tokens.
- A provider-neutral task/board event normalizer, no-op event publisher boundary, and preview JSON/OpenAPI schema artifacts under `src/main/resources/contracts/`.
- An OpenProject-first read-sync mapper that translates projects/statuses/work packages into Weave concepts without enabling runtime HTTP calls.
- A connector manifest skeleton contract for capabilities, cursors, webhooks, commands, support-safe errors, secret references, redaction policy, and write-disabled provider posture.
- Vikunja and Nextcloud Deck comparison/fallback adapter boundaries; they remain useful for comparison/import planning but no longer define the first provider path.
- Fail-closed Vikunja, OpenProject, and Nextcloud Deck repository placeholders that advertise preview/read-sync/fallback capabilities but do not perform runtime HTTP calls.
- A hidden local/in-memory backend preview facade behind `weave.boards.preview.runtime-enabled` that proves provider-neutral create, move, and complete operations without drag-only UI assumptions.

Not implemented now:

- No live provider navigation or product enablement until promotion gates pass.
- No live provider runtime configuration, secrets, Caddy routes, or smoke checks.
- No user-facing screenshots.
- No notifications, audit streams, webhooks, or automation consumers.

## Hidden preview API

The feature-gated preview API is deliberately narrow and disabled by default for live providers:

- `GET /api/boards/preview`
- `POST /api/boards/{boardId}/tasks`
- `POST /api/boards/tasks/{taskId}/move`
- `POST /api/boards/tasks/{taskId}/complete`

All routes require the normal authenticated `weave:workspace` backend boundary. They return provider-neutral Weave domain shapes and support-safe `boards-*` errors. The local adapter stores only in-memory preview data, exposes `releaseStatus = active-feature-gated-preview`, and must not be documented as a customer-ready module.

## 2026-05-14 issue/spec matrix

| Track | Source | This implementation wave | Status |
| --- | --- | --- | --- |
| Boards/Tasks domain | Workspace spec `specs/14-boards-tasks-domain-and-provider-adapter-contract.md`; `weave` issues #119-#123 | Adds the feature-gated backend runtime slice and lets the Flutter feature-gated preview consume it when authenticated. | Implemented as active feature-gated preview; not live-provider-enabled. |
| Calendar private/workspace model | `weave-backend` #52 | Existing calendar facade remains workspace-scoped and private-personal CalDAV stays fail-closed until the access model is finished. | Deferred from this Boards runtime PR; do not add fake private calendar UI. |
| Calendar external CalDAV credentials/profile | `weave-backend` #56; `weave-infra` #54 | No credential/profile issuing in this wave because revocation, storage, redaction, and support-bundle handling are the acceptance gate. | Deferred; keep client setup secret-free and truthful. |

Calendar follow-up must land as a separate security-focused wave before Apple `.mobileconfig`, DAVx5, desktop CalDAV discovery, or private-personal calendar access is exposed.

## Provider-neutral model

The Weave model follows `../specs/14-boards-tasks-domain-and-provider-adapter-contract.md`:

- `WeaveProject` maps to a provider project/workspace grouping.
- `Board` is the Weave-owned board concept.
- `BoardColumn` maps provider lists/buckets/statuses into `not_started`, `in_progress`, `blocked`, `done`, or `archived`.
- `TaskItem` is the provider-neutral task/card shape consumed by future API/UI work.
- `ProviderRef` preserves provider IDs, URLs, etags, versions, and sync timestamps for diagnostics, export, migration, and conflict handling without making those references the primary product identity.

## OpenProject-first read-sync boundary

OpenProject is now the preferred first provider-backed read-sync validation path because its API gives the best near-term seam for provider-led source-of-truth boards. The current mapper translates:

- OpenProject project → Weave project and board
- OpenProject status → Weave board column
- OpenProject work package → Weave task item

The repository placeholder fails closed with `provider_unavailable`; it declares read-sync capabilities but performs no HTTP calls and enables no product/runtime provider. A later promotion spec must define authentication, visibility filtering, cursor persistence, route DTOs, smoke/E2E, export/backup, and accessibility gates before any OpenProject runtime is reachable. The first path is read-only: no provider writes, no agentic/team writes, and no raw OpenProject UI as normal Weave UX.

## Vikunja fallback/comparison boundary

Vikunja remains a lightweight, self-hostable, API-oriented comparison/fallback candidate. Its mapper still translates Vikunja projects/buckets/tasks into Weave concepts, and its support-safe HTTP/status mapping remains useful for future comparison. Vikunja is no longer the first strategic provider path.

## Provider spike contracts

The backend preview layer now declares three disabled adapter contracts against the same `BoardsRepository` port:

- `OpenProjectBoardsRepository`: preferred read-only-first provider seam; declares comments, attachments, non-destructive archive, incremental sync, custom fields, and accessible non-drag move compatibility in the contract, but is disabled for runtime use.
- `VikunjaBoardsRepository`: comparison/fallback candidate; supports comments, attachments, non-destructive archive, webhooks, incremental sync, checklists, and accessible non-drag move commands in the contract, but is disabled for runtime use.
- `NextcloudDeckBoardsRepository`: Nextcloud-adjacent fallback bridge/import candidate; declares comments, attachments, and non-destructive archive, but avoids claiming webhooks or incremental sync until tested.

All three fail closed with support-safe `provider_unavailable` errors until a promotion spec defines auth, route DTOs, OpenAPI publication, smoke/E2E coverage, export/backup, and accessibility gates.

## Event normalizer

`TaskBoardEventNormalizer` is the backend-owned preview boundary for provider activity from Vikunja webhooks, OpenProject activity records, Deck polling/ETag changes, or a later connector gateway. It:

- accepts provider draft events through `TaskBoardEventInput`;
- emits provider-neutral `TaskBoardEvent` envelopes with stable idempotency keys;
- records provider identity only as support/sync metadata;
- preserves ordering inputs such as source event id and provider timestamps in payload fields without publishing routes;
- redacts support-unsafe payload keys such as tokens, secrets, raw messages, credentials, authorisation headers, and provider URLs before support-safe events leave the adapter boundary.

This is still preview-only and has no notification, audit, webhook, or live runtime publication.

## Contract artifacts

- `src/main/resources/contracts/boards-preview.openapi.yaml` contains preview schema components only and intentionally has `paths: {}`.
- `src/main/resources/contracts/task-board-event.schema.json` contains the preview normalized event envelope.
- `src/main/resources/contracts/connector-manifest.schema.json` contains the internal connector manifest skeleton for OpenProject-first read sync and later providers.

These artifacts are open contract drafts for implementation alignment; they are not a published live-provider API surface.
