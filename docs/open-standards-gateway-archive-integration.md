# Open Standards Gateway Archive Integration

Status: PR #1043 facade cutover and #1046 API-origin stabilization implemented; credentialed dogfood evidence and archive parity target still tracked; not complete.

Evidence marker: `OPEN_STANDARDS_GATEWAY_ARCHIVE_FULL_TARGET_TRACKED`

This page records the July 2026 open-standards gateway archive as an implementation/evidence contract. PR #1043 moves normal member data planes to Weave-owned WebDAV, CalDAV, and Matrix-compatible northbound facades. The accepted facade profiles are implemented and provider-neutral; the archive remains not complete because physical native-client evidence, Calls media parity, advanced protocol extensions, and Matrix E2EE/device lifecycle still need executable proof.

## Current Gap Inventory

| Gap id | Domain | Required before complete claim |
| --- | --- | --- |
| `files-full-webdav` | Files/WebDAV | The accepted WebDAV facade profile implements `PROPFIND`, `GET`, `HEAD`, `PUT`, `MKCOL`, `DELETE`, `COPY`, `MOVE`, `LOCK`, and `UNLOCK` through `FilesProviderPort`, including ETags, preconditions, lock conflicts, quota 507 mapping, canonical IDs, and conformance accounting. Follow-up remains for streaming PUT of large objects, dead-property extensions, physical OS integration evidence, and provider-atomic or Weave-ledger precondition proof. |
| `calendar-caldav-parity` | Calendar/CalDAV | The accepted CalDAV facade profile implements discovery, collection listing, `calendar-query`, `calendar-multiget`, `sync-collection`, free/busy, GET/PUT/DELETE, ETags, stable UIDs, recurrence, and DST behavior through `CalendarProviderPort`; Flutter CalDAV repositories consume this facade. Follow-up remains for `MKCALENDAR`, physical OS sync evidence, and broader scheduling extensions. |
| `chat-matrix-parity` | Chat/Matrix | The OIDC-gated Weave Matrix Client-Server facade implements versions, whoami, sync, joined rooms, messages, and idempotent send through `ChatProviderPort`. Matrix parsing/projection is owned by the shared Rust/Ruma Matrix core loaded through required server JNI on Spring Boot and generated `flutter_rust_bridge` bindings in Flutter; the in-memory southbound provider/fixture proves the replaceable port. The facade is fixed to the public API origin, raw Matrix provider URLs are rejected by member handoff, and stale local profile state is normalized without clearing the OIDC session. The raw Chat API-first member data-plane is removed. Follow-up remains for broader Matrix Client-Server parity, encrypted-room verification and device revocation/recovery, governed provisioning, and live southbound replacement evidence. |
| `transitional-rest-cleanup` | Chat/Calendar/OpenAPI | Completed: PR #1043 removed REST member data-plane routes, and the #1023 cleanup removes the deprecated PA message bridge, configuration, models, and tests. Weaver now enters only through stock Matrix plus Spring AI MCP. |
| `calls-matrixrtc-profile-0` | Calls/MatrixRTC/WebRTC | The proprietary Calls API is removed. Complete Matrix Native OAuth, exact MatrixRTC Profile 0 wire support, RTC Authorizer abuse/replay tests, MatrixRTC media E2EE, LiveKit transport, TURN/reconnect, physical-device native coordination, consent, accessibility, and support-safe audit before any Ready claim. |
| `protocol-credentials` | Identity/OIDC and native protocols | Implemented: OIDC-authenticated members mint one-time Weave credentials scoped to `WEBDAV_FILES` or `CALDAV_CALENDAR`; hashes are durable, expiring, revocable, audited, and never provider credentials. Physical OS account proof remains a release-evidence task. |
| `admin-openapi-control-plane` | Admin/OpenAPI | Keep OpenAPI to manifest, setup, readiness, revoke, device credentials, admin/provider configuration, and generated convenience models. Calls signaling and membership remain Matrix/MatrixRTC rather than an OpenAPI member surface. |
| `native-client-boundary` | Flutter/native OS | Return Weave WebDAV/CalDAV endpoints and Weave credentials only, keep responses screen-reader friendly, and prove revoke disables OS integration access. |
| `mcp-domain-boundary` | MCP/Agents | Spring AI 2.0 stateful Streamable HTTP is implemented for governed Files, Calendar, and Chat tools, approved-tool resources, bounded prompts, and standard form elicitation. Calls MCP remains blocked on the Calls profile; all outputs exclude raw provider URLs, credentials, tokens, and downstream payloads. |

## PR 1043 Implementation Plan

| Order | Slice | Owner | Done when |
| --- | --- | --- | --- |
| 1 | Target contract reset | specs/docs | The canonical spec and implementation lock say Matrix northbound is an OIDC-gated Weave facade backed by shared Rust/Ruma core, not a raw homeserver boundary. |
| 2 | Matrix Rust core seam | rust/server/client | Implemented: `weave-matrix-core` owns Matrix JSON through Ruma/Serde, Spring requires its JNI library, Flutter builds and loads generated `flutter_rust_bridge` bindings, and neither side has a handwritten protocol fallback. |
| 3 | Matrix member data-plane cutover | server/rust/client | Implemented for PR #1043: Spring remains the OIDC/authz/audit gatekeeper, `/_matrix/client/**` exposes whoami/sync/joined rooms/member events/messages/send, Flutter uses the Matrix facade repository and Rust bridge descriptor, and the obsolete REST chat repository is removed. |
| 4 | WebDAV member data-plane cutover | server/client | Implemented for PR #1043: Files supports WebDAV read/list/download/write/folder/delete/copy/move/lock/unlock under `/dav/files` and Flutter uses WebDAV for normal file data-plane operations. |
| 5 | CalDAV member data-plane cutover | server/client | Implemented for PR #1043: Calendar supports CalDAV discovery, `calendar-query`, `calendar-multiget`, `sync-collection`, free/busy, GET/PUT/DELETE, ETags, and Flutter CalDAV/iCalendar event access under `/caldav`. |
| 6 | Protocol credential and native cutover | server/client/infra/e2e | Implemented at the protocol/control boundary: Weave-issued scoped, expiring, revocable credentials protect WebDAV/CalDAV, never expose provider credentials, and revoke tests deny follow-up protocol access. Physical OS account evidence remains separate. |
| 7 | Integrated E2E and claim gate | e2e/release | Offline protocol and mapping evidence is implemented. The credentialed dogfood lane must still prove OIDC access, revocation, DAV device credentials, and API-origin Matrix behavior before #1046 or the archive target can be closed. Release wording keeps incomplete E2EE/federation/native claims blocked until executable evidence exists. |

## Spring AI MCP Implementation

Spring AI MCP is implemented as the only runtime MCP path. `weave-mcp-server` uses Spring Boot 4.1, Spring AI 2.0, and the official stateful Streamable HTTP WebMVC transport at `/mcp`, enabling standard form elicitation for write approvals. Spring Security validates OIDC issuer, audience, token lifetime, and `weave:workspace` scope before protocol handling. RuntimeProfile discovery and every invocation are rechecked by `weave-backend`; Files, Calendar, and Chat dispatch through their canonical services and provider ports. The Python/FastMCP runtime and handwritten Java JSON-RPC controller are removed. Calls tools stay absent until the Calls canonical profile satisfies the same gate.

## Current Evidence Boundary

Current branch evidence covers the PR #1043 normal member data-plane cutover and #1046 offline stabilization: Files through `/dav/files`, Calendar through `/caldav`, Chat through `/_matrix/client/**` on the public API origin, and governed Weaver access through Spring AI `/mcp`. Spring remains the OIDC/authz/audit gatekeeper, canonical ports own product semantics, and providers stay replaceable southbound. Credentialed dogfood execution remains required before the live scenarios are satisfied. This is not a claim of every WebDAV/CalDAV/Matrix extension, Matrix E2EE completion, physical native OS account availability, Calls media parity, or full archive closure; those remain bounded by the gap inventory above.
