# Space anchor contract

`spaces` is the cross-domain organization context anchor. A Space represents a stable team, project, working room, or organizational context even when the provider containers attached to that context change.

A Space is product-owned. Matrix rooms, Nextcloud folders, OpenProject projects, CalDAV calendars, MatrixRTC slots/media sessions, decision ledgers, and optional ARC context attach as domain bindings; they do not define the product boundary.

## Canonical objects

- `Space`: stable organization context with a Weave-owned ID, key, name, type, default surface, and archive state.
- `SpaceType`: team, project, community, support room, department, or other governed organization type.
- `SpaceMembership`: provider-neutral relationship between a Weave person and a Space.
- `SpaceRole`: provider-neutral context role such as owner, admin, member, guest, or observer.
- `DomainBinding`: support-safe link from the Space to a canonical domain object.
- `ContextPolicy`: policy flags for member visibility, Weaver access, provider write boundaries, and fail-closed behavior.
- `DefaultSurface`: first member surface for the Space, e.g. chat, boards, files, calendar, or decisions.
- `ContextArchive`: durable archive/deprovisioning state independent of a single provider.

## Binding rules

A DomainBinding references a canonical domain and a support-safe member reference. Admin/operator evidence may retain a redacted provider object reference for readiness, migration, and audit, but normal member payloads must not expose raw provider IDs, endpoints, credentials, or downstream diagnostics.

Each binding records:

- canonical `domain` key;
- provider category or adapter alias for admin/operator compatibility;
- redacted provider object reference;
- member-safe reference;
- readiness state;
- source-of-truth mode;
- lossy notes;
- migration status.

Readiness, source-of-truth, lossy notes, and migration status are required so a provider change can preserve organizational context without claiming lossless migration. The standard is no unaccounted data loss.

## Membership and roles

Space membership is provider-neutral and resolves through Weave people/identity contracts. Provider membership may be synchronized from the Space, imported into it, or shared with explicit precedence, but member-facing roles remain Weave roles. Unknown or conflicting provider roles fail closed until an admin reviews the mapping.

## Fixture

`specs/0005-spaces-anchor/space-anchor-fixture.json` demonstrates one Marketing Space bound to chat, files, boards, and calendar. The boards binding intentionally requires a dry-run because workflow transitions may be lossy during migration.

## Weaver context

RuntimeProfile v2 may reference a Space as desired context only. It does not create a domain tool or authorization grant, and an ARC-bound runtime must not use a Space binding to bypass Weave facades or call raw provider APIs directly.
