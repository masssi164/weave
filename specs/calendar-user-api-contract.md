# Calendar User API implementation contract

Status: backend Calendar slice and generated transient-preview consumer are under
integration for #1476/#1479. Real product runtime integration remains a separate
acceptance requirement.
Classification: cross-repo contract; Server owns implementation. This file is
implementation evidence for pinned corpus `71a2093d91ad300bc733ede66080bddc90f97e60`,
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
  repository. Identity mappings contain no event payload. Agenda browsing of an
  unmapped provider event returns a transient event preview and occurrence projections,
  never a durable Event mapping, Resource, or relationship. The preview has a bounded
  opaque handle, provider-backed readback, current organization/actor/Calendar scope
  checks, active-binding revision and provider-version checks. It has no stable Event
  ID or meeting-thread reference and is not an authorization grant. Explicit
  materialization through a generated User operation rechecks the provider and creates
  or reuses one stable mapping before an existing event can be edited or deleted.
  Expiry, tampering, stale binding/version, member denial and audit failure must not
  publish a mapping. Raw collection IDs, UIDs, paths, ETags and sync tokens remain
  private. Existing provider payload must never override the authorized scope.
  Native Calendar's existing normalized temporal/attendee/recurrence/override
  tables must be represented in the entity-first persistence model. The retired
  SQL definitions alone do not establish the current runtime schema. Add those
  missing entity definitions and exercise the real normalized store against
  PostgreSQL, preserving existing table identities and data.

## Temporal and mutation contract

- Public transport represents DATE, FLOATING, UTC and ZONED explicitly, with exactly
  one date, local datetime or instant and a TZID only for ZONED. UTC accepts
  optional all-zero fractional seconds emitted by generated consumers, normalizing
  equivalent whole-second values on read; nonzero fractions are rejected. DATE end is exclusive.
  Agenda evaluation requires an explicit IANA evaluation zone and a bounded window.
  Occurrence instants are query projections; persisted temporal intent is unchanged.
  Expansion includes intervals beginning before the window that still overlap it,
  and fails explicitly when a result limit would truncate the agenda.
- Reuse the canonical Calendar recurrence model, iCal4j codec and bounded evaluator.
  Typed recurrence includes the supported frequency, interval, count or UNTIL,
  BYDAY/BYMONTHDAY/BYMONTH/BYSETPOS/WKST, RDATE/EXDATE and single-instance overrides.
  Preserve DTSTART-compatible UNTIL representations; DATE/FLOATING are not normalized
  to UTC in transport. Reject unsupported properties/parameters, temporal forms or
  recurrence edits atomically instead of discarding fields. Existing provider payloads
  outside the complete transport/codec profile (including unmappable attendee member
  references or override-only properties) fail closed before a replacing write.
  Calendar envelope metadata is limited to VERSION/PRODID/GREGORIAN CALSCALE;
  scheduling METHOD, custom envelope properties and embedded VTIMEZONE require a
  separate preservation contract and are rejected.
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
Root integration owns artifact/client generation, Flutter's preview-to-edit flow,
deployment bootstrap and real Flutter/MCP/provider journeys. Local mocks or
compilation do not close the stories.
Public CalDAV and provider migration acceptance remain deferred.

## Validation evidence

- `:server:test`: 1,158 tests, zero failures/errors. Four authoritative normalized
  Calendar persistence cases are deliberately skipped here and run on PostgreSQL.
- `:server:postgresJpaTest --tests '*NativeCalendarProviderAdapterTest'`: all nine
  Calendar tests pass with zero skips, using entity-first PostgreSQL tables and the
  production `NativeCalendarRelationalStore`. The existing Cucumber engine also runs
  twelve unchanged Boards scenarios. Evidence includes provider-backed transient
  preview, no browse mapping, idempotent materialization and stable readback after
  adapter/service restart, as well as all four temporal kinds,
  attendees, typed UNTIL/RDATE/EXDATE, moved and cancelled instances, fresh adapter
  read/query, exact versions, stale delete, deletion and database interval constraints.
- HTTP/controller/service tests cover shared human organization admission, unknown
  scope and denied Space without provider writes, stable retry identity and changed
  replay conflict, required/stale write versions, audit failure, unsupported existing
  payload rejection, strict unknown/null input and typed 400/415 errors. The real
  service accepts generated UTC `.000Z`, returns equivalent whole seconds, and rejects
  nonzero fractions before a provider write. Provider transport remains mocked in
  these HTTP cases; root integration owns the real generated-client HTTP journey.
- Calendar recurrence regressions prove all four temporal kinds include intervals
  that start before and overlap an agenda window, UTC supports all four frequencies,
  and result exhaustion fails instead of silently truncating.
- `specCorpusConformance` passes against pinned corpus `71a2093d91ad`;
  `docsStructureCheck`, `checkOpenApiContractFresh` and `checkClientUserApiFresh`
  pass. The full Flutter unit/widget suite passes with one existing skip;
  JVM User/Admin client checks and MCP/product-E2E module tests pass. These are
  build and local test results, not a substitute for a real deployed product journey.

The transient-preview increment adds generated User OpenAPI/Dart operations and a
Flutter preview-to-edit/delete path. Focused server service and HTTP tests assert
opaque previews, zero browse mappings, explicit idempotent materialization,
cross-actor and stale-version denial, and the transport distinction between a
preview and materialized Event. The Flutter transport tests cover provider-backed
readback and materialization ordering. The root generation freshness tasks pass.
This does not close #1476/#1479: real integrated provider and Flutter journeys,
PostgreSQL restart/restore, and MCP/admin behavior still need independent evidence.
Unsupported preservation cases remain blocked; native provider success does not
qualify every possible provider payload.
