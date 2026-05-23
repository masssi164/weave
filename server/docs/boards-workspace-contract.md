# Boards/Tasks workspace contract

Boards/Tasks is an active v0.1 dogfood-production Weave workspace surface. Weave owns the product API, DTOs, Context/Space authorization, audit, and support-safe errors; providers stay behind backend adapters.

## Runtime posture

- Runtime remains fail-closed by default: `weave.boards.runtime-enabled=false` and `weave.boards.provider=local-workspace` unless infra/operator configuration opts in.
- Older compatibility environment/property names may be accepted internally for migration only; the documented v0.1 runtime keys use workspace naming.
- The authenticated workspace endpoint is `GET /api/boards/workspace`.
- User task actions use backend routes for create, move, complete, status update, and decision-link operations.
- All routes require the normal authenticated workspace boundary plus Context/Space authorization.
- Responses use support-safe `boards-*` errors and provider-neutral domain shapes.

## v0.1 write contract

User writes are in scope only when explicit, attributable, authorized, and auditable:

- local workspace provider: supports provider-neutral task create, move, complete, status update, and decision-link operations for dogfood UX and tests.
- OpenProject provider: supports workspace-sync read evidence through the backend facade; provider writes remain refused until later audit/consent promotion proves provider-write safety.
- agent/team writes remain out of v0.1 until sandboxing, tool-whitelist, consent, and audit contracts are accepted.

## Provider safety boundary

OpenProject is the first real provider-backed workspace-sync engine. It is optional, backend-held-token only, and never a direct Flutter/provider-secret dependency. Backend sync metadata must remain context-scoped, user-write-audited, and support-safe; raw provider URLs, offsets, errors, and credentials are not allowed in Weave responses or support bundles.

## Executable evidence

- `src/main/resources/contracts/boards-workspace.openapi.yaml` declares the provider-neutral workspace API.
- `src/main/resources/contracts/task-board-event.schema.json` declares the normalized event envelope.
- `src/test/resources/features/openproject-boards-workspace.feature` binds OpenProject fail-closed, workspace-sync, Context/Space denial, support-safe metadata, and provider-write refusal scenarios.
- Controller/service tests prove authenticated workspace access, local user task writes, audit publication, support-safe errors, and OpenProject provider refusals.
