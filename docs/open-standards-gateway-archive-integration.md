# Open Standards Gateway Archive Integration

Status: implementing; archive full target tracked; not complete.

Evidence marker: `OPEN_STANDARDS_GATEWAY_ARCHIVE_FULL_TARGET_TRACKED`

This page records the July 2026 open-standards gateway archive as an implementation/evidence contract. The archive target is larger than the current PR #1043 server/client MVP: Files, Calendar, Chat, Calls, native integration, MCP/tool boundaries, and protocol credentials all need executable evidence before the umbrella can close.

## Current Gap Inventory

| Gap id | Domain | Required before complete claim |
| --- | --- | --- |
| `files-full-webdav` | Files/WebDAV | Implement `MOVE`, `COPY`, `LOCK`, `UNLOCK`, streaming PUT, quota 507 mapping, lock discovery, supported lock properties, and provider-atomic or Weave-ledger preconditions. |
| `calendar-caldav-parity` | Calendar/CalDAV | Implement `calendar-multiget`, `sync-collection`, `MKCALENDAR`, recurrence DST evidence, ETags on GET/DELETE, full discovery paths, and Flutter CalDAV/iCalendar data-plane cutover. |
| `chat-matrix-parity` | Chat/Matrix | Complete the OIDC-gated Weave Matrix Client-Server facade as the member data plane, backed by the shared Rust/Ruma Matrix core through server JNI and Flutter `flutter_rust_bridge`; prove it against a real Matrix homeserver fixture while keeping Synapse or any other homeserver only as a southbound provider/fixture, add governed provisioning flow, device revocation, support-safe E2EE/device state, durable ledger evidence, and retire raw Chat API-first member data-plane use. |
| `transitional-rest-cleanup` | Chat/Calendar/OpenAPI | Remove or demote deprecated REST member data-plane compatibility routes after the Matrix and CalDAV facades cover their normal behavior; cleanup is tracked by #1044, with Chat retirement tied to #1022 and Calendar parity tied to #967/#1018. |
| `calls-join-grants` | Calls/WebRTC | Add remove participant, grant revocation, expired reuse denial, Flutter LiveKit join/publish/subscribe, LiveKit provider port hardening, and support-safe audit for create, join, leave, removal, and end. |
| `protocol-credentials` | Identity/OIDC and native protocols | Add Weave-issued device credentials for `WEBDAV_FILES` and `CALDAV_CALENDAR`: scoped, expiring, revocable, audited, never provider credentials, and never returned after creation. |
| `admin-openapi-control-plane` | Admin/OpenAPI | Keep OpenAPI to manifest, setup, readiness, revoke, device credentials, admin/provider configuration, generated convenience models, and Calls join-grant control. |
| `native-client-boundary` | Flutter/native OS | Return Weave WebDAV/CalDAV endpoints and Weave credentials only, keep responses screen-reader friendly, and prove revoke disables OS integration access. |
| `mcp-domain-boundary` | MCP/Agents | Keep MCP tools on Weave domain semantics for files, calendar, chat, and calls; outputs must not contain raw provider URLs, credentials, tokens, or downstream payloads. |

## PR 1043 Implementation Plan

| Order | Slice | Owner | Done when |
| --- | --- | --- | --- |
| 1 | Target contract reset | specs/docs | The canonical spec and implementation lock say Matrix northbound is an OIDC-gated Weave facade backed by shared Rust/Ruma core, not a raw homeserver boundary. |
| 2 | Matrix Rust core seam | rust/server/client | `weave-matrix-core` compiles with JNI and Flutter bridge features, the Spring facade exposes `/_matrix/client/versions` and sync/send evidence through the canonical Chat facade, and Flutter has a Rust-core bridge descriptor without adding new provider reachability. |
| 3 | Matrix durable facade parity | server/rust | Room IDs, event IDs, timeline paging, membership, send idempotency, E2EE/device-state support evidence, and error mapping move from hand-shaped Java maps into Rust/Ruma-backed protocol helpers behind Spring authz/audit. |
| 4 | WebDAV full facade parity | server/client | Files supports the remaining WebDAV methods and properties (`MOVE`, `COPY`, `LOCK`, `UNLOCK`, streaming PUT, quota 507, lock discovery, ETags and preconditions) under `/dav/files` with Flutter using WebDAV for normal file data-plane operations. |
| 5 | CalDAV full facade parity | server/client | Calendar supports CalDAV discovery, `calendar-multiget`, `sync-collection`, `MKCALENDAR`, recurrence/DST evidence, ETags, and Flutter/native Calendar cutover under `/caldav`. |
| 6 | Protocol credential and native cutover | server/client/infra/e2e | Weave-issued scoped, expiring, revocable device credentials protect WebDAV/CalDAV native OS access, never expose provider credentials, and revoke tests deny follow-up protocol access. |
| 7 | Integrated E2E and claim gate | e2e/release | Smoke proves availability, E2E proves behavior across Matrix, WebDAV, and CalDAV, and release wording keeps incomplete E2EE/federation/native claims blocked until executable evidence exists. |

## Spring.ai.mcp Block

Spring.ai.mcp remains blocked. The hard rule from the archive still applies: do not work on Spring.ai.mcp until Matrix chat facading, WebDAV files facading, CalDAV calendar facading, Calls join grants/WebRTC boundary, native OS/client integration readiness, and Cucumber/Gherkin evidence are reflected in executable gates and implementation evidence.

## Current Evidence Boundary

Current branch evidence covers partial files WebDAV read/write, a CalDAV server MVP, a Matrix-shaped Spring projection, Flutter files WebDAV write paths, and Calls control endpoints. The Matrix projection is now the intended northbound shape only when Spring remains the OIDC/authz/audit gatekeeper and the reusable Matrix protocol rules move into the shared Rust/Ruma core. Spring projection is not final parity. That evidence is useful, but it must stay labelled as partial until the gap inventory above is closed with tests, client/server implementation, and CI/PR evidence.
