# Domain facade protocol projections

Weave uses domain facades as product truth. Open protocols are exposed as
projections only when they improve native OS integration, interoperability, or
federation without leaking provider semantics into member, admin, or MCP
contracts.

This decision applies to Files, Calendar, and Chat. Calls/Meetings remain a
separate native-call and media-signaling boundary.

## Decision

Each domain has one Weave-owned core:

- canonical object references such as `file:`, `event:`, `conversation:`, and
  `message:`;
- Space/workspace/team/channel bindings;
- policy, capability, audit, and support-safe error semantics;
- provider mappings hidden behind adapters;
- generated OpenAPI member/admin contracts;
- governed MCP tools generated or validated from the same facade metadata.

OpenAPI is the primary JSON contract for Weave clients, Admin Console, and the
MCP adapter, but it is not the only valid projection. WebDAV, CalDAV/iCalendar,
and Matrix are allowed where they are the right open standard for OS integration
or federation. They must remain Weave projections or provider adapters, never
the product truth.

## Files

Final shape:

- Product truth: Weave Files facade.
- JSON/API projection: `/api/files/**` and generated OpenAPI models.
- Standard projection: optional Weave WebDAV-compatible surface for desktop or
  admin-approved clients.
- Native OS adapters: iOS File Provider extension and Android DocumentsProvider
  / Storage Access Framework, backed by the Weave Files facade or Weave WebDAV
  projection.
- Provider adapters: Nextcloud WebDAV first; S3/object storage or other storage
  adapters later.
- MCP: semantic tools such as `files.search`, `files.read_metadata`,
  `files.read_content`, `files.write`, and `files.share_item`, backed by Weave
  policy and audit.

WebDAV is an open standard and is a strong fit for file/folder interoperability,
but raw WebDAV is not the member product contract. File Provider and
DocumentsProvider implementations expose native OS file surfaces while preserving
Weave IDs, policy, audit, and support-safe errors.

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

MCP must not receive raw Matrix access by default. E2EE and room history make
chat the hardest domain for server-side tools; MCP should operate on governed
Weave projections, consented summaries, decision records, attachment references,
and user-authorized decrypted indexes.

## MCP projection rule

MCP tools are semantic Weave tools, not raw protocol scripts. Under the hood,
they may call OpenAPI, WebDAV, CalDAV, Matrix, or provider adapters, but:

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
