# Domain facade protocol projections

Weave uses domain facades as product truth. Open protocols are exposed as
projections only when they improve native OS integration, interoperability, or
federation without leaking provider semantics into member, admin, or MCP
contracts.

This decision applies to Files, Calendar, People/Contacts, and Chat.
Calls/Meetings remain a separate native-call and media-signaling boundary.

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
- application use cases shared by every northbound projection;
- provider-neutral southbound ports and adapter conformance profiles;
- generated OpenAPI admin/control contracts;
- governed MCP tools, resources, and prompts over the same use cases.

OpenAPI is the JSON control/admin/setup/revoke/manifest convenience contract for
Weave clients, Admin Console, and generated models. It is not product or
data-plane authority. For Files, the durable northbound data plane is the Weave
WebDAV facade. Calendar uses Weave CalDAV/iCalendar, People-domain contacts use
Weave CardDAV/vCard, Chat targets Weave Matrix Client-Server core first, and MCP
uses semantic Weave tools over domain use cases. These surfaces must remain
Weave projections or provider adapters, never raw provider pass-throughs.

## OpenAPI role

OpenAPI remains useful. It is the generated contract for Flutter, Web, Admin
Console, setup/revoke screens, support-safe readiness cards, manifests, and
generated models. It is not the authority for Files, Calendar, People/Contacts,
Chat, Matrix, MCP, provider-switch, or domain-kernel data-plane semantics once
the target projection exists. Standard protocol surfaces need Weave-owned
OpenAPI discovery, setup, status, revoke, and audit paths so clients can learn
whether a projection is enabled and how credentials or grants expire.

## Target server module shape

The server uses the same internal outline for collaboration domains:

| Layer | Responsibility |
| --- | --- |
| `server/.../<domain>/domain` | Immutable canonical values, stable identifiers, and domain invariants. No protocol, provider, DTO, SDK, Spring, or persistence types. |
| `server/.../<domain>/application` | Authorized use cases, policy, preconditions, audit intent, and support-safe domain failures. |
| `server/.../<domain>/port` | Provider-neutral southbound contracts expressed only in canonical values. |
| `server/.../<domain>/projection` | WebDAV, CalDAV/iCalendar, CardDAV/vCard, Matrix Client-Server, native OS, MCP, and control-plane projections over use cases. |
| `server/.../<domain>/adapter` | Southbound provider clients and anti-corruption mappers implementing ports. |
| `server/.../<domain>/mapping` | Provider object mapping, lossy-field, permission-impact, conflict, portability, and audit evidence. |
| `server/.../<domain>/policy` | Capability gates, revocation, credential lifecycle, and audit decisions. |

The Files and Calendar facades and their Nextcloud WebDAV/CalDAV adapters use
the canonical `FilesProviderPort` and `CalendarProviderPort`. Their former
DTO-shaped compatibility ports have been removed. Chat follows the same port
shape and keeps Matrix, Slack, Teams, and future providers behind canonical
conversation and message values.

## Client access discovery and credential lifecycle

Member clients discover access from the authenticated organization manifest and
domain setup endpoints. The manifest may expose Weave-owned protocol paths such
as `/dav/files`, `/caldav`, `/_matrix/client`, or control paths such as
`/api/calendar/native-sync-setup`. It must not expose raw provider endpoints,
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
- Meetings/Calls use MatrixRTC signaling, independent RTC authorization, short-lived SFU tokens, and native call handoff state;
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

The first northbound WebDAV slices and their dependency, client adapter,
authentication, and write decisions are recorded in [ADR-005: Files WebDAV
facade slice](adr-005-files-webdav-facade-slice.md). `PUT`, `MKCOL`, and
`DELETE` use Weave ETags, conditional preconditions, support-safe errors, and
mutation audit. `MOVE`, `COPY`, `LOCK`, and `UNLOCK` are now part of the same
Weave-owned WebDAV member data-plane proof. Native OS provider integrations and
public MCP write tools remain separate cutover slices.

Near-term federation for files should use Weave guest/external sharing policy.
Provider-native federated shares may become adapter capabilities later.

## Calendar

Final shape:

- Product truth: Weave Calendar facade for workspace, team, and channel events.
- JSON/API projection: `/api/calendar/**` setup, status, revoke, manifest, and
  generated control models.
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

## People/Contacts

Final shape:

- Product truth: the canonical `people` domain owns address books, contacts,
  contact methods, directory links, visibility, policy, readiness, revoke state,
  audit, and support-safe errors.
- JSON/API projection: setup, status, revoke, manifest, support-safe recovery,
  and generated control models only.
- Standard projection: Weave CardDAV/vCard at `/dav/addressbooks` for native
  address-book sync and external CardDAV clients.
- Native OS adapters: iOS Contacts/CardDAV account setup and Android Contacts
  Provider sync through Weave-owned setup/status/revoke boundaries.
- Provider adapters: IDM/directory imports, external contact providers, or
  generic CardDAV sources behind ports/adapters.
- MCP: semantic People-domain tools for contact search, contact read, and
  proposed updates, backed by policy, redaction, and audit.

Contacts and address books are not a subfeature of Calendar. The first slice
should extend the canonical People-domain Contact/AddressBook model, vCard
mapping, CardDAV discovery/read skeleton, and fail-closed writes until conflict,
revoke, audit, recovery, and support-safe error behavior exists.

## Chat

Final shape:

- Product truth: Weave Chat facade for conversations, messages, membership,
  attachments, decisions, meeting threads, policy, readiness, and audit.
- JSON/API projection: Weave Chat OpenAPI where server-visible metadata,
  readiness, attachment, decision-ledger, and governed write operations are safe.
- Standard projection: OIDC-gated Weave Matrix Client-Server facade backed by
  the shared Rust/Ruma Matrix core. Federation identity is later and gated.
- Shared protocol core: server consumes the Rust core through JNI; Flutter
  consumes the same core through `flutter_rust_bridge`. Ruma, serde,
  serde_json, thiserror, and tracing own reusable Matrix protocol modeling. The
  Flutter feature additionally uses the Apache-2.0 Matrix Rust SDK for Matrix
  E2EE state machines and its encrypted SQLite store; the retired Dart Matrix
  SDK is not a compatibility path.
- Native clients: Weave mobile clients use a Matrix-capable transport layer where
  encrypted room participation requires it, but product UI exposes Weave
  conversations/channels rather than raw Matrix IDs.
- Provider adapters: Matrix first; Slack/Teams imports or bridges later as
  adapters, not product truth.
- MCP: semantic tools such as `chat.send_message`,
  `chat.search_accessible_messages`, `chat.summarize_thread`, and
  `chat.create_decision_ref`, with explicit consent/audit gates for shared-state
  writes and decrypted-content access.

Matrix is the target northbound chat protocol, but it must be exposed through
Weave's own facade, not by promoting Synapse or another homeserver to the
product boundary. The facade is served from the public Weave API origin;
`matrix.<tenant>` is reserved for the selected southbound provider and operator
checks. Platform configuration, member handoff, Flutter, and Weaver must reject
that provider origin as a member endpoint. The staged implementation starts with Matrix Client-Server
core before federation. Weave must own `server_name`, signing keys, Matrix user
IDs, room IDs, event IDs, membership, timeline persistence, and the canonical
chat ledger before any federation claim.
Tenant isolation, identity mapping, moderation, invite policy, retention, E2EE,
and external-room UX must be proven before broad inter-organization federation
is enabled.

The E2EE boundary is deliberately asymmetric. Spring/JNI validates OIDC and
capabilities, projects Matrix endpoints, and persists only public device and
cross-signing keys, opaque to-device traffic, encrypted room-key backups, and
`m.room.encrypted` event envelopes. Flutter's Rust client owns private keys,
Olm/Megolm state, decryption, encrypted local persistence, SAS verification,
cross-signing, and recovery secrets. A stable Weave device ID and a
Keychain-held per-profile store passphrase reopen the same encrypted store after
force-quit, relaunch, token refresh, and an in-place TestFlight update. Sign-out
closes the in-process client without deleting crypto state; explicit account
removal is the only product action that deletes the passphrase, store, and
device ID. The facade persists a support-safe hash of the Keycloak `sid` or
`session_state` binding to that device. Token refresh may reopen the same
device, but the same OIDC session cannot claim a different device ID to bypass
revocation. Separate physical devices use separate OIDC login sessions.

Encrypted rooms fail closed. The canonical Chat adapter accepts opaque
encrypted envelopes and immutable `m.room.encryption` state, while the Flutter
repository only projects SDK-decrypted events and never falls back to a
plaintext timeline or send route. Lost-device revocation denies further Matrix
operations for that device. Provider replacement must classify encrypted
history as lossy, `unsupported`, or `archive_only` unless opaque events and the
client-side key strategy are both proven.

Member Chat API-first message surfaces are obsolete and removed. REST remains
only for control/admin/setup convenience and fixture-fenced evidence where a
focused contract permits it. Slack and Teams remain southbound adapter or
bridge candidates.

MCP must not receive raw Matrix access by default. E2EE and room history make
chat the hardest domain for server-side tools; MCP should operate on governed
Weave projections, consented summaries, decision records, attachment references,
and user-authorized decrypted indexes.

## Meetings/Calls

Final shape:

- Product truth: Weave owns meeting identity, authorized participants/roles,
  policy, consent, artifact references, retention, audit, and provider fidelity.
- Member signaling: Matrix v1.19 plus pinned MatrixRTC Profile 0. There is no
  Calls member REST/OpenAPI projection, proprietary event, or join-grant model.
- Authorization/media boundary: an internal RTC Authorizer treats Matrix OpenID
  as identity input only, independently validates current room, slot, member,
  device, role, policy, nonce, audience, and expiry, then may issue a short-lived
  least-privilege SFU token.
- Standard/native boundary: calendar invites, meeting links, iOS CallKit /
  PushKit, Android Core-Telecom, MatrixRTC signaling, WebRTC media, and
  MatrixRTC media E2EE for private calls.
- Provider adapters: LiveKit first where configured; Teams/Meet/other meeting
  providers later through adapters.
- MCP: the Calls catalog stays empty until current RTC authorization and accepted
  approval/action-evidence gates can be enforced without exposing provider APIs.

Non-goal: WebDAV and CalDAV do not solve calls. Calendar may schedule a meeting
and Chat may host a meeting thread, but calls need explicit native call UI,
media, signaling, permissions, RTC authorization, E2EE key, and revoke boundaries.

## MCP projection rule

MCP tools are semantic Weave tools, not raw protocol scripts. The target runtime
is the Spring AI 2.x stateful Streamable HTTP server. Its annotated tools,
resources, and prompts call domain use cases and never scrape or mirror OpenAPI.
Files MCP data-plane tools route through the
WebDAV-backed Weave Files facade/projection. Calendar, People/Contacts, Chat,
and other tools route through their Weave domain use cases and standard
projections under the server boundary, but:

- tool names and capability keys stay domain-semantic;
- server policy, authorization, validation, redaction, and audit remain the hard
  boundary;
- raw provider credentials, URLs, diagnostics, and unrestricted protocol methods
  are not exposed to MCP clients;
- destructive or shared-state writes require the same approval/audit receipts as
  product clients.

The Spring AI cutover is complete for Files, Calendar, and Chat. The
Python/FastMCP OpenAPI route-map gateway and handwritten Java JSON-RPC
controller are removed. There is no compatibility endpoint or second MCP
catalog. Spring AI publishes the fixed canonical catalog ceiling, while the
backend-owned RuntimeProfile filters discovery and revalidates every
invocation. Approval-required calls use standard form elicitation through the
OpenClaw plugin approval manager, then carry an argument-bound one-use receipt
`_meta`; a receipt reference alone never authorizes a write. The backend binds
that receipt to the current RuntimeProfile hash, canonical domain and scopes,
exact tool, contract/policy versions, decision, approval time, expiry, and
audit reference before any domain use case runs.

## Cleanup required

The following drift must be removed as this architecture lands:

- member-facing Nextcloud, CalDAV, WebDAV, or Matrix implementation details in
  normal Files/Calendar/Chat copy;
- DTO defaults or error messages that expose provider identifiers as product
  truth;
- private personal calendar setup language in release-facing paths while the
  current product scope is shared workspace/team/channel calendars;
- duplicate MCP contract logic that is not generated from or validated against
  the server facade metadata, domain MCP projection, and control-plane
  allowlist;
- native setup surfaces that expose provider endpoints instead of Weave
  projections.
