# Nextcloud Integration Instructions

`integrations/nextcloud` owns reusable Nextcloud platform concerns that can be shared by Files today and Calendar or Deck later.

Own here:
- Nextcloud session entities and auth-method semantics
- secure Nextcloud session persistence
- login flow v2, bearer-session resolution, account validation, and app-password revocation
- configured-base-URL normalization, session replacement rules, reconnect rules, and connection lifecycle orchestration
- shared Riverpod providers for the reusable Nextcloud platform stack

Do not own here:
- file-domain entities or WebDAV-to-file mapping
- feature presentation state for files, calendars, or boards
- UI-specific failure wording that only makes sense in one feature when the underlying failure is still shared

Boundary rules:
- consume app-level OIDC state from `features/auth/`, but do not take ownership of app-auth token refresh or persistence
- keep persisted bearer sessions tokenless; only live bearer sessions should carry an in-memory bearer token
- preserve current app-password revocation and stale-session invalidation behavior when refactoring
- shared Nextcloud protocol code should become more reusable here, while DAV parsing that is still file-specific should remain in `features/files/`

Default endpoint expectations:
- the user-facing default files endpoint now derives from `files.<tenant_domain>` to match infrastructure, even though some internal compatibility-safe field names still refer to Nextcloud
- do not assume the browser-facing default host is `nextcloud.<tenant_domain>` when updating setup or endpoint-derivation behavior
