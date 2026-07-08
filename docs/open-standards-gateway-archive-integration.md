# Open Standards Gateway Archive Integration

Status: implementing; archive full target tracked; not complete.

Evidence marker: `OPEN_STANDARDS_GATEWAY_ARCHIVE_FULL_TARGET_TRACKED`

This page records the July 2026 open-standards gateway archive as an implementation/evidence contract. The archive target is larger than the current PR #1043 server/client MVP: Files, Calendar, Chat, Calls, native integration, MCP/tool boundaries, and protocol credentials all need executable evidence before the umbrella can close.

## Current Gap Inventory

| Gap id | Domain | Required before complete claim |
| --- | --- | --- |
| `files-full-webdav` | Files/WebDAV | Implement `MOVE`, `COPY`, `LOCK`, `UNLOCK`, streaming PUT, quota 507 mapping, lock discovery, supported lock properties, and provider-atomic or Weave-ledger preconditions. |
| `calendar-caldav-parity` | Calendar/CalDAV | Implement `calendar-multiget`, `sync-collection`, `MKCALENDAR`, recurrence DST evidence, ETags on GET/DELETE, full discovery paths, and Flutter CalDAV/iCalendar data-plane cutover. |
| `chat-matrix-parity` | Chat/Matrix | Use a real Matrix homeserver as the member data plane, add governed provisioning flow, device revocation, support-safe E2EE/device state, and retire the Spring projection as final parity. Spring projection is not final parity. |
| `calls-join-grants` | Calls/WebRTC | Add remove participant, grant revocation, expired reuse denial, Flutter LiveKit join/publish/subscribe, LiveKit provider port hardening, and support-safe audit for create, join, leave, removal, and end. |
| `protocol-credentials` | Identity/OIDC and native protocols | Add Weave-issued device credentials for `WEBDAV_FILES` and `CALDAV_CALENDAR`: scoped, expiring, revocable, audited, never provider credentials, and never returned after creation. |
| `admin-openapi-control-plane` | Admin/OpenAPI | Keep OpenAPI to manifest, setup, readiness, revoke, device credentials, admin/provider configuration, generated convenience models, and Calls join-grant control. |
| `native-client-boundary` | Flutter/native OS | Return Weave WebDAV/CalDAV endpoints and Weave credentials only, keep responses screen-reader friendly, and prove revoke disables OS integration access. |
| `mcp-domain-boundary` | MCP/Agents | Keep MCP tools on Weave domain semantics for files, calendar, chat, and calls; outputs must not contain raw provider URLs, credentials, tokens, or downstream payloads. |

## Spring.ai.mcp Block

Spring.ai.mcp remains blocked. The hard rule from the archive still applies: do not work on Spring.ai.mcp until Matrix chat facading, WebDAV files facading, CalDAV calendar facading, Calls join grants/WebRTC boundary, native OS/client integration readiness, and Cucumber/Gherkin evidence are reflected in executable gates and implementation evidence.

## Current Evidence Boundary

Current branch evidence covers partial files WebDAV read/write, a CalDAV server MVP, a Matrix-shaped Spring projection, Flutter files WebDAV write paths, and Calls control endpoints. That evidence is useful, but it must stay labelled as partial until the gap inventory above is closed with tests, client/server implementation, and CI/PR evidence.
