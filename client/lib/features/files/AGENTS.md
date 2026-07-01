# Files Feature Instructions

`files` owns Weave file browsing behavior, upload/download actions, and internal file entities/view state. Normal member file flows go through the Weave backend facade; direct Nextcloud/WebDAV clients are obsolete in release client code.

Rules:
- keep backend facade DTO mapping and file-specific failure mapping in `data/`
- distinguish files from directories in feature models, not by ad hoc widget logic
- maintain stable path and identifier semantics for navigation, selection, and refresh
- keep expansion, loading, and error state for directory trees out of raw transport objects
- do not add direct Nextcloud/WebDAV repository/provider seams for normal member paths

Directory tree behavior:
- recursive tree behavior must be driven by feature state, not by widgets walking raw transport payloads
- avoid importing another feature to resolve file metadata or storage concerns
- preserve predictable parent/child relationships when refreshing nested folders

Boundary reminders:
- backend-owned product facades are the release boundary for Files
- provider-specific diagnostics or migration helpers must live outside normal member presentation/providers and need explicit admin/debug scope
- future provider-backed file features should extend backend/OpenAPI contracts instead of importing provider clients in `features/files/`

Accessibility:
- each file row should be understandable as one unit when appropriate
- screen readers must get clear file or folder naming plus relevant state
- nested navigation must remain traversable without losing context in deep directory trees

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
