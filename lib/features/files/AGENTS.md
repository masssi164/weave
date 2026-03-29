# Files Feature Instructions

`files` owns WebDAV modeling and the translation from DAV responses into internal file entities and view state.

Rules:
- keep WebDAV parsing and DTOs in `data/`
- distinguish files from directories in feature models, not by ad hoc widget logic
- maintain stable path and identifier semantics for navigation, selection, and refresh
- keep expansion, loading, and error state for directory trees out of raw transport objects

Directory tree behavior:
- recursive tree behavior must be driven by feature state, not by widgets walking raw DAV payloads
- avoid importing another feature to resolve file metadata or storage concerns
- preserve predictable parent/child relationships when refreshing nested folders

Accessibility:
- each file row should be understandable as one unit when appropriate
- screen readers must get clear file or folder naming plus relevant state
- nested navigation must remain traversable without losing context in deep directory trees
