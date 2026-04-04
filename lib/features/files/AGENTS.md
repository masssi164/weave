# Files Feature Instructions

`files` owns WebDAV modeling, directory browsing behavior, and the translation from DAV responses into internal file entities and view state. Shared Nextcloud auth, session, and connection lifecycle concerns now live in `lib/integrations/nextcloud/`.

Rules:
- keep WebDAV parsing and DTOs in `data/`
- distinguish files from directories in feature models, not by ad hoc widget logic
- maintain stable path and identifier semantics for navigation, selection, and refresh
- keep expansion, loading, and error state for directory trees out of raw transport objects
- depend on reusable Nextcloud contracts from `lib/integrations/nextcloud/` instead of re-implementing auth/session/orchestration inside `files`
- keep file-specific failure mapping and directory state in `files`, even when the underlying session comes from the shared Nextcloud integration layer

Directory tree behavior:
- recursive tree behavior must be driven by feature state, not by widgets walking raw DAV payloads
- avoid importing another feature to resolve file metadata or storage concerns
- preserve predictable parent/child relationships when refreshing nested folders

Boundary reminders:
- `NextcloudDavClient` is the file-focused protocol client for DAV directory access
- bearer/app-password selection, persisted Nextcloud sessions, app-password revocation, and configured-base-URL matching belong to `integrations/nextcloud`, not `files`
- future Nextcloud-backed features should be able to reuse the same platform layer without importing `features/files/`

Accessibility:
- each file row should be understandable as one unit when appropriate
- screen readers must get clear file or folder naming plus relevant state
- nested navigation must remain traversable without losing context in deep directory trees
