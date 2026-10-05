# Calendar generated consumer and integrated evidence contract

Status: implementation in progress for #1472, #1479 and #1480.
Classification: cross-repo-contract. This repository records implementation and
validation against pinned corpus `3b27ad2e667e462d9f63ff9c58fc02469e167f9b`,
`steering/release-2026-10-product-consolidation.md` and `domains/calendar/spec.md`.
The Server contract is described in `specs/calendar-user-api-contract.md`.

## Deployment and ownership

The Calendar User API resolves the canonical organization's single ACTIVE Calendar
binding before provider access. The host-only development profile and the exact
isolated E2E overlay explicitly bootstrap the selected native Calendar binding with
`configuration:calendar:deployment`. Bootstrap is disabled by default in Server;
an existing conflicting binding fails without replacement. These development/test
inputs neither adopt an external provider nor activate a migration. Persistent
operator environments require explicit reviewed bootstrap configuration; no live
stack is changed by this work.

The opt-in properties are `weave.provider-bindings.bootstrap.calendar.enabled`,
`.organization-ref`, `.adapter-key`, and `.configuration-ref`. The organization
must equal `WEAVE_CONTEXT_AUTHORIZATION_DEFAULT_TENANT_ID`; the adapter must match
`weave.calendar.provider`. Existing environment provider selection cannot silently
route a request to a different provider. Public Calendar consumers use only the
server-generated User API. CalDAV remains a southbound mechanism.

## Generated consumers and actual behavior

Generate User/Admin artifacts and affected JVM, Dart and TypeScript consumers from
Server controllers and DTOs. No transport schema or generated file is hand edited.
Calendar payload DTOs preserve DATE/FLOATING/UTC/ZONED and typed recurrence without
provider UIDs, ETags or collection paths becoming public references.
Closed write objects declare `additionalProperties: false` from their server
annotations, matching runtime rejection of unknown fields. Recurrence interval is
explicitly required because an omitted primitive defaults to zero and is rejected
by server validation; generation must not advertise that invalid omission as valid.

Product E2E replaces its handwritten northbound CalDAV event journey with the
generated JVM User client. Existing iCalendar projection fixtures remain reusable
protocol evidence and are not deleted. In the existing isolated Compose
run it creates events through the actual member session, checks query/read/update
and strong version preconditions, and independently denies outsider access.
Temporal values, event identity, authorized scope and meeting-thread correlation
must survive provider readback and the existing restart phase. Cleanup uses the
same generated API where available, followed by teardown of only the exact owned
namespace. Failure diagnostics report stages/statuses, never tokens or raw payloads.
Generated compilation and fixture tests do not establish live acceptance.

Run `generatedApiCi`, applicable Server Calendar/persistence tests, product E2E
contract tests, infra product/Compose checks, `specCorpusConformance`, and the
actual Full Compose E2E workflow. Flutter and MCP journey evidence remains explicit
until the corresponding consumers are integrated; no Calendar closure is inferred
from the Server-only increment. Migration/cutover remains #1498 scope.

## Flutter evidence reconciliation

The current Flutter Calendar scenario uses generated User operations for discovery,
agenda, event reads and versioned mutations. Its transport tests independently
assert temporal kinds, attendee and meeting-thread metadata, create retry identity,
precondition failures and session invalidation. Calendar state must not restore an
old member or scope snapshot after a late response. Floating/zoned wall-clock values
must survive the host time zone's daylight-saving gaps without normalization.
Visible event filtering uses interval overlap and preserves the exclusive all-day
end; recurring occurrences cannot silently overwrite or delete their master.

The former `@calendar-flutter-caldav` scenario is replaced by
`@calendar-flutter-generated-user`. Nine CalDAV server fixtures remain historical
protocol/integrity evidence. Their archive inventory count changes deliberately
from ten to nine; this does not remove a permission, temporal or version assertion.
The separate July Flutter protocol feature remains an explicitly historical target,
not current #1470 acceptance. Its old DAV/control-only direction cannot constrain
new product consumers. Current live Calendar behavior is mapped separately to the
isolated product journey and is not inferred from Flutter transport fixtures.

Generated Dart DATE values use a UTC field container on decode and serialize their
calendar fields directly, without converting them to another instant. This is a
deterministic generator correction, not a hand edit of generated models. Date-only
wire round trips must preserve a civil date even when the host zone skipped that
date (Pacific/Apia, 2011-12-30). Date-time values still preserve their instant. The
generator fails if its pinned upstream template shape changes, and consumer wire
tests run in UTC, Europe/Berlin and Pacific/Apia to expose host-zone dependence.
`clientGeneratedCalendarTemporalWire` enforces these three zones as a required
dependency of `clientCi` and therefore `generatedApiCi`.

## Integrated local qualification

The integrated Calendar slice passes `generatedApiCi`, `acceptanceContract`,
`specCorpusConformance`, `docsStructureCheck`, `gradleStructureCheck` and
`specContract`. The code-first export has 64 User and 29 Admin operations, including
six new Calendar User operations. The separate Admin contract is unchanged by this
Calendar increment. Freshness checks regenerate in temporary locations and leave
the checked-in sources unchanged.

- Server suite: 1,117 tests, no failures/errors; four normalized Calendar cases are
  deliberately assigned to the PostgreSQL task.
- PostgreSQL Calendar task: eight Calendar cases plus twelve existing Cucumber
  cases, no failures/errors/skips.
- Full Flutter suite: 570 passed, one existing offline-contract-only skip. Analysis
  passes. The additional seven generated temporal wire tests pass independently in
  each of UTC, Europe/Berlin and Pacific/Apia.
- Admin Console: build and 37 tests pass. JVM product E2E: 49 fixture tests pass;
  MCP: 20 tests pass; generated JVM clients compile and the User client contract
  fixture passes. These results do not claim a generated MCP Calendar tool.
- Independent Flutter review findings on host DST normalization, stale mutation
  rollback and overlapping events were fixed with regressions. Generator DATE
  correction received a separate scoped review without a material finding.

The live Compose Calendar journey and real Flutter single-sign-in/recovery proof
remain required. A passing source/fixture gate does not establish either outcome.
