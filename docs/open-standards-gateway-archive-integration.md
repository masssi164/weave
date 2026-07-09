# Open Standards Gateway Archive Integration

Status: PR #1043 facade cutover implemented; archive parity target still tracked; not complete.

Evidence marker: `OPEN_STANDARDS_GATEWAY_ARCHIVE_FULL_TARGET_TRACKED`

This page records the July 2026 open-standards gateway archive as an implementation/evidence contract. PR #1043 moves the normal member data planes to WebDAV, CalDAV, and Matrix-compatible northbound facades. The archive target remains larger than the PR #1043 slice: native integration, device credentials, deeper protocol parity, Calls media, MCP/tool boundaries, and Matrix E2EE/device lifecycle still need executable evidence before the umbrella can close.

## Current Gap Inventory

| Gap id | Domain | Required before complete claim |
| --- | --- | --- |
| `files-full-webdav` | Files/WebDAV | PR #1043 implements normal WebDAV member data-plane operations including read/list/download/write/folder/delete and WebDAV `MOVE`, `COPY`, `LOCK`, and `UNLOCK`. Follow-up remains for streaming PUT, quota 507 edge evidence, advanced WebDAV property coverage, physical OS integration, and provider-atomic or Weave-ledger precondition evidence. |
| `calendar-caldav-parity` | Calendar/CalDAV | PR #1043 implements CalDAV discovery/list/read/write/delete plus `calendar-query`, `calendar-multiget`, `sync-collection`, and free/busy. Follow-up remains for `MKCALENDAR`, recurrence DST expansion, Flutter CalDAV native/device proof, physical OS sync evidence, and credential-backed native account setup. |
| `chat-matrix-parity` | Chat/Matrix | PR #1043 completes the OIDC-gated Weave Matrix Client-Server facade as the member data plane for whoami/sync/joined rooms/messages/send, backed by the shared Rust/Ruma Matrix core boundary descriptors. Follow-up remains for server JNI depth, flutter_rust_bridge production binding, broader Matrix Client-Server parity, durable paging/idempotency, governed provisioning, device revocation, support-safe E2EE/device state, southbound provider/fixture interoperability evidence, and removal of any remaining raw Chat API-first member data-plane assumptions. |
| `transitional-rest-cleanup` | Chat/Calendar/OpenAPI | Completed for PR #1043: REST member data-plane compatibility routes for Calendar events and Chat conversations/messages are removed from OpenAPI and runtime routing. Keep #1044 as the follow-up cleanup bucket for deleting obsolete docs, generated remnants, and any downstream references found after merge. |
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
| 3 | Matrix member data-plane cutover | server/rust/client | Implemented for PR #1043: Spring remains the OIDC/authz/audit gatekeeper, `/_matrix/client/**` exposes whoami/sync/joined rooms/messages/send, Flutter uses the Matrix facade repository and Rust bridge descriptor, and the obsolete REST chat repository is removed. |
| 4 | WebDAV member data-plane cutover | server/client | Implemented for PR #1043: Files supports WebDAV read/list/download/write/folder/delete/copy/move/lock/unlock under `/dav/files` and Flutter uses WebDAV for normal file data-plane operations. |
| 5 | CalDAV member data-plane cutover | server/client | Implemented for PR #1043: Calendar supports CalDAV discovery, `calendar-query`, `calendar-multiget`, `sync-collection`, free/busy, GET/PUT/DELETE, ETags, and Flutter CalDAV/iCalendar event access under `/caldav`. |
| 6 | Protocol credential and native cutover | server/client/infra/e2e | Weave-issued scoped, expiring, revocable device credentials protect WebDAV/CalDAV native OS access, never expose provider credentials, and revoke tests deny follow-up protocol access. |
| 7 | Integrated E2E and claim gate | e2e/release | Smoke proves availability, E2E proves behavior across Matrix, WebDAV, and CalDAV, and release wording keeps incomplete E2EE/federation/native claims blocked until executable evidence exists. |

## Spring.ai.mcp Block

Spring.ai.mcp remains blocked. The hard rule from the archive still applies: do not work on Spring.ai.mcp until Matrix chat facading, WebDAV files facading, CalDAV calendar facading, Calls join grants/WebRTC boundary, native OS/client integration readiness, and Cucumber/Gherkin evidence are reflected in executable gates and implementation evidence.

## Current Evidence Boundary

Current branch evidence covers the PR #1043 normal member data-plane cutover: Files through `/dav/files`, Calendar through `/caldav`, and Chat through `/_matrix/client/**` with Spring Boot as the OIDC/authz/audit gatekeeper and Rust/Ruma as the shared protocol-core target. This is not a claim of complete Matrix Client-Server parity, Matrix E2EE completion, native OS account availability, or full archive closure; those remain blocked by the gap inventory above.
