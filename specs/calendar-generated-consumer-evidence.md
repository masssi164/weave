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
