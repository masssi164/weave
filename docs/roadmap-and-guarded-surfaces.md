# Roadmap and guarded surfaces

The README showcase is limited to product surfaces that contributors can evaluate directly today: setup, service review, chat, files, and settings. This page keeps in-progress product areas visible without marketing them as fully shipped.

## Teams-like calendar

Calendar is active product scope, but it is not promoted as a normal finished surface in the README showcase. The target product model is workspace/org, team, and channel scheduling backed by the Weave backend calendar facade and Nextcloud/CalDAV. Private personal calendar ingestion is not a product goal for the current MVP path.

[<img src="assets/roadmap/06-calendar-roadmap-readiness.svg" alt="Weave calendar roadmap visual showing workspace, team, and channel scheduling scopes with channel event CRUD validated through the backend facade." width="560">](assets/roadmap/06-calendar-roadmap-readiness.svg)

Current evidence and boundaries:

- Shared scope metadata and channel event create/read/update/delete are live-stack contract scope.
- Meeting-thread attachment is follow-up work.
- Raw Nextcloud Calendar is not the normal product UX.
- Backend actor credentials must not leak into generated setup artifacts.

## Boards/tasks

Boards/tasks are active Weave scope behind feature gates and provider-neutral backend contracts. The current product language must not claim a live Vikunja, Deck, OpenProject, or other provider integration unless that provider path is configured and validated.

[<img src="assets/roadmap/07-boards-feature-gate.svg" alt="Weave boards feature-gate visual showing provider-neutral task columns, keyboard movement actions, and screen-reader-friendly status labels." width="560">](assets/roadmap/07-boards-feature-gate.svg)

Current evidence and boundaries:

- Flutter may render provider-neutral board/task DTOs and accessible non-drag actions.
- Provider auth, pagination, sync, webhook validation, export/import, and error translation belong behind backend or connector adapters.
- Any provider-specific claim must be tied to a validated runtime and documented capability boundary.

## Matrix E2EE

Matrix E2EE is active chat architecture scope, not a completed product claim. Weave must not claim chat is end-to-end encrypted until encrypted-room behavior, device verification, key backup/recovery, lost-device handling, multi-device behavior, and accessible verification/recovery UX are implemented and validated.

## Related contracts

- [Product scope: calendar hierarchy, Matrix E2EE, and Boards](product-calendar-e2ee-boards-scope.md)
- [Product acceptance flows](product-acceptance-flows.md)
- [Quality and acceptance evidence](quality-and-evidence.md)
