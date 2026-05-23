# ADR: flexible Context/Space model v0

Status: proposed implementation seam
Date: 2026-05-18
Owner: `weave-backend` for API seams; `weave`/`weave-infra` consume through follow-up specs and tests.

## Decision

Weave will use a flexible **Context** model for collaboration scope instead of making `Team` and `Channel` hard backend primitives.

A Context is a product-owned collaboration boundary that can link chat, calendar events, files, boards/tasks, meeting threads, connector grants, and audit entries. `workspace`, `team`, and `channel` remain useful default templates in UX and seeded local/dev data, but backend contracts must treat them as context kinds/templates rather than fixed hierarchy requirements.

## Why now

Weave is moving from a hard workspace/team/channel tree toward flexible contexts/spaces while preserving familiar workspace, team, and channel templates. The backend already has facade foundations for Calendar, Boards/Tasks, Files, connector guardrails, and audit-aware operations. This ADR records the executable seam that lets future work align without breaking active feature gates.

## Vocabulary

| Term | Backend meaning | UX/product note |
| --- | --- | --- |
| Tenant/Org | Hard isolation and deployment boundary. | SaaS-ready isolation contract; not optional. |
| Context | Stable product scope for work, permissions, links, and audit. | Users may see this as a space, project, team, channel, meeting, or custom context. |
| Space | Friendly UX name for a user-visible context. | Use where `Context` sounds too technical. |
| Template | Initial shape or copy applied to a context. | `team` and `channel` are templates, not hard backend tables/routes. |
| Provider ref | Mapping to Matrix, Nextcloud, OpenProject-first Boards workspace-sync, Vikunja/Deck fallback comparisons, etc. | Hidden except support-safe diagnostics and admin/fallback views. |
| Meeting Thread | First-class context-adjacent object linking event, chat, files, tasks, and decisions. | Must stay safe with Matrix E2EE. |

## Contract direction

The backend should introduce Context-compatible seams before adding large runtime behavior:

- Context IDs are Weave-owned IDs: `tenant_id` + `context_id`. Provider IDs remain in `provider_bindings`.
- A context has canonical `tenant_id` and `context_id` fields, optional `parent_context_id`, `kind`, `template`, `display_name`, lifecycle state, capabilities, and audit/consent policy hints. Context graph state is represented through separate edges, memberships, provider bindings, and ReBAC tuples.
- `kind` is extensible. The initial safe vocabulary is `workspace`, `space`, `project`, `team`, `channel`, `meeting`, and `custom`.
- Context graph edges capture `contains`, `linked_to`, `calendar_for`, `board_for`, `files_for`, `thread_for`, and `imports_from` relationships.
- Memberships and a minimal ReBAC tuple adapter shape are part of the first schema so authorization can evolve without baking roles into feature-specific DTOs.
- The first ReBAC adapter evaluates only Weave Context object refs (`context:<context_id>`) with known relations (`context_viewer`, `context_editor`, `context_admin`, `context_member`, `context_owner`). Unknown relations and raw provider-binding object refs fail closed.
- Provider bindings include capabilities plus cursor/webhook references, but not provider tokens or raw errors.
- Existing Calendar scope types (`workspace`, `team`, `channel`) can remain compatibility values while new contracts model them as context templates.
- Boards/Tasks projects and Calendar scopes should be able to attach to a context without depending on a hard team/channel hierarchy.
- Connector and assistant grants are context-scoped plus source-scoped. External-system/source consent must be explicit and revocable.
- Agentic or connector-based write actions must produce audit records before promotion beyond sandbox/gated runtime.
- Files keep Nextcloud visible as the provider/fallback, but normal product navigation should link files to contexts through Weave-owned metadata rather than raw Nextcloud folder semantics.


## Ordered iteration plan

1. **Context Graph Schema** — land this first backend PR: ADR plus contract artifact for `tenant_id`/`context_id`, context graph edges, memberships, provider bindings, and ReBAC tuples. No runtime routes.
2. **ReBAC Adapter MVP** — add internal authorization port/tests that evaluate tenant-scoped context memberships and tuple relationships for context view/edit/admin decisions; preserve tenant isolation, membership projection through context edges, unknown-relation fail-closed behavior, and no raw provider-binding bypass.
3. **Connector SDK Skeleton** — define manifest, capabilities, cursors, webhooks, commands, support-safe errors, and redaction policy without enabling live writes.
4. **OpenProject Board workspace-sync MVP** — prefer OpenProject as the first provider-backed, provider-led source-of-truth sync path because its API is the best first validation target; map it into context-bound boards/tasks through the connector skeleton. Vikunja and Nextcloud Deck remain comparison/fallback candidates only. Keep provider writes disabled until smoke/export/accessibility plus audit/consent gates pass.
5. **Audit Event Pipeline / Consent internal seam** — append-only CloudEvents-like audit envelope for connector/assistant write attempts, consent grant/revocation events, support-safe redaction, and fail-closed missing-audit behavior. This remains backend-only and does not enable live provider writes.
6. **Consent Center MVP** — backend consent grants/revocation storage and frontend/admin surface for context-scoped + source-scoped connector/assistant access.
7. **Meeting Thread Schema** — canonical MeetingThread object with optional calendar event, Matrix thread/room, task, and file bindings; safe when Matrix content is E2EE.
8. **Client-side Personal Index MVP** — per-user local index over accessible contexts/providers for discovery/search only; no upload or server-side personal data hoarding.

## Non-goals for this ADR

- No public `/api/contexts` route is introduced by this PR.
- No migration of existing Calendar, Boards/Tasks, Files, or connector runtime code is performed here.
- No agentic writes in team rooms or shared contexts are enabled.
- No live Boards/Tasks provider runtime is made mandatory by this PR. It records the next-iteration preference to validate OpenProject first for provider-backed workspace-sync because of its API; Vikunja and Nextcloud Deck remain comparison/fallback candidates only.
- No private calendar ingestion is enabled; private calendars require explicit connector consent, revocation, audit, and data-limit gates.

## First implementation slices enabled by this ADR

1. Backend contract artifact for the Context schema and a test that keeps it from drifting back into hard team/channel assumptions.
2. Workspace spec update to replace hard hierarchy language with Context/Space language while preserving workspace/team/channel templates.
3. Backend DTO/API slice for context discovery, behind feature gates, with Calendar scopes and Boards workspace state linked by context IDs.
4. Frontend naming pass: user-facing `Space`/`Context` copy where appropriate; retain `Team`/`Channel` as setup templates and labels when useful.
5. Consent/audit schema slice for connector and assistant writes, explicitly context-scoped and source-scoped.

## Validation gates

- Contract artifacts exist and are test-covered.
- Docs continue to say Weave is the product boundary; Matrix/Nextcloud/task providers are implementation/admin/fallback/provider layers.
- Team/channel words may appear as templates or compatibility scope types, but not as the only backend information architecture.
- Runtime flags stay fail-closed for connectors, live Boards/Tasks providers, private calendars, and agentic writes.
- OpenProject-first workspace-sync remains a connector validation path with provider writes disabled until audit/consent promotion; no agentic/team writes.
