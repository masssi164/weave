# Calendar User API implementation contract

Status: implementation in progress for #1472 and #1479.
Classification: cross-repo contract; Server owns implementation. This file is
implementation evidence for pinned corpus `3b27ad2e667e462d9f63ff9c58fc02469e167f9b`,
`steering/release-2026-10-product-consolidation.md`, `domains/calendar/spec.md` and
`docs/reference/calendar-support-profile.md`. It does not redefine product truth.

## API and authority

- Add generated User operations for calendar discovery, bounded agenda, event
  read/create/update/delete under `/api/calendar/calendars`. The legacy Calendar
  facade and public CalDAV setup routes do not implement these operations.
- Every request requires the admitted human organization, the Calendar capability,
  and current Context/Space VIEW or EDIT authorization before provider access.
  Workspace scope is `workspace-default`. Team/channel scopes are discovered only
  from configured organization CONTAINS edges; no example team or channel is invented.
  Channel ownership must match its configured team. Event scope cannot be changed by
  an update or provider extension. The application derives stable calendar/event
  references and meeting-thread correlation references; this does not create a chat room.
- Resolve exactly one ACTIVE `calendar` binding from `ProviderBindingRepository`
  for the canonical organization. The supported configuration handle is
  `configuration:calendar:deployment`, referring to the existing server-owned
  `weave.calendar.provider` and its adapter properties in this single-organization
  deployment. The adapter key must match that binding. Missing, stale or unmatched
  configuration fails closed. Root integration owns explicit binding bootstrap and
  deployment wiring; this implementation neither auto-activates nor migrates data.
  An opt-in server bootstrap uses `weave.provider-bindings.bootstrap.calendar.enabled`
  (false when absent), `.organization-ref`, `.adapter-key`, and `.configuration-ref`.
  It admits only the configured canonical organization and creates a missing initial
  binding; conflicting existing authority is rejected without replacing it.
- Reuse provider payload storage and the existing private provider object mapping
  repository. Identity mappings contain no event payload. Browsing may record an
  opaque event identity, but does not create a generic Resource or relationship.
  Raw collection IDs, UIDs, paths, ETags and sync tokens remain private. Existing
  provider payload must never override the authorized scope.
  Native Calendar's existing normalized temporal/attendee/recurrence/override
  tables must be represented in the entity-first persistence model. The retired
  SQL definitions alone do not establish the current runtime schema. Add those
  missing entity definitions and exercise the real normalized store against
  PostgreSQL, preserving existing table identities and data.

## Temporal and mutation contract

- Public transport represents DATE, FLOATING, UTC and ZONED explicitly, with exactly
  one date, local datetime or instant and a TZID only for ZONED. DATE end is exclusive.
  Agenda evaluation requires an explicit IANA evaluation zone and a bounded window.
  Occurrence instants are query projections; persisted temporal intent is unchanged.
- Reuse the canonical Calendar recurrence model, iCal4j codec and bounded evaluator.
  Typed recurrence includes the supported frequency, interval, count or UNTIL,
  BYDAY/BYMONTHDAY/BYMONTH/BYSETPOS/WKST, RDATE/EXDATE and single-instance overrides.
  Preserve DTSTART-compatible UNTIL representations; DATE/FLOATING are not normalized
  to UTC in transport. Reject unsupported properties/parameters, temporal forms or
  recurrence edits atomically instead of discarding fields.
- Create uses a required 16–128 character Idempotency-Key to reserve a deterministic
  opaque identity per organization, calendar and actor. Repeating an unchanged create
  can return the same event; reusing the identity for a different payload conflicts.
  Updates replace the complete supported event payload and preserve server-owned
  identity/scope. Update and delete require a single strong public If-Match version;
  missing preconditions return 428, stale versions 412. Translate public versions to
  the private provider version and enforce it atomically at the provider.
- Audit must succeed before a write. Provider exceptions are translated into the
  stable support-safe API envelope. Explicit operation IDs, required headers,
  validation, success/error status codes and temporal schemas come from server code.

## Validation and integration

Use HTTP authorization/contract negatives, service mapping/binding/precondition
checks, temporal/recurrence round trips and actual provider persistence tests.
Include stale identical updates and concurrent delete ordering, unknown CalDAV
properties, wrong scope/organization and zero provider writes on rejection.
Run existing Calendar integrity/security tests and code-first metadata tests.
Root integration owns artifact/client generation, deployment bootstrap and real
Flutter/MCP/provider journeys. Local mocks or compilation do not close the stories.
Public CalDAV and provider migration acceptance remain deferred.
