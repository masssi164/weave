# Domain facade protocol projections

Weave uses domain facades as product truth. Open protocols are exposed as
projections only when they improve native OS integration, interoperability, or
federation without leaking provider semantics into member, admin, or MCP
contracts.

This decision applies to Files, Calendar, and Chat. Calls/Meetings remain a
separate native-call and media-signaling boundary.

## Why standards and federation matter

Open standards are not a retreat from Weave product ownership. They reduce
lock-in and let organizations use generic, native, and federated clients without
handing those clients raw provider URLs or tokens. A member should be able to
open files through an operating-system file surface, see workspace events in a
native calendar, or join a call from a platform call UI while Weave still owns
domain truth, policy, audit, revocation, readiness, support-safe errors, and
provider mappings.

The northbound/southbound split is therefore mandatory:

- **Northbound:** Weave exposes product APIs and selected standard protocol
  projections to Flutter, Web, Admin Console, MCP, native OS integrations, and
  generic clients.
- **Southbound:** Weave calls configured domain providers through adapters such
  as WebDAV, CalDAV, Matrix, Graph, Google, Slack, Teams, LiveKit, or storage
  APIs. These adapters are interchangeable implementation choices, not member
  product contracts.

## Decision

Each domain has one Weave-owned core:

- canonical object references such as `file:`, `event:`, `conversation:`, and
  `message:`;
- Space/workspace/team/channel bindings;
- policy, capability, audit, and support-safe error semantics;
- provider mappings hidden behind adapters;
- generated OpenAPI member/admin contracts;
- governed MCP tools generated or validated from the same facade metadata.

OpenAPI is the primary JSON control contract for Weave clients, Admin Console,
and generated models, but it is not the only valid projection. For Files, the
durable northbound data plane is the Weave WebDAV facade. WebDAV,
CalDAV/iCalendar, and Matrix are allowed where they are the right open standard
for OS integration or federation. They must remain Weave projections or provider
adapters, never raw provider pass-throughs.

## OpenAPI role

OpenAPI remains critical. It is the generated contract for Flutter, Web, Admin
Console, setup/revoke screens, support-safe readiness cards, MCP route
allowlists, and generated models. Adding WebDAV, CalDAV/iCalendar, Matrix, or a
native OS boundary must not displace OpenAPI as control plane. For Files,
however, OpenAPI must not remain the long-term list/read/write data plane.
Standard protocol surfaces need a Weave-owned OpenAPI discovery, setup, status,
revoke, and audit path so clients can learn whether a projection is enabled and
how credentials or grants expire.

## Target server module shape

The server should move toward domain packages with the same internal outline:

| Layer | Responsibility |
| --- | --- |
| `server/.../<domain>/facade` | Northbound Weave product API, OpenAPI DTOs, and support-safe errors. |
| `server/.../<domain>/projection` | Optional WebDAV, CalDAV/iCalendar, Matrix-compatible, native OS, or MCP projection adapters over the facade. |
| `server/.../<domain>/adapter` | Southbound provider clients and anti-corruption mappers. |
| `server/.../<domain>/mapping` | Provider object mapping, lossy-field, permission-impact, conflict, portability, and audit evidence. |
| `server/.../<domain>/policy` | Capability gates, revocation, credential lifecycle, and audit decisions. |

Existing packages can migrate incrementally. Do not rename broad packages or
move all code in one PR; add the boundary where new contracts or tests need it.

## Client access discovery and credential lifecycle

Member clients discover access from the authenticated organization manifest and
domain setup endpoints. The manifest may expose Weave-owned paths such as
`/api/files`, `/api/calendar/native-sync-setup`, or
`/api/calls/native-boundary-setup`. It must not expose raw provider endpoints,
provider tenant URLs, app passwords, bearer tokens, static profile secrets,
endpoint rotation data, or admin diagnostics.

Credential and grant lifecycle is part of the Weave contract:

- Files native access requires revocable per-device Weave grants before member
  availability can be true.
- Calendar native/generic access requires scoped revocable CalDAV/iCalendar
  credentials, signed profile delivery where profiles are used, and revoke
  evidence.
- Chat access is session-bound and must not hand raw Matrix, Slack, or Teams
  credentials to member clients or MCP.
- Meetings/Calls use short-lived join grants and native call handoff state;
  media-provider credentials stay behind server/provider adapters.

The first implementation slice records this discovery in the organization
manifest as support-safe metadata only. It does not claim that native iOS or
Android integrations are ready.

## Files

Final shape:

- Product truth: Weave Files facade.
- JSON/API projection: `/api/files/**` for discovery, readiness, setup, revoke,
  credential lifecycle, and generated control-plane models only.
- Standard projection: Weave WebDAV-compatible surface at `/dav/files` as the
  durable northbound Files data plane for member/client/MCP file semantics.
- Native OS adapters: iOS File Provider extension and Android DocumentsProvider
  / Storage Access Framework, backed by the Weave Files facade or Weave WebDAV
  projection.
- Provider adapters: Nextcloud WebDAV first; S3/object storage or other storage
  adapters later.
- MCP: semantic tools such as `files.search`, `files.read_metadata`,
  `files.read_content`, `files.write`, and `files.share_item`, backed by the
  WebDAV-backed Weave Files facade/projection plus Weave policy and audit.

WebDAV is an open standard and is a strong fit for file/folder interoperability,
but raw WebDAV is not the member product contract. File Provider and
DocumentsProvider implementations expose native OS file surfaces while preserving
Weave IDs, policy, audit, and support-safe errors.

The first northbound WebDAV slice and its dependency, client adapter,
authentication, and write-gate decisions are recorded in [ADR-005: Files WebDAV
facade slice](adr-005-files-webdav-facade-slice.md). Write promotion is tracked
by #1007 and must cover ETag, conflict, lock, quota, revocation, and audit
policy before WebDAV write methods, Flutter mutations, or MCP write tools are
enabled.

Near-term federation for files should use Weave guest/external sharing policy.
Provider-native federated shares may become adapter capabilities later.

## Calendar

Final shape:

- Product truth: Weave Calendar facade for workspace, team, and channel events.
- JSON/API projection: `/api/calendar/**` and generated OpenAPI models.
- Standard projection: Weave CalDAV/iCalendar for native iOS/macOS Calendar and
  external calendar clients.
- Native OS adapters: iOS/macOS CalDAV configuration profile against Weave
  CalDAV; Android Account + SyncAdapter writing through Calendar Provider /
  `CalendarContract`, backed by the Weave Calendar facade or Weave CalDAV.
- Provider adapters: Nextcloud CalDAV first; Graph/Google/generic CalDAV-like
  adapters later.
- MCP: semantic tools such as `calendar.search_events`,
  `calendar.create_event`, `calendar.update_event`, and
  `calendar.link_meeting_thread`, backed by server policy and audit.

CalDAV/iCalendar are the right standards for native calendar interoperability,
especially on Apple platforms. Android has no universal CalDAV account install
path, so Weave needs an Android sync adapter if native Calendar integration is a
product requirement.

Private personal calendar ingestion is not the current product path. Keep the
scope on Weave-owned workspace/team/channel calendars and meeting threads.
External attendee/inter-organization federation can later use iTIP/iMIP or
provider-specific bridge adapters behind the Calendar facade.

## Chat

Final shape:

- Product truth: Weave Chat facade for conversations, messages, membership,
  attachments, decisions, meeting threads, policy, readiness, and audit.
- JSON/API projection: Weave Chat OpenAPI where server-visible metadata,
  readiness, attachment, decision-ledger, and governed write operations are safe.
- Backing protocol and federation projection: Matrix Client-Server and
  Server-Server APIs.
- Native clients: Weave mobile clients use a Matrix-capable transport layer where
  encrypted room participation requires it, but product UI exposes Weave
  conversations/channels rather than raw Matrix IDs.
- Provider adapters: Matrix first; Slack/Teams imports or bridges later as
  adapters, not product truth.
- MCP: semantic tools such as `chat.send_message`,
  `chat.search_accessible_messages`, `chat.summarize_thread`, and
  `chat.create_decision_ref`, with explicit consent/audit gates for shared-state
  writes and decrypted-content access.

Matrix is the strongest open and federated fit for Teams-like chat, but open
federation should be gated. Tenant isolation, identity mapping, moderation,
invite policy, retention, and external-room UX must be proven before broad
inter-organization federation is enabled.

Near-term Chat decision: **Weave Chat API first, Matrix-compatible transport and
federation later behind governance gates.** Matrix can be the first backing
adapter and future federation projection, but Matrix is not mandatory domain
truth and not the member product vocabulary. Slack and Teams remain southbound
adapter or bridge candidates.

MCP must not receive raw Matrix access by default. E2EE and room history make
chat the hardest domain for server-side tools; MCP should operate on governed
Weave projections, consented summaries, decision records, attachment references,
and user-authorized decrypted indexes.

## Meetings/Calls

Final shape:

- Product truth: Weave Meetings/Calls facade for meeting metadata, invites,
  participants, policy, join grants, recordings/captions/artifact references,
  and audit.
- JSON/API projection: `/api/calls/**` and generated OpenAPI models.
- Standard/native boundary: calendar invites, meeting links, iOS CallKit /
  PushKit boundaries, Android Telecom / ConnectionService boundaries, and
  provider-neutral join grants.
- Provider adapters: LiveKit first where configured; Teams/Meet/other meeting
  providers later through adapters.
- MCP: semantic tools such as `meetings.find`, `meetings.prepare_agenda`,
  `meetings.create_join_grant`, and `meetings.link_chat_thread`, backed by
  policy and audit.

Non-goal: WebDAV and CalDAV do not solve calls. Calendar may schedule a meeting
and Chat may host a meeting thread, but calls need explicit native call UI,
media, signaling, permissions, join-grant, and revoke boundaries.

## MCP projection rule

MCP tools are semantic Weave tools, not raw protocol scripts. Under the hood,
Files MCP data-plane tools route through the WebDAV-backed Weave Files
facade/projection. Other tools may call OpenAPI, WebDAV, CalDAV, Matrix, or
provider adapters under the server boundary, but:

- tool names and capability keys stay domain-semantic;
- server policy, authorization, validation, redaction, and audit remain the hard
  boundary;
- raw provider credentials, URLs, diagnostics, and unrestricted protocol methods
  are not exposed to MCP clients;
- destructive or shared-state writes require the same approval/audit receipts as
  product clients.

## Cleanup required

The following drift must be removed as this architecture lands:

- member-facing Nextcloud, CalDAV, WebDAV, or Matrix implementation details in
  normal Files/Calendar/Chat copy;
- DTO defaults or error messages that expose provider identifiers as product
  truth;
- private personal calendar setup language in release-facing paths while the
  current product scope is shared workspace/team/channel calendars;
- duplicate MCP contract logic that is not generated from or validated against
  the server facade metadata and OpenAPI route allowlist;
- native setup surfaces that expose provider endpoints instead of Weave
  projections.
