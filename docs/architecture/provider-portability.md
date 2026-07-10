# Provider portability contract

Provider portability means Weave can replace or add provider adapters without changing the member-facing domain contract. It does not promise magical lossless migration. The release principle is **no unaccounted data loss**: every unsupported field, permission, object, or semantic difference must be reported, classified, and approved before an apply path is allowed.

## Provider Adapter Manifest

Every provider adapter must publish a support-safe manifest before it can back a product domain.

| Field | Requirement |
| --- | --- |
| `adapterKey` | Stable adapter identifier, e.g. `keycloak-realm`, `openproject-primary`, `livekit`, or `nextcloud-files`. |
| `domain` | One canonical domain, or an explicit list when the adapter spans domains. |
| `apiProfile` | Standards/API profile used by the adapter, such as OIDC, SAML, SCIM, CalDAV, WebDAV, Matrix Client-Server, OpenProject REST, LiveKit token API, or provider Graph API. |
| `canonicalObjects` | Canonical object kinds the adapter can read, write, or link. |
| `capabilityKeys` | Stable Weave capability keys, split into read, write, admin, and runtime/tool grants. |
| `readinessChecks` | Backend-owned checks that prove credentials, connectivity, scopes, schema, policy, and audit safety. |
| `unsupportedFields` | Provider features or fields not represented in the canonical contract. |
| `migrationLimits` | Known lossy areas, rate limits, export/import gaps, and conflict rules. |
| `auditEvents` | Support-safe audit event names emitted for dry-run, apply, rollback, policy decision, and diagnostics. |
| `secretBoundary` | Statement that credentials, raw provider payloads, and endpoints are never exposed to normal members. |

## Provider Mapping Table

Each domain-backed category must keep a mapping from provider object kinds to canonical object kinds.

| Mapping column | Description |
| --- | --- |
| Source provider object | Provider-native kind and immutable identifier strategy. |
| Canonical object | Weave domain object and stable reference. |
| Source of truth | Provider-owned, Weave-owned, or shared with explicit precedence. |
| Read support | Supported fields, pagination, filters, and consistency guarantees. |
| Write support | Supported mutations, idempotency, optimistic locking, and rollback/compensation. |
| Lossy fields | Unsupported fields or semantics that must appear in a lossy report. |
| Conflict rule | How duplicate, stale, deleted, renamed, or concurrently changed objects are handled. |
| Audit reference | Event names and evidence references for dry-run/apply/rollback. |

## Required reports

Provider changes and migrations must produce machine-readable and reviewer-readable evidence.

- **Export report**: source objects discovered, immutable IDs, counts, redacted samples, permissions, attachments/binaries, versions, references, rate-limit notes, and unsupported provider features.
- **Import report**: target feasibility, object mapping, generated target IDs, skipped objects, idempotency keys, and post-import validation.
- **Lossy report**: every unsupported field, permission, workflow transition, recurrence rule, comment, attachment, version, lock, or provider-native feature that cannot be preserved as-is.
- **Conflict report**: duplicate identities, existing target objects, renamed/deleted source objects, concurrent updates, last-admin guards, membership mismatches, and required admin choices.
- **Rollback/retention report**: rollback feasibility, retained provider data, retention/legal-hold boundary, and manual remediation steps.

A provider switch may be shown to members only as stable capability state and impact copy. Admins/operators see the reports, readiness cards, and next actions in the Admin Console or operator evidence bundle.

## Sprint 11 Nextcloud Files and Calendar portability evidence

The first release-quality Nextcloud path uses backend actor adapters. Member-facing clients consume Weave Files and Calendar states only; provider URLs, app passwords, actor usernames, raw WebDAV/CalDAV bodies, and raw provider paths stay out of member errors.

| Domain | Export/import portability | Lossy or blocked fields | Deletion and conflict safety | Readiness evidence |
| --- | --- | --- | --- | --- |
| Files via Nextcloud WebDAV | Portable objects are folders, files, MIME type, byte size, modified timestamp, quota used/total, and opaque Weave file IDs derived from normalized product paths. Import may recreate folders and upload file bytes through the backend actor. | Provider-native shares, versions, locks, comments, tags, external links, and retention/legal-hold metadata require a lossy report before adapter replacement. | Path traversal and invalid identifiers are rejected before a WebDAV request. Permission, lock, quota, not-found, auth, and unavailable responses map to stable product errors; delete conflicts are surfaced as refresh-and-retry conflicts rather than raw provider failures. | Admin readiness must prove backend actor credentials, WebDAV route, quota/status access, and write/delete scope without returning the actor username, app password, base URL, or raw response body to members. |
| Calendar via Nextcloud CalDAV | Portable objects are scoped workspace/team/channel events with title, description, start/end, timezone, location, all-day state, attendees, ETag, updated timestamp, and opaque Weave event IDs. Import may PUT iCalendar VEVENT resources through backend actor collections. | Recurrence (`RRULE`, `RDATE`, `EXDATE`) is explicitly blocked until the Weave recurrence contract preserves intent. Provider alarms, attachments, organizer delegation, resources, and free/busy semantics require a lossy report. | Event IDs are scoped opaque IDs; reading through the wrong scope is rejected. Downstream auth, not-found, conflict, invalid response, and unavailable states map to stable calendar errors with provider paths redacted. | Admin readiness must prove CalDAV collection access, backend actor auth, scoped collection creation/read/write/delete, and credential setup safety without exposing passwords, tokens, user IDs, base URLs, or raw provider errors to members. |

## Native OS integration boundary

Native OS integrations sit above the provider adapter layer. The governing
decision is [Domain facade protocol projections](domain-facade-protocol-projections.md):
Weave domain facades are product truth, while WebDAV, CalDAV/iCalendar, Matrix,
OpenAPI, native OS extensions/providers, and MCP tools are projections or
adapters over that truth.

Native integrations use OS contracts but receive only Weave-owned
setup/status/provisioning metadata:

| Domain | Native OS boundary | Weave facade contract | Availability gate |
| --- | --- | --- | --- |
| Files | iOS File Provider extension; Android DocumentsProvider / Storage Access Framework. | `GET /api/files/native-provider-setup` for setup/readiness plus `/dav/files` WebDAV facade roots/list/open hooks. The response contains Weave paths only; `PUT`, `MKCOL`, and `DELETE` use Weave ETags, conditional preconditions, support-safe errors, and mutation audit. | Full native availability is blocked until iOS/Android provider implementations list/open/write at least one Weave file through the facade and revocation is proven on device. |
| Calendar | iOS CalDAV configuration profile semantics; Android Account/SyncAdapter plus Calendar Provider / CalendarContract. | `GET /api/calendar/native-sync-setup` plus setup credential lifecycle and event facade hooks. The response contains Weave API paths only and no raw calendar-provider host. | Full native availability is blocked until signed profile/account setup, event sync, and revoke/fail-closed behavior are proven on physical or instrumentation devices. |
| Calls/Meetings | iOS CallKit + PushKit/VoIP concerns; Android Telecom / ConnectionService where supported. | `GET /api/calls/native-boundary-setup` describes Weave meeting invitation, policy, and join-grant boundaries. Actual media transport remains separate. | Full native availability is blocked until provider-neutral meetings facade endpoints, native call UI, permissions, audio routing, and revoke/join evidence are proven on devices. |

OpenAPI remains the primary setup/status/provisioning surface for Weave clients,
Admin Console, and MCP route allowlists. It does not replace native OS provider,
account, sync, call UI, or protocol projections where standards are the better
fit. Files may expose a Weave WebDAV-compatible projection; Calendar may expose
a Weave CalDAV/iCalendar projection; Chat may use Matrix for transport and
federation. These projections must use Weave facades and never become provider
pass-throughs.

## Keycloak desired-state dry-run direction

Sprint 8 identity work uses Keycloak as the concrete desired-state dry-run profile. The dry-run must compare a desired realm/client/role/group mapping with the current support-safe snapshot and report planned create/update/delete/no-op actions without mutating a live realm.

Minimum dry-run evidence:

- realm/client presence and redirect/origin policy status;
- role/group mapping to Weave capability profiles;
- last-admin and lockout protection;
- immutable subject strategy and email-rename handling;
- raw secret and provider-payload redaction;
- audit event names for dry-run and any future guarded apply.

Apply remains out of scope until readiness, approval, rollback, and audit gates exist.

## Sprint 12 provider portability schema v2

Sprint 12 upgrades the portability vocabulary from coarse loss classes to field-level, machine-readable classes used by dry-run evidence, admin review, and release claim checks. The canonical classes are:

- `portable`: the field maps to a Weave canonical object without extra admin action.
- `lossy`: the field can be represented only with explicit loss in `LossyMappingReport`.
- `unsupported`: the target adapter cannot represent the field and apply remains blocked unless policy permits omission.
- `manual_review`: admin review is required before apply, usually because identity, permission, conflict, or member-impact context is incomplete.
- `vendor_locked`: the field is provider-owned and cannot be exported or replayed as a Weave canonical value.
- `archive_only`: the field is preserved in a support-safe archive but is not imported into the target provider.

The v2 contract is intentionally evidence-first: Weave promises **no unaccounted data loss**, not lossless migration. Release claims must not market “lossless migration”; every unsupported, lossy, vendor-locked, or archive-only field must be counted in support-safe evidence before apply can proceed.

### Machine-readable v2 reports

The canonical schemas live under `server/src/main/resources/contracts/portability/` and are checked by `./gradlew portabilityContractCheck`:

- `ProviderAdapterManifest` declares adapter capabilities, readiness checks, unsupported fields, limits, audit events, and secret boundaries.
- `ProviderMapping` maps source provider objects to Weave canonical objects and target adapter objects with v2 field classes.
- `ExportManifest` records object counts, content hashes, mapping references, and audit references.
- `ImportManifest` records target import feasibility and links to dry-run evidence.
- `ImportFeasibilityReport` classifies whether apply is feasible, feasible with manual review, or blocked.
- `LossyMappingReport` counts lossy fields and requires approval.
- `ConflictReport` lists conflicts that must be resolved before apply.
- `PermissionImpactReport` explains ownership, share, role, and visibility consequences without raw provider identifiers.
- `ArchiveManifest` lists support-safe archive references and content hashes.
- `RollbackRetentionReport` records rollback archive retention and restore-smoke requirements.
- `MigrationRun` and `MigrationAuditRef` bind the reports into the server-side apply gate.

### Domain dry-run fixtures

Redacted Sprint 12 fixtures in `specs/0006-portability-contract/` cover Files, Calendar, Boards, and Chat. Negative fixtures reject silent drops and raw provider leaks. Admin Console and release evidence may render only stable product states such as `available`, `not_configured`, `guarded`, `manual_review_required`, `blocked`, and `unavailable`; member impact previews must never expose raw provider identifiers, URLs, tokens, payloads, or downstream error bodies.
