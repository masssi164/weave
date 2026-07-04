# Calendar native sync setup

Weave Calendar native integration is additive to the in-app Calendar view. Native OS setup must use Weave-owned setup and sync facades, not raw provider account details.

## Current executable slice

`GET /api/calendar/native-sync-setup` returns authenticated, support-safe setup metadata for native calendar sync:

- iOS boundary: CalDAV configuration profile semantics delivered through a Weave route.
- Android boundary: Account/SyncAdapter plus Calendar Provider / CalendarContract semantics.
- Flutter/native bridge role: setup, status, and revoke only.
- Calendar proof hooks: `GET /api/calendar/scopes`, `GET /api/calendar/events`, `POST /api/calendar/client-setup/credentials`, and the fail-closed Apple profile route.
- Support-safe blocked states for the remaining work: signed profile delivery, Android SyncAdapter wiring, scoped credential secret issuance, and physical-device sync/revoke evidence.

The response deliberately contains only Weave-owned API paths. It must not include provider hostnames, provider account URLs, provider credentials, bearer tokens, or raw provider diagnostics.

## Product boundary

The member app may show native setup status and start or revoke native setup. It must not become a CalDAV/WebDAV client and must not store provider credentials. Calendar rows and sync state belong in the native OS calendar account/provider layer, backed by Weave calendar facade endpoints.

Full native availability remains blocked until physical-device evidence proves profile/account setup, event sync, and revocation behavior.
